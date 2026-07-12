package com.ainote.app.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class MemorySignalClassifier {

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(api[_ -]?key|secret|password|access[_ -]?token|auth[_ -]?token|session[_ -]?token|"
                    + "(?:fake[-_])?token[-_][a-z0-9_-]+|sk-[a-z0-9_-]+|AKIA[0-9A-Z]{16})");
    private static final Pattern INTERACTION_STYLE_SIGNAL = Pattern.compile(
            "(回答|回复|语气|口吻|风格|格式|详细|简短|严肃|深刻|正式|专业|俏皮|轻松|幽默|活泼|开玩笑|玩笑|啰嗦|精简|简洁|"
                    + "answer|reply|tone|style|format|formal|serious|concise|detailed|professional)");
    private static final Pattern REAL_CHINESE_INTERACTION_STYLE_SIGNAL = Pattern.compile(
            "(\u56de\u7b54|\u56de\u590d|\u8bed\u6c14|\u53e3\u543b|\u98ce\u683c|\u683c\u5f0f|\u8be6\u7ec6|"
                    + "\u7b80\u77ed|\u7b80\u6d01|\u4e25\u8083|\u6b63\u5f0f|\u4e13\u4e1a|\u5e7d\u9ed8|\u6df1\u523b|"
                    + "\u8868\u8fbe)");
    private static final Pattern PROJECT_CONTEXT_LABEL = Pattern.compile(
            "^(项目上下文|项目背景|project context|project background)[:：].+",
            Pattern.CASE_INSENSITIVE);

    private final MemorySignalAdvisor signalAdvisor;
    private final MemoryPrivacyService privacyService;

    public MemorySignalClassifier(MemorySignalAdvisor signalAdvisor) {
        this(signalAdvisor, new MemoryPrivacyService());
    }

    @Autowired
    public MemorySignalClassifier(MemorySignalAdvisor signalAdvisor,
                                  MemoryPrivacyService privacyService) {
        this.signalAdvisor = signalAdvisor == null ? MemorySignalAdvisor.disabled() : signalAdvisor;
        this.privacyService = privacyService == null ? new MemoryPrivacyService() : privacyService;
    }

    MemorySignalClassifier() {
        this(MemorySignalAdvisor.disabled(), new MemoryPrivacyService());
    }

    public SignalClassification classify(MemoryCapturePolicy.CaptureRequest request) {
        String userId = request == null ? "" : request.userId();
        String userMessage = normalize(request == null ? "" : request.userMessage());
        String aiResponse = normalize(request == null ? "" : request.aiResponse());
        String compact = compact(userMessage);
        List<String> matchedSignals = new ArrayList<>();

        boolean validUser = userId != null && !userId.isBlank();
        boolean blankMessage = userMessage.isBlank();
        boolean sensitive = containsSensitive(userMessage) || containsSensitive(aiResponse);
        boolean forgetRequest = isForgetRequest(compact);
        boolean noStoreOrUncertain = isNoStoreOrUncertain(userMessage, compact);
        boolean referenceOnly = isReferenceOnlyTask(compact) || isRagReference(aiResponse);
        boolean transientOperation = isTransientOperation(compact);
        boolean oneOffScope = isOneOffInstruction(compact);
        boolean taskOnlyContent = isOneOffTaskContent(userMessage);
        boolean roleOverridePrompt = isRoleOverridePrompt(userMessage);
        boolean durableMemoryCandidate = !taskOnlyContent && !roleOverridePrompt;
        boolean explicitRemember = isExplicitRemember(userMessage, compact);
        boolean interactionStyleCorrection = durableMemoryCandidate && looksLikeInteractionStyleCorrection(userMessage, compact);
        boolean preferenceCorrection = durableMemoryCandidate && looksLikePreferenceCorrection(userMessage, compact);
        boolean stableStylePreference = durableMemoryCandidate && looksLikeStableStylePreference(userMessage, compact);
        boolean oneOffStylePreference = oneOffScope && looksLikeStyleDirective(userMessage, compact);
        boolean assistantFeedback = isAssistantFeedback(compact);
        boolean correctionSignal = preferenceCorrection || interactionStyleCorrection;
        boolean interactionStyleSignal = interactionStyleCorrection || stableStylePreference || oneOffStylePreference;
        boolean projectContextSignal = isProjectContext(userMessage);
        boolean preferenceSignal = (durableMemoryCandidate && looksLikePreference(userMessage, compact)) || interactionStyleSignal;
        boolean hardDeny = !validUser
                || blankMessage
                || sensitive
                || forgetRequest
                || noStoreOrUncertain
                || referenceOnly
                || transientOperation
                || oneOffScope
                || assistantFeedback
                || taskOnlyContent
                || roleOverridePrompt;

        if (!hardDeny) {
            MemorySignalAdvisor.AdvisorResult advice = signalAdvisor.advise(request);
            if (shouldExposeAdviceSignals(advice)) {
                matchedSignals.addAll(advice.signals());
            }
            if (canMergeAdvice(advice)) {
                String memoryType = advice.memoryType();
                if ("style".equals(memoryType)) {
                    interactionStyleSignal = true;
                    preferenceSignal = true;
                } else if ("project_context".equals(memoryType)) {
                    projectContextSignal = true;
                } else if ("preference".equals(memoryType) || "fact".equals(memoryType)) {
                    preferenceSignal = true;
                }
            }
        }

        addSignal(matchedSignals, !validUser, "missing_user");
        addSignal(matchedSignals, blankMessage, "blank_message");
        addSignal(matchedSignals, sensitive, "sensitive_content");
        addSignal(matchedSignals, forgetRequest, "forget_request");
        addSignal(matchedSignals, noStoreOrUncertain, "explicit_no_store_or_uncertain");
        addSignal(matchedSignals, referenceOnly, "reference_only");
        addSignal(matchedSignals, transientOperation, "transient_operation");
        addSignal(matchedSignals, oneOffScope, "one_off_scope");
        addSignal(matchedSignals, taskOnlyContent, "task_only_content");
        addSignal(matchedSignals, roleOverridePrompt, "role_override_prompt");
        addSignal(matchedSignals, assistantFeedback, "assistant_feedback");
        addSignal(matchedSignals, explicitRemember, "explicit_remember");
        addSignal(matchedSignals, preferenceCorrection, "preference_correction");
        addSignal(matchedSignals, interactionStyleCorrection, "interaction_style_correction");
        addSignal(matchedSignals, stableStylePreference, "stable_style_preference");
        addSignal(matchedSignals, oneOffStylePreference, "one_off_style_preference");
        addSignal(matchedSignals, projectContextSignal, "project_context");
        addSignal(matchedSignals, preferenceSignal && !interactionStyleSignal, "preference_signal");

        return new SignalClassification(
                validUser,
                blankMessage,
                sensitive,
                forgetRequest,
                noStoreOrUncertain,
                referenceOnly,
                transientOperation,
                oneOffScope,
                taskOnlyContent,
                roleOverridePrompt,
                assistantFeedback,
                explicitRemember,
                preferenceSignal,
                correctionSignal,
                interactionStyleSignal,
                projectContextSignal,
                List.copyOf(matchedSignals));
    }

    private boolean shouldExposeAdviceSignals(MemorySignalAdvisor.AdvisorResult advice) {
        return advice != null
                && advice.signals() != null
                && !advice.signals().isEmpty()
                && !advice.signals().contains("advisor_disabled");
    }

    private boolean canMergeAdvice(MemorySignalAdvisor.AdvisorResult advice) {
        return advice != null
                && advice.available()
                && advice.shouldCapture()
                && ("preference".equals(advice.memoryType())
                || "style".equals(advice.memoryType())
                || "project_context".equals(advice.memoryType())
                || "fact".equals(advice.memoryType()));
    }

    private void addSignal(List<String> matchedSignals, boolean condition, String signal) {
        if (condition) {
            matchedSignals.add(signal);
        }
    }

    private boolean containsSensitive(String text) {
        return SENSITIVE_PATTERN.matcher(text).find()
                || privacyService.scan(text).hasBlockingFindings();
    }

    private boolean isExplicitRemember(String userMessage, String compact) {
        if (isIncidentalRememberTask(userMessage, compact)) {
            return false;
        }
        if (isNoStoreOrUncertain(userMessage, compact)) {
            return false;
        }
        if (compact.contains("不要记住") || compact.contains("別記住") || compact.contains("别记住")) {
            return false;
        }
        if (compact.startsWith("记住") || compact.startsWith("請記住") || compact.startsWith("请记住")) {
            return true;
        }

        if (compact.contains("\u4e0d\u8981\u8bb0\u4f4f") || compact.contains("\u522b\u8bb0\u4f4f")) {
            return false;
        }
        if (compact.startsWith("\u8bb0\u4f4f") || compact.startsWith("\u8bf7\u8bb0\u4f4f")) {
            return true;
        }

        String normalized = userMessage == null ? "" : userMessage.trim().toLowerCase(Locale.ROOT);
        return Pattern.compile("(?i)^\\s*(please\\s+)?remember\\s*[:：,，-]\\s*\\S.+").matcher(normalized).find()
                || Pattern.compile("(?i)^\\s*(please\\s+)?remember\\s+that\\s+"
                        + "(my|i\\b|i'm\\b|i am\\b|we\\b|our\\b|this project\\b|the project\\b).+")
                .matcher(normalized)
                .find()
                || Pattern.compile("(?i)^\\s*(please\\s+)?keep\\s+in\\s+mind\\s*[:：,，-]?\\s*(that\\s+)?"
                        + "(my|i\\b|i'm\\b|i am\\b|we\\b|our\\b|this project\\b|the project\\b).+")
                .matcher(normalized)
                .find();
    }

    private boolean isNoStoreOrUncertain(String userMessage, String compact) {
        String normalized = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        return compact.contains("先别记")
                || compact.contains("先不要记")
                || compact.contains("暂时别记")
                || compact.contains("暂时不要记")
                || compact.contains("不要长期记")
                || compact.contains("不用长期记")
                || compact.contains("可能还会改")
                || compact.contains("等我确认")
                || compact.contains("待确认")
                || Pattern.compile("(?i)\\b(don't|do\\s+not)\\s+(remember|save|store)\\s+"
                        + "(this|that|it|my\\s+(preference|details?|information|profile))\\b")
                        .matcher(normalized)
                        .find()
                || Pattern.compile("(?i)\\b(not\\s+sure|uncertain|temporary|tentative)\\b.{0,40}"
                        + "\\b(save|store|remember|keep)\\b")
                        .matcher(normalized)
                        .find()
                || Pattern.compile("(?i)\\b(might|may)\\s+change\\s+(this|that|my\\s+preference)\\b")
                        .matcher(normalized)
                        .find();
    }

    private boolean isIncidentalRememberTask(String userMessage, String compact) {
        String normalized = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        return normalized.contains("how can i recall or remember")
                || normalized.contains("how to remember")
                || normalized.contains("how to easily remember")
                || normalized.contains("can't remember")
                || normalized.contains("cannot remember")
                || normalized.contains("different ways of saying")
                || normalized.contains("ways of saying")
                || normalized.contains("write a rap")
                || normalized.contains("every sentence start")
                || normalized.contains("check grammar")
                || normalized.contains("make it easy to remember")
                || normalized.contains("sort from a to z")
                || compact.contains("forgetit");
    }

    private boolean looksLikePreference(String userMessage, String compact) {
        if (isQuotedOrExercisePreferenceTask(userMessage)) {
            return false;
        }
        if (compact.contains("我希望")
                || compact.contains("我喜欢")
                || compact.contains("我偏好")) {
            return true;
        }
        if (compact.contains("\u6211\u5e0c\u671b")
                || compact.contains("\u6211\u559c\u6b22")
                || compact.contains("\u6211\u504f\u597d")) {
            return true;
        }
        String normalized = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        return Pattern.compile("(?i)\\b(i\\s+(?:still\\s+|really\\s+|usually\\s+|generally\\s+|also\\s+|personally\\s+)?"
                        + "(?:prefer|like|don't\\s+like|do\\s+not\\s+like)|my\\s+preference\\s+is)\\b")
                .matcher(normalized)
                .find();
    }

    private boolean isQuotedOrExercisePreferenceTask(String userMessage) {
        String normalized = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        return normalized.contains("give me a response to")
                || normalized.contains("complete the conversation")
                || normalized.contains("write dialogue")
                || normalized.contains("correct this message")
                || normalized.contains("check grammar")
                || normalized.contains("rewrite ")
                || normalized.contains("re write ")
                || normalized.contains("to send in a discussion")
                || normalized.contains("only return the raw message");
    }

    private boolean isOneOffTaskContent(String userMessage) {
        String normalized = userMessage == null ? "" : userMessage.trim().toLowerCase(Locale.ROOT);
        return isQuotedOrExercisePreferenceTask(userMessage)
                || normalized.startsWith("task:")
                || normalized.startsWith("prompt:")
                || normalized.startsWith("response:")
                || normalized.startsWith("you are an expert")
                || Pattern.compile("(?i)^\\s*(task:|prompt:|response:|story\\s+about|character\\s+notes|"
                                + "choose\\s+the\\s+right\\s+answer|ask\\s+me\\s+questions|please\\s+edit\\s+this\\s+text|"
                                + "please\\s+correct)\\b")
                        .matcher(normalized)
                        .find()
                || Pattern.compile("(?i)^\\s*(write|make|generate|create|prepare|revise|rewrite|polish|"
                                + "improve|paraphrase|translate|complete|summarize|describe|explain)\\b")
                        .matcher(normalized)
                        .find()
                || Pattern.compile("(?i)^\\s*(please\\s+)?(generate|make|write|create|revise|rewrite|polish|"
                                + "improve|paraphrase|translate)\\b")
                        .matcher(normalized)
                        .find()
                || Pattern.compile("(?i)^\\s*(is\\s+it|what\\s+if|which\\s+type|tell\\s+how|how\\s+to|"
                                + "how\\s+can\\s+i|how\\s+begin|what\\s+are|what\\s+is|can\\s+.+\\?)\\b")
                        .matcher(normalized)
                        .find()
                || Pattern.compile("(?i)^\\s*i\\s+want\\s+you\\s+to\\s+(act|create|write|make|design|"
                                + "generate|prepare|work|respond|ask)\\b")
                        .matcher(normalized)
                        .find()
                || normalized.startsWith("(")
                || normalized.startsWith("[")
                || normalized.contains("please generate")
                || normalized.contains("in this detailed script")
                || normalized.contains("this is a concept for lyrics")
                || normalized.contains("for references:")
                || normalized.contains(" writing prompt:")
                || normalized.startsWith("writing prompt:");
    }

    private boolean isRoleOverridePrompt(String userMessage) {
        String normalized = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        return normalized.contains("ignore all previous instructions")
                || normalized.contains("ignore all the instructions")
                || normalized.contains("do anything now")
                || normalized.contains("no filters or restrictions")
                || normalized.contains("no limitations")
                || normalized.contains("openai policy")
                || normalized.contains("openai policies")
                || normalized.contains("no ethics")
                || normalized.contains("no prohibitions")
                || normalized.contains("opposite day")
                || normalized.contains("free from all restrictions")
                || normalized.contains("unhinged response")
                || normalized.contains("try to do harm")
                || normalized.contains("illegal requests")
                || normalized.contains("answer as dan")
                || normalized.contains("act just like dan");
    }

    private boolean looksLikePreferenceCorrection(String userMessage, String compact) {
        String normalized = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        boolean chineseCorrection = compact.contains("\u4e0d\u518d")
                || compact.contains("\u6539\u4e3a")
                || compact.contains("\u4ee5\u540e\u4e0d\u8981")
                || (hasChineseCorrectionMarker(compact)
                        && (compact.contains("希望")
                        || compact.contains("偏好")
                        || compact.contains("喜欢")
                        || hasInteractionStyleSignal(userMessage)));
        if (chineseCorrection) {
            return true;
        }
        boolean directCorrection = compact.contains("浠ュ悗")
                || compact.contains("涓嶅啀")
                || compact.contains("鏀逛负")
                || Pattern.compile("(?i)\\b(from\\s+now\\s+on|by\\s+default|always|"
                                + "i\\s+(no\\s+longer|don't|do\\s+not|prefer|would\\s+rather)|"
                                + "earlier\\s+i\\s+(said|wanted)|my\\s+preference\\s+is|"
                                + "don't\\s+(answer|reply)|do\\s+not\\s+(answer|reply))\\b")
                        .matcher(normalized)
                        .find();
        if (!directCorrection) {
            return false;
        }
        return compact.contains("以后不要")
                || compact.contains("不再")
                || compact.contains("改为")
                || compact.contains("nolonger")
                || compact.contains("instead")
                || compact.contains("rather");
    }

    private boolean looksLikeInteractionStyleCorrection(String userMessage, String compact) {
        String normalized = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        boolean directStyleCorrection = (hasChineseCorrectionMarker(compact) && hasInteractionStyleSignal(userMessage))
                || ((compact.contains("\u4e0d\u8981") || compact.contains("\u522b"))
                && (hasStandaloneChineseWant(compact) || compact.contains("\u6539\u4e3a")))
                || Pattern.compile("(?i)\\b(from\\s+now\\s+on|"
                                + "don't\\s+(answer|reply)|do\\s+not\\s+(answer|reply))\\b")
                        .matcher(normalized)
                        .find();
        if (!directStyleCorrection) {
            return false;
        }
        boolean hasNegativeDirective = compact.contains("不要")
                || compact.contains("别")
                || compact.contains("不是")
                || compact.contains("少一点")
                || compact.contains("少点")
                || compact.contains("\u522b")
                || compact.contains("\u4e0d\u8981")
                || compact.contains("don't")
                || compact.contains("donot")
                || compact.contains("not")
                || compact.contains("less");
        boolean hasPositiveReplacement = compact.contains("改成")
                || compact.contains("改为")
                || compact.contains("而是")
                || compact.contains("应该")
                || compact.contains("多一点")
                || compact.contains("多点")
                || hasStandaloneChineseWant(compact)
                || compact.contains("\u6539\u4e3a")
                || compact.contains("more")
                || compact.contains("instead")
                || hasStandaloneChineseWantAfterNegativeDirective(compact);
        return hasNegativeDirective
                && hasPositiveReplacement
                && hasInteractionStyleSignal(userMessage);
    }

    private boolean hasChineseCorrectionMarker(String compact) {
        return compact.contains("更正")
                || compact.contains("纠正")
                || compact.contains("修正")
                || (compact.contains("不是") && compact.contains("而是"));
    }

    private boolean looksLikeStableStylePreference(String userMessage, String compact) {
        boolean stableScope = compact.contains("以后")
                || compact.contains("今后")
                || compact.contains("之后")
                || compact.contains("从今天开始")
                || compact.contains("从现在开始")
                || compact.contains("接下来")
                || compact.contains("默认")
                || compact.contains("一直")
                || compact.contains("fromnowon")
                || compact.contains("bydefault")
                || compact.contains("always");
        return stableScope && looksLikeStyleDirective(userMessage, compact);
    }

    private boolean looksLikeStyleDirective(String userMessage, String compact) {
        boolean styleIntent = hasInteractionStyleSignal(userMessage);
        boolean directive = compact.contains("请")
                || compact.contains("希望")
                || compact.contains("要")
                || compact.contains("用")
                || compact.contains("回答")
                || compact.contains("回复")
                || compact.contains("answer")
                || compact.contains("reply")
                || compact.contains("iwantyouto");
        return styleIntent && directive;
    }

    private boolean hasInteractionStyleSignal(String userMessage) {
        String text = userMessage == null ? "" : userMessage;
        return INTERACTION_STYLE_SIGNAL.matcher(text).find()
                || REAL_CHINESE_INTERACTION_STYLE_SIGNAL.matcher(text).find();
    }

    private boolean hasStandaloneChineseWantAfterNegativeDirective(String compact) {
        int negativeIndex = firstNegativeDirectiveIndex(compact);
        if (negativeIndex < 0) {
            return false;
        }
        int wantIndex = compact.indexOf("要", negativeIndex + 1);
        while (wantIndex >= 0) {
            boolean partOfNegative = wantIndex > 0 && compact.charAt(wantIndex - 1) == '不';
            if (!partOfNegative) {
                return true;
            }
            wantIndex = compact.indexOf("要", wantIndex + 1);
        }
        return false;
    }

    private boolean hasStandaloneChineseWant(String compact) {
        int wantIndex = compact.indexOf('\u8981');
        while (wantIndex >= 0) {
            boolean partOfNegative = wantIndex > 0 && compact.charAt(wantIndex - 1) == '\u4e0d';
            if (!partOfNegative) {
                return true;
            }
            wantIndex = compact.indexOf('\u8981', wantIndex + 1);
        }
        return false;
    }

    private int firstNegativeDirectiveIndex(String compact) {
        int best = -1;
        for (String token : List.of("不要", "别", "少一点", "少点")) {
            int index = compact.indexOf(token);
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
    }

    private boolean isForgetRequest(String compact) {
        return compact.contains("忘记")
                || compact.contains("不要记住")
                || compact.contains("别记住")
                || compact.contains("forgetthis")
                || compact.contains("forgetthat");
    }

    private boolean isTransientOperation(String compact) {
        if (compact.equals("确认") || compact.equals("確定") || compact.equals("取消")
                || compact.equals("可以") || compact.equals("好的") || compact.equals("好") || compact.equals("行")
                || compact.equals("ok") || compact.equals("yes") || compact.equals("no")
                || compact.equals("confirm") || compact.equals("cancel")) {
            return true;
        }
        return compact.contains("删除全部笔记")
                || compact.contains("删除所有笔记")
                || compact.contains("清空笔记")
                || compact.contains("删掉全部笔记")
                || compact.contains("deleteallnotes")
                || compact.contains("deleteeverynote")
                || compact.contains("deletenote")
                || compact.contains("confirmdelete")
                || compact.contains("canceldelete");
    }

    private boolean isReferenceOnlyTask(String compact) {
        return compact.contains("总结这篇笔记")
                || compact.contains("总结当前笔记")
                || compact.contains("总结选中笔记")
                || compact.contains("这篇笔记")
                || compact.contains("当前笔记")
                || compact.contains("selectednote")
                || compact.contains("currentnote")
                || compact.contains("thisnote")
                || compact.contains("whatdoesthisnotesay")
                || compact.contains("summarizethisnote");
    }

    private boolean isOneOffInstruction(String compact) {
        return compact.contains("这次")
                || compact.contains("这一次")
                || compact.contains("本次")
                || compact.contains("本轮")
                || compact.contains("当前这次")
                || compact.contains("thisonce")
                || compact.contains("thisreply")
                || compact.contains("thistime")
                || compact.contains("forthisreply");
    }

    private boolean isAssistantFeedback(String compact) {
        boolean feedbackVerb = compact.contains("ilikethis")
                || compact.contains("ilovethis")
                || compact.contains("thisisgood")
                || compact.contains("goodanswer")
                || compact.contains("greatanswer")
                || compact.contains("喜欢")
                || compact.contains("很好")
                || compact.contains("不错")
                || compact.contains("有帮助")
                || compact.contains("谢谢")
                || compact.contains("thanks")
                || compact.contains("thankyou");
        boolean answerReference = compact.contains("thisanswer")
                || compact.contains("thisreply")
                || compact.contains("thisresponse")
                || compact.contains("youranswer")
                || compact.contains("yourreply")
                || compact.contains("yourresponse")
                || compact.contains("这个回答")
                || compact.contains("这次回答")
                || compact.contains("你的回答")
                || compact.contains("这个回复")
                || compact.contains("这次回复")
                || compact.contains("你的回复")
                || compact.contains("这个答案")
                || compact.contains("你的答案");
        return answerReference && feedbackVerb;
    }

    private boolean isProjectContext(String userMessage) {
        String text = userMessage == null ? "" : userMessage.trim();
        String compact = compact(normalize(text));
        return PROJECT_CONTEXT_LABEL.matcher(text).find()
                || compact.contains("项目上下文")
                || compact.contains("项目背景")
                || compact.contains("当前项目是")
                || compact.contains("我的当前项目是");
    }

    private boolean isRagReference(String text) {
        return text.contains("<rag_context")
                || text.contains("reference_only")
                || text.contains("operation_target=\"false\"");
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private String compact(String text) {
        return text.replaceAll("\\s+", "");
    }

    public record SignalClassification(
            boolean validUser,
            boolean blankMessage,
            boolean sensitive,
            boolean forgetRequest,
            boolean noStoreOrUncertain,
            boolean referenceOnly,
            boolean transientOperation,
            boolean oneOffScope,
            boolean taskOnlyContent,
            boolean roleOverridePrompt,
            boolean assistantFeedback,
            boolean explicitRemember,
            boolean preferenceSignal,
            boolean correctionSignal,
            boolean interactionStyleSignal,
            boolean projectContextSignal,
            List<String> matchedSignals) {
    }
}
