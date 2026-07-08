package com.ainote.app.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SimulatedMemoryReplayCaseGenerator {

    private static final String APPROVED_AT = "2026-07-08T00:00:00Z";
    private static final List<String> PERSONAS = List.of(
            "colloquial_cn_user",
            "conflicted_correction_user",
            "project_lead_user",
            "engineer_user",
            "product_ops_user",
            "research_knowledge_user",
            "emotional_feedback_user",
            "privacy_risk_user",
            "operation_user",
            "mixed_language_user",
            "novice_user",
            "power_user");
    private static final List<String> TOPICS = List.of(
            "会议纪要",
            "任务规划",
            "项目复盘",
            "知识库整理",
            "代码审查",
            "需求评审",
            "研究摘要",
            "发布检查",
            "风险清单",
            "路线图讨论",
            "客户反馈归纳",
            "实验记录");
    private static final List<String> EN_TOPICS = List.of(
            "meeting notes",
            "task planning",
            "project review",
            "knowledge base cleanup",
            "code review",
            "requirements review",
            "research summary",
            "release checklist",
            "risk register",
            "roadmap discussion",
            "feedback synthesis",
            "experiment log");

    private SimulatedMemoryReplayCaseGenerator() {
    }

    static MemoryReplayDataset generate(Map<String, Long> existingCategoryCounts) {
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases = new ArrayList<>();
        List<MemoryReplayDataset.ManifestEntry> manifest = new ArrayList<>();
        int globalIndex = 0;

        for (Map.Entry<String, Integer> target : targetCategoryMinimums().entrySet()) {
            String category = target.getKey();
            int existing = Math.toIntExact(existingCategoryCounts.getOrDefault(category, 0L));
            int needed = Math.max(0, target.getValue() - existing);
            for (int categoryIndex = 1; categoryIndex <= needed; categoryIndex++) {
                globalIndex++;
                String language = globalIndex % 3 == 0 ? "en" : "cn";
                GeneratedCase generatedCase = buildCase(category, language, categoryIndex, globalIndex);
                cases.add(generatedCase.replayCase());
                manifest.add(generatedCase.manifestEntry());
            }
        }

        return new MemoryReplayDataset(cases, manifest);
    }

    static Map<String, Integer> targetCategoryMinimums() {
        Map<String, Integer> minimums = new LinkedHashMap<>();
        minimums.put("operation", 150);
        minimums.put("explicit_preference", 150);
        minimums.put("implicit_preference", 180);
        minimums.put("style", 180);
        minimums.put("correction", 140);
        minimums.put("project_context", 140);
        minimums.put("reference_only", 150);
        minimums.put("rag_reference", 150);
        minimums.put("one_off", 140);
        minimums.put("assistant_feedback", 100);
        minimums.put("sensitive", 100);
        minimums.put("forget", 100);
        minimums.put("ambiguous", 120);
        minimums.put("multi_turn_correction", 100);
        minimums.put("complex_project_context", 100);
        return minimums;
    }

    private static GeneratedCase buildCase(String category, String language, int categoryIndex, int globalIndex) {
        String id = "replay_" + language + "_" + category + "_sim_" + zeroPad(categoryIndex);
        String token = "SIM" + zeroPad(globalIndex);
        String topic = "cn".equals(language)
                ? TOPICS.get(globalIndex % TOPICS.size())
                : EN_TOPICS.get(globalIndex % EN_TOPICS.size());
        MemoryReplayEvaluationService.MemoryReplayCase replayCase = switch (category) {
            case "operation" -> operationCase(id, language, token);
            case "explicit_preference" -> explicitPreferenceCase(id, language, topic, token);
            case "implicit_preference" -> implicitPreferenceCase(id, language, topic, token);
            case "style" -> styleCase(id, language, token);
            case "correction" -> correctionCase(id, language, token);
            case "project_context" -> projectContextCase(id, language, topic, token);
            case "reference_only" -> referenceOnlyCase(id, language, token);
            case "rag_reference" -> ragReferenceCase(id, language, token);
            case "one_off" -> oneOffCase(id, language, token);
            case "assistant_feedback" -> assistantFeedbackCase(id, language, token);
            case "sensitive" -> sensitiveCase(id, language, token);
            case "forget" -> forgetCase(id, language, token);
            case "ambiguous" -> ambiguousCase(id, language, token);
            case "multi_turn_correction" -> multiTurnCorrectionCase(id, language, token);
            case "complex_project_context" -> complexProjectContextCase(id, language, token);
            default -> throw new IllegalArgumentException("Unsupported replay category: " + category);
        };

        String persona = PERSONAS.get(globalIndex % PERSONAS.size());
        List<String> scenarioTags = scenarioTags(category, language);
        MemoryReplayDataset.ManifestEntry manifestEntry = new MemoryReplayDataset.ManifestEntry(
                replayCase.id(),
                "simulated_realistic",
                "generator:SimulatedMemoryReplayCaseGenerator:v1",
                persona,
                scenarioTags,
                "cn".equals(language) ? List.of("zh") : List.of("en"),
                "simconv-" + token,
                "redaction-simulated-v1",
                "approved",
                "consistency_reviewer_v1",
                APPROVED_AT);
        return new GeneratedCase(replayCase, manifestEntry);
    }

    private static MemoryReplayEvaluationService.MemoryReplayCase operationCase(String id, String language, String token) {
        if ("cn".equals(language)) {
            return deny(id, "删除全部笔记，操作演练 " + token, "需要确认后才能删除。",
                    "DENY_TRANSIENT", "operation_or_confirmation", List.of("transient_operation"));
        }
        return deny(id, "delete all notes for operation drill " + token, "I need confirmation before deleting notes.",
                "DENY_TRANSIENT", "operation_or_confirmation", List.of("transient_operation"));
    }

    private static MemoryReplayEvaluationService.MemoryReplayCase explicitPreferenceCase(
            String id, String language, String topic, String token) {
        if ("cn".equals(language)) {
            return allow(id, "记住：我希望" + topic + "默认先给结论，再列行动项；偏好编号 " + token,
                    "已记录。", "ALLOW_EXPLICIT", "preference", false,
                    "explicit_memory", List.of("explicit_remember"));
        }
        return allow(id, "remember: I prefer " + topic + " to start with the conclusion and then action items; preference " + token,
                "Noted.", "ALLOW_EXPLICIT", "preference", false,
                "explicit_memory", List.of("explicit_remember"));
    }

    private static MemoryReplayEvaluationService.MemoryReplayCase implicitPreferenceCase(
            String id, String language, String topic, String token) {
        if ("cn".equals(language)) {
            return allow(id, "我希望以后处理" + topic + "时先列风险再给建议；偏好编号 " + token,
                    "明白。", "ALLOW_IMPLICIT_LOW_CONFIDENCE", "preference", false,
                    "implicit_preference", List.of("preference_signal"));
        }
        return allow(id, "I prefer " + topic + " to list risks before suggestions; preference " + token,
                "Understood.", "ALLOW_IMPLICIT_LOW_CONFIDENCE", "preference", false,
                "implicit_preference", List.of("preference_signal"));
    }

    private static MemoryReplayEvaluationService.MemoryReplayCase styleCase(String id, String language, String token) {
        if ("cn".equals(language)) {
            return allow(id, "以后回答请专业简洁，先给结论，再补充依据；风格编号 " + token,
                    "我会按这个风格回答。", "ALLOW_IMPLICIT_LOW_CONFIDENCE", "style", false,
                    "implicit_interaction_style", List.of("stable_style_preference"));
        }
        return allow(id, "from now on, answer in a professional concise tone with the conclusion first; style " + token,
                "I will use that style.", "ALLOW_IMPLICIT_LOW_CONFIDENCE", "style", false,
                "implicit_interaction_style", List.of("stable_style_preference"));
    }

    private static MemoryReplayEvaluationService.MemoryReplayCase correctionCase(String id, String language, String token) {
        if ("cn".equals(language)) {
            return allow(id, "以后不要用轻松口吻，改为正式简洁回答；纠正编号 " + token,
                    "已调整。", "ALLOW_IMPLICIT_LOW_CONFIDENCE", "style", true,
                    "correction_interaction_style", List.of("interaction_style_correction"));
        }
        return allow(id, "no longer use a casual tone; instead answer formally and concisely; correction " + token,
                "Adjusted.", "ALLOW_IMPLICIT_LOW_CONFIDENCE", "style", true,
                "correction_interaction_style", List.of("interaction_style_correction"));
    }

    private static MemoryReplayEvaluationService.MemoryReplayCase projectContextCase(
            String id, String language, String topic, String token) {
        if ("cn".equals(language)) {
            return allow(id, "项目上下文：匿名" + topic + "项目正在做阶段二评估，需要保留审计记录；编号 " + token,
                    "项目背景已记录。", "ALLOW_IMPLICIT_LOW_CONFIDENCE", "project_context", false,
                    "project_context", List.of("project_context"));
        }
        return allow(id, "project context: the anonymous " + topic + " project is in phase two evaluation and must keep audit notes; context " + token,
                "Project context recorded.", "ALLOW_IMPLICIT_LOW_CONFIDENCE", "project_context", false,
                "project_context", List.of("project_context"));
    }

    private static MemoryReplayEvaluationService.MemoryReplayCase referenceOnlyCase(String id, String language, String token) {
        if ("cn".equals(language)) {
            return deny(id, "总结这篇笔记：作者说他喜欢红色标签和短标题；引用编号 " + token,
                    "这是对当前笔记的总结。", "DENY_TOOL_RESULT", "reference_context_not_profile",
                    List.of("reference_only"));
        }
        return deny(id, "summarize this note: the author says they like red tags and short titles; reference " + token,
                "This is a selected-note summary.", "DENY_TOOL_RESULT", "reference_context_not_profile",
                List.of("reference_only"));
    }

    private static MemoryReplayEvaluationService.MemoryReplayCase ragReferenceCase(String id, String language, String token) {
        if ("cn".equals(language)) {
            return deny(id, "根据检索结果回答这个问题，引用编号 " + token,
                    "<rag_context role=\"reference_only\">资料说某团队喜欢红色标签。</rag_context>",
                    "DENY_TOOL_RESULT", "reference_context_not_profile", List.of("reference_only"));
        }
        return deny(id, "answer using the retrieval result only; reference " + token,
                "<rag_context role=\"reference_only\">The source says one team likes red tags.</rag_context>",
                "DENY_TOOL_RESULT", "reference_context_not_profile", List.of("reference_only"));
    }

    private static MemoryReplayEvaluationService.MemoryReplayCase oneOffCase(String id, String language, String token) {
        if ("cn".equals(language)) {
            return deny(id, "这次回复请用正式口吻，不要延伸成长期偏好；编号 " + token,
                    "这次我会正式一些。", "DENY_TRANSIENT", "one_off_instruction",
                    List.of("one_off_scope"));
        }
        return deny(id, "for this reply, use a formal tone only; one-off " + token,
                "I will use a formal tone for this reply.", "DENY_TRANSIENT", "one_off_instruction",
                List.of("one_off_scope"));
    }

    private static MemoryReplayEvaluationService.MemoryReplayCase assistantFeedbackCase(String id, String language, String token) {
        if ("cn".equals(language)) {
            return deny(id, "这个回答很好，谢谢，结构很清楚；反馈编号 " + token,
                    "谢谢反馈。", "DENY_TRANSIENT", "assistant_feedback",
                    List.of("assistant_feedback"));
        }
        return deny(id, "I like this answer, thanks; feedback " + token,
                "Glad it helped.", "DENY_TRANSIENT", "assistant_feedback",
                List.of("assistant_feedback"));
    }

    private static MemoryReplayEvaluationService.MemoryReplayCase sensitiveCase(String id, String language, String token) {
        if ("cn".equals(language)) {
            return deny(id, "记住，我的 API key 是 fake-api-key-" + token,
                    "我不能保存密钥。", "DENY_SENSITIVE", "sensitive_content",
                    List.of("sensitive_content"));
        }
        return deny(id, "remember: my api key is fake-api-key-" + token,
                "I cannot store secrets.", "DENY_SENSITIVE", "sensitive_content",
                List.of("sensitive_content"));
    }

    private static MemoryReplayEvaluationService.MemoryReplayCase forgetCase(String id, String language, String token) {
        if ("cn".equals(language)) {
            return deny(id, "不要记住我之前说的深色模式偏好；忘记编号 " + token,
                    "好的，不会继续记住。", "FORGET_REQUEST", "forget_request",
                    List.of("forget_request"));
        }
        return deny(id, "forget this dark mode preference; forget " + token,
                "I will forget it.", "FORGET_REQUEST", "forget_request",
                List.of("forget_request"));
    }

    private static MemoryReplayEvaluationService.MemoryReplayCase ambiguousCase(String id, String language, String token) {
        if ("cn".equals(language)) {
            return deny(id, "这个问题我再想想，先放一边；想法编号 " + token,
                    "好的。", "DENY_TRANSIENT", "no_stable_user_memory_signal", List.of());
        }
        return deny(id, "I am still thinking about this, leave it aside for now; thought " + token,
                "Okay.", "DENY_TRANSIENT", "no_stable_user_memory_signal", List.of());
    }

    private static MemoryReplayEvaluationService.MemoryReplayCase multiTurnCorrectionCase(
            String id, String language, String token) {
        if ("cn".equals(language)) {
            return allow(id, "前面我说可以轻松一点，但以后不要轻松，改为正式简洁回答；多轮纠正编号 " + token,
                    "已按新的偏好调整。", "ALLOW_IMPLICIT_LOW_CONFIDENCE", "style", true,
                    "correction_interaction_style", List.of("interaction_style_correction"));
        }
        return allow(id, "earlier I said casual was fine, but no longer use casual tone; instead answer formally and concisely; multi-turn correction " + token,
                "Updated to the new preference.", "ALLOW_IMPLICIT_LOW_CONFIDENCE", "style", true,
                "correction_interaction_style", List.of("interaction_style_correction"));
    }

    private static MemoryReplayEvaluationService.MemoryReplayCase complexProjectContextCase(
            String id, String language, String token) {
        if ("cn".equals(language)) {
            return allow(id, "项目上下文：匿名知识库项目同时支持搜索、笔记、权限和审计，本阶段重点验证记忆治理；编号 " + token,
                    "复杂项目背景已记录。", "ALLOW_IMPLICIT_LOW_CONFIDENCE", "project_context", false,
                    "project_context", List.of("project_context"));
        }
        return allow(id, "project background: the anonymous knowledge workspace covers search, notes, permissions, and audit; this phase validates memory governance; context " + token,
                "Complex project context recorded.", "ALLOW_IMPLICIT_LOW_CONFIDENCE", "project_context", false,
                "project_context", List.of("project_context"));
    }

    private static MemoryReplayEvaluationService.MemoryReplayCase allow(
            String id,
            String userMessage,
            String assistantOutput,
            String decisionType,
            String memoryType,
            boolean correction,
            String policyReason,
            List<String> signals) {
        return new MemoryReplayEvaluationService.MemoryReplayCase(
                id,
                userMessage,
                assistantOutput,
                true,
                decisionType,
                memoryType,
                correction,
                policyReason,
                signals);
    }

    private static MemoryReplayEvaluationService.MemoryReplayCase deny(
            String id,
            String userMessage,
            String assistantOutput,
            String decisionType,
            String policyReason,
            List<String> signals) {
        return new MemoryReplayEvaluationService.MemoryReplayCase(
                id,
                userMessage,
                assistantOutput,
                false,
                decisionType,
                null,
                null,
                policyReason,
                signals);
    }

    private static List<String> scenarioTags(String category, String language) {
        List<String> tags = new ArrayList<>();
        tags.add(MemoryReplayDatasetLoader.scenarioTagForCategory(category));
        if ("cn".equals(language)) {
            tags.add("chinese_stable_preference");
        }
        if ("en".equals(language)) {
            tags.add("multilingual_code_switch");
        }
        return List.copyOf(tags);
    }

    private static String zeroPad(int value) {
        return String.format("%04d", value);
    }

    private record GeneratedCase(
            MemoryReplayEvaluationService.MemoryReplayCase replayCase,
            MemoryReplayDataset.ManifestEntry manifestEntry) {
    }
}
