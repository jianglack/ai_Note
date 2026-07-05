package com.ainote.app.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class MemorySignalClassifier {

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(api[_ -]?key|secret|password|token|sk-[a-z0-9_-]+|AKIA[0-9A-Z]{16})");
    private static final Pattern INTERACTION_STYLE_SIGNAL = Pattern.compile(
            "(回答|回复|语气|口吻|风格|格式|详细|简短|严肃|深刻|正式|专业|俏皮|轻松|幽默|活泼|开玩笑|玩笑|啰嗦|精简|简洁|"
                    + "answer|reply|tone|style|format|formal|serious|concise|detailed|professional)");
    private static final Pattern PROJECT_CONTEXT_LABEL = Pattern.compile(
            "^(项目上下文|项目背景|project context|project background)[:：].+",
            Pattern.CASE_INSENSITIVE);

    private final MemorySignalAdvisor signalAdvisor;

    public MemorySignalClassifier(MemorySignalAdvisor signalAdvisor) {
        this.signalAdvisor = signalAdvisor == null ? MemorySignalAdvisor.disabled() : signalAdvisor;
    }

    MemorySignalClassifier() {
        this(MemorySignalAdvisor.disabled());
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
        boolean referenceOnly = isReferenceOnlyTask(compact) || isRagReference(aiResponse);
        boolean transientOperation = isTransientOperation(compact);
        boolean oneOffScope = isOneOffInstruction(compact);
        boolean explicitRemember = isExplicitRemember(compact);
        boolean interactionStyleCorrection = looksLikeInteractionStyleCorrection(userMessage, compact);
        boolean preferenceCorrection = looksLikePreferenceCorrection(compact);
        boolean stableStylePreference = looksLikeStableStylePreference(userMessage, compact);
        boolean oneOffStylePreference = oneOffScope && looksLikeStyleDirective(userMessage, compact);
        boolean correctionSignal = preferenceCorrection || interactionStyleCorrection;
        boolean interactionStyleSignal = interactionStyleCorrection || stableStylePreference || oneOffStylePreference;
        boolean projectContextSignal = isProjectContext(userMessage);
        boolean preferenceSignal = looksLikePreference(compact) || interactionStyleSignal;
        boolean hardDeny = !validUser
                || blankMessage
                || sensitive
                || forgetRequest
                || referenceOnly
                || transientOperation
                || oneOffScope;

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
                } else if ("preference".equals(memoryType)) {
                    preferenceSignal = true;
                }
            }
        }

        addSignal(matchedSignals, !validUser, "missing_user");
        addSignal(matchedSignals, blankMessage, "blank_message");
        addSignal(matchedSignals, sensitive, "sensitive_content");
        addSignal(matchedSignals, forgetRequest, "forget_request");
        addSignal(matchedSignals, referenceOnly, "reference_only");
        addSignal(matchedSignals, transientOperation, "transient_operation");
        addSignal(matchedSignals, oneOffScope, "one_off_scope");
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
                referenceOnly,
                transientOperation,
                oneOffScope,
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
                || "project_context".equals(advice.memoryType()));
    }

    private void addSignal(List<String> matchedSignals, boolean condition, String signal) {
        if (condition) {
            matchedSignals.add(signal);
        }
    }

    private boolean containsSensitive(String text) {
        return SENSITIVE_PATTERN.matcher(text).find();
    }

    private boolean isExplicitRemember(String compact) {
        return compact.contains("记住")
                || compact.contains("請記住")
                || compact.contains("remember")
                || compact.contains("keepinmind");
    }

    private boolean looksLikePreference(String compact) {
        return compact.contains("我希望")
                || compact.contains("我喜欢")
                || compact.contains("我偏好")
                || compact.contains("iprefer")
                || compact.contains("ilike")
                || compact.contains("prefer")
                || compact.contains("like")
                || compact.contains("iwantyouto");
    }

    private boolean looksLikePreferenceCorrection(String compact) {
        return compact.contains("以后不要")
                || compact.contains("不再")
                || compact.contains("改为")
                || compact.contains("nolonger")
                || compact.contains("instead")
                || compact.contains("rather");
    }

    private boolean looksLikeInteractionStyleCorrection(String userMessage, String compact) {
        boolean hasNegativeDirective = compact.contains("不要")
                || compact.contains("别")
                || compact.contains("少一点")
                || compact.contains("少点")
                || compact.contains("not")
                || compact.contains("less");
        boolean hasPositiveReplacement = compact.contains("改成")
                || compact.contains("改为")
                || compact.contains("多一点")
                || compact.contains("多点")
                || compact.contains("more")
                || compact.contains("instead")
                || hasStandaloneChineseWantAfterNegativeDirective(compact);
        return hasNegativeDirective
                && hasPositiveReplacement
                && INTERACTION_STYLE_SIGNAL.matcher(userMessage).find();
    }

    private boolean looksLikeStableStylePreference(String userMessage, String compact) {
        boolean stableScope = compact.contains("以后")
                || compact.contains("今后")
                || compact.contains("之后")
                || compact.contains("接下来")
                || compact.contains("默认")
                || compact.contains("一直")
                || compact.contains("fromnowon")
                || compact.contains("bydefault")
                || compact.contains("always");
        return stableScope && looksLikeStyleDirective(userMessage, compact);
    }

    private boolean looksLikeStyleDirective(String userMessage, String compact) {
        boolean styleIntent = INTERACTION_STYLE_SIGNAL.matcher(userMessage).find();
        boolean directive = compact.contains("请")
                || compact.contains("希望")
                || compact.contains("要")
                || compact.contains("用")
                || compact.contains("回答")
                || compact.contains("回复")
                || compact.contains("answer")
                || compact.contains("reply");
        return styleIntent && directive;
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

    private boolean isProjectContext(String userMessage) {
        return PROJECT_CONTEXT_LABEL.matcher(userMessage).find();
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
            boolean referenceOnly,
            boolean transientOperation,
            boolean oneOffScope,
            boolean explicitRemember,
            boolean preferenceSignal,
            boolean correctionSignal,
            boolean interactionStyleSignal,
            boolean projectContextSignal,
            List<String> matchedSignals) {
    }
}
