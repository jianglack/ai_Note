package com.ainote.app.service;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class MemoryCapturePolicy {

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(api[_ -]?key|secret|password|token|sk-[a-z0-9_-]+|AKIA[0-9A-Z]{16})");

    public CaptureDecision evaluate(CaptureRequest request) {
        String userMessage = normalize(request.userMessage());
        String aiResponse = normalize(request.aiResponse());
        String compact = compact(userMessage);

        if (request.userId() == null || request.userId().isBlank() || userMessage.isBlank()) {
            return CaptureDecision.deny(DecisionType.DENY_TRANSIENT, "blank_or_missing_user");
        }
        if (containsSensitive(userMessage) || containsSensitive(aiResponse)) {
            return CaptureDecision.deny(DecisionType.DENY_SENSITIVE, "sensitive_content");
        }
        if (isForgetRequest(compact)) {
            return CaptureDecision.deny(DecisionType.FORGET_REQUEST, "forget_request");
        }
        if (isReferenceOnlyTask(compact) || isRagReference(aiResponse)) {
            return CaptureDecision.deny(DecisionType.DENY_TOOL_RESULT, "reference_context_not_profile");
        }
        if (isTransientOperation(compact)) {
            return CaptureDecision.deny(DecisionType.DENY_TRANSIENT, "operation_or_confirmation");
        }
        if (isExplicitRemember(compact)) {
            return CaptureDecision.allow(DecisionType.ALLOW_EXPLICIT, "explicit_memory", 0.95);
        }
        if (looksLikePreference(compact)) {
            return CaptureDecision.allow(DecisionType.ALLOW_IMPLICIT_LOW_CONFIDENCE, "implicit_preference", 0.65);
        }
        return CaptureDecision.deny(DecisionType.DENY_TRANSIENT, "no_stable_user_memory_signal");
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

    public enum DecisionType {
        ALLOW_EXPLICIT,
        ALLOW_IMPLICIT_LOW_CONFIDENCE,
        DENY_TRANSIENT,
        DENY_SENSITIVE,
        DENY_TOOL_RESULT,
        FORGET_REQUEST
    }

    public record CaptureRequest(String userId, String userMessage, String aiResponse) {
    }

    public record CaptureDecision(DecisionType type,
                                  boolean allowed,
                                  String reason,
                                  double baseConfidence) {
        static CaptureDecision allow(DecisionType type, String reason, double baseConfidence) {
            return new CaptureDecision(type, true, reason, baseConfidence);
        }

        static CaptureDecision deny(DecisionType type, String reason) {
            return new CaptureDecision(type, false, reason, 0.0);
        }
    }
}
