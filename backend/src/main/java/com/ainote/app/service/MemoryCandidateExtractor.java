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
        String category = correction ? "preference" : inferCategory(content);
        double confidence = Math.max(decision.baseConfidence(), correction ? 0.9 : 0.0);
        return List.of(new MemoryCandidate(
                category,
                category,
                content,
                confidence,
                "user",
                userMessage,
                correction
        ));
    }

    private String normalizeContent(String userMessage) {
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
                                  boolean correction) {
    }
}
