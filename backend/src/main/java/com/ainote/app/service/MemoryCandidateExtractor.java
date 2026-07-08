package com.ainote.app.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MemoryCandidateExtractor {

    private static final Pattern CHINESE_CONTRASTIVE_STYLE = Pattern.compile(
            "^(?:现在|以后|接下来|之后)?(?:请)?(?:不要|别|少一点|少点)(?<avoid>[^，,。；;]+)[，,。；;]?(?:要|改成|改为|多一点|多点)(?<prefer>[^，,。；;]+)$");
    private static final Pattern CHINESE_STABLE_STYLE = Pattern.compile(
            "^(?:以后|今后|之后|接下来)(?:请)?(?:默认)?(?:回答|回复)?(?:请)?(?<style>.+)$");
    private static final Pattern PROJECT_CONTEXT_LABEL = Pattern.compile(
            "^(?:项目上下文|项目背景|project context|project background)[:：]\\s*(?<content>.+)$",
            Pattern.CASE_INSENSITIVE);

    public List<MemoryCandidate> extract(MemoryCapturePolicy.CaptureRequest request,
                                         MemoryCapturePolicy.CaptureDecision decision) {
        if (request == null || decision == null || !decision.allowed()) {
            return List.of();
        }

        String userMessage = request.userMessage() == null ? "" : request.userMessage().trim();
        String content = normalizeContent(userMessage);
        if (content.isBlank()) {
            return List.of();
        }

        boolean correction = isCorrection(userMessage);
        String memoryType = inferMemoryType(userMessage, content);
        if ("implicit_interaction_style".equals(decision.reason())) {
            memoryType = "style";
        }
        if (correction && "fact".equals(memoryType)) {
            memoryType = "preference";
        }
        String category = "style".equals(memoryType) ? "preference" : memoryType;
        double confidence = Math.max(decision.baseConfidence(), correction ? 0.9 : 0.0);
        String scope = "project_context".equals(memoryType) ? "project" : "user";
        return List.of(new MemoryCandidate(
                memoryType,
                category,
                content,
                confidence,
                scope,
                userMessage,
                correction,
                decision.matchedSignals(),
                decision.type().name(),
                decision.reason()
        ));
    }

    private String normalizeContent(String userMessage) {
        String projectContext = normalizeProjectContext(userMessage);
        if (!projectContext.isBlank()) {
            return projectContext;
        }

        String stableStyle = normalizeStableStyle(userMessage);
        if (!stableStyle.isBlank()) {
            return stableStyle;
        }

        String contrastiveStyle = normalizeContrastiveStyle(userMessage);
        if (!contrastiveStyle.isBlank()) {
            return contrastiveStyle;
        }

        String content = userMessage
                .replaceFirst("(?i)^\\s*(remember|keep in mind)[:,，]?\\s*", "")
                .replaceFirst("^\\s*(请)?记住[:,，]?\\s*", "")
                .replaceFirst("^\\s*我希望你以后", "希望你以后")
                .replaceFirst("^\\s*我希望", "希望")
                .trim();
        if (content.length() > 240) {
            content = content.substring(0, 240).trim();
        }
        return content;
    }

    private String normalizeProjectContext(String userMessage) {
        Matcher matcher = PROJECT_CONTEXT_LABEL.matcher(userMessage == null ? "" : userMessage.trim());
        if (!matcher.find()) {
            return "";
        }
        return matcher.group("content").trim();
    }

    private String normalizeStableStyle(String userMessage) {
        String normalized = userMessage == null ? "" : userMessage.trim();
        String compact = normalized.replaceAll("\\s+", "");
        if (compact.contains("以后不要") || compact.contains("不再") || compact.contains("改为")) {
            return "";
        }
        Matcher matcher = CHINESE_STABLE_STYLE.matcher(normalized);
        if (!matcher.find()) {
            return "";
        }
        String style = cleanupStyleFragment(matcher.group("style"))
                .replaceFirst("^请", "")
                .replaceFirst("^用", "")
                .trim();
        if (style.isBlank()) {
            return "";
        }
        return "希望交互风格" + stripTrailingSentencePunctuation(style);
    }

    private String normalizeContrastiveStyle(String userMessage) {
        String normalized = userMessage == null ? "" : userMessage.trim();
        Matcher matcher = CHINESE_CONTRASTIVE_STYLE.matcher(normalized);
        if (!matcher.find()) {
            return "";
        }

        String avoid = cleanupStyleFragment(matcher.group("avoid"));
        String prefer = cleanupStyleFragment(matcher.group("prefer"));
        if (avoid.isBlank() || prefer.isBlank()) {
            return "";
        }
        return "希望交互风格" + prefer + "，避免" + avoid;
    }

    private String cleanupStyleFragment(String value) {
        return value == null ? "" : value
                .replaceFirst("^这么", "")
                .replaceFirst("^太", "")
                .trim();
    }

    private String stripTrailingSentencePunctuation(String value) {
        return value == null ? "" : value.replaceFirst("[。.!！]+$", "").trim();
    }

    private String inferMemoryType(String userMessage, String content) {
        if (isProjectContext(userMessage)) {
            return "project_context";
        }
        if (isStyleMemory(userMessage, content)) {
            return "style";
        }
        return inferCategory(content);
    }

    private boolean isProjectContext(String userMessage) {
        return PROJECT_CONTEXT_LABEL.matcher(userMessage == null ? "" : userMessage.trim()).find();
    }

    private boolean isStyleMemory(String userMessage, String content) {
        String normalized = (content == null ? "" : content) + " " + (userMessage == null ? "" : userMessage);
        return normalized.contains("交互风格")
                || normalized.contains("语气")
                || normalized.contains("口吻")
                || normalized.contains("严肃")
                || normalized.contains("专业")
                || normalized.contains("俏皮")
                || normalized.contains("开玩笑");
    }

    private String inferCategory(String content) {
        String normalized = content.toLowerCase(Locale.ROOT);
        if (normalized.contains("回复")
                || normalized.contains("回答")
                || normalized.contains("语气")
                || normalized.contains("格式")
                || normalized.contains("reply")
                || normalized.contains("answer")
                || normalized.contains("format")) {
            return "preference";
        }
        if (normalized.contains("喜欢")
                || normalized.contains("希望")
                || normalized.contains("prefer")
                || normalized.contains("like")) {
            return "preference";
        }
        return "fact";
    }

    private boolean isCorrection(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return normalized.contains("不再")
                || normalized.contains("改为")
                || normalized.contains("以后不要")
                || isContrastiveCorrection(text)
                || normalized.contains("nolonger")
                || normalized.contains("instead")
                || normalized.contains("rather");
    }

    private boolean isContrastiveCorrection(String text) {
        return CHINESE_CONTRASTIVE_STYLE.matcher(text == null ? "" : text.trim()).find();
    }

    public record MemoryCandidate(String memoryType,
                                  String category,
                                  String content,
                                  double confidence,
                                  String scope,
                                  String evidenceExcerpt,
                                  boolean correction,
                                  List<String> policySignals,
                                  String decisionType,
                                  String policyReason) {
        public MemoryCandidate {
            policySignals = policySignals == null ? List.of() : List.copyOf(policySignals);
            decisionType = decisionType == null ? "" : decisionType;
            policyReason = policyReason == null ? "" : policyReason;
        }

        public MemoryCandidate(String memoryType,
                               String category,
                               String content,
                               double confidence,
                               String scope,
                               String evidenceExcerpt,
                               boolean correction) {
            this(memoryType, category, content, confidence, scope, evidenceExcerpt, correction, List.of(), "", "");
        }
    }
}
