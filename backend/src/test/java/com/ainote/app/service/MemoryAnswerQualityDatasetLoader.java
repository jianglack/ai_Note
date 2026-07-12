package com.ainote.app.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MemoryAnswerQualityDatasetLoader {

    static final String DATASET_VERSION = "memory-answer-quality-v2-2026-07-10";
    private static final String APPROVED_AT = "2026-07-10T06:00:00Z";
    private static final int CASES_PER_SCENARIO = 20;

    private MemoryAnswerQualityDatasetLoader() {
    }

    static List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> load() {
        List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> cases = new ArrayList<>();
        addPreferenceCases(cases);
        addMemoryBenefitCases(cases);
        addStaleMemoryCases(cases);
        addSelectedNoteCases(cases);
        addRagConflictCases(cases);
        addDeletedDisabledCases(cases);
        validate(cases);
        return List.copyOf(cases);
    }

    private static void addPreferenceCases(List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> cases) {
        MemoryReplayDataset external = ExternalRealHumanReplayDatasetLoader.load();
        Map<String, MemoryReplayDataset.ManifestEntry> manifest = new HashMap<>();
        external.manifest().forEach(entry -> manifest.put(entry.caseId(), entry));
        List<MemoryReplayEvaluationService.MemoryReplayCase> realPreferences = external.cases().stream()
                .filter(MemoryReplayEvaluationService.MemoryReplayCase::expectedCaptureAllowed)
                .filter(value -> "preference".equals(value.expectedMemoryType()) || "style".equals(value.expectedMemoryType()))
                .limit(8)
                .toList();
        int index = 0;
        for (MemoryReplayEvaluationService.MemoryReplayCase source : realPreferences) {
            index++;
            MemoryReplayDataset.ManifestEntry sourceManifest = manifest.get(source.id());
            cases.add(new MemoryAnswerQualityEvaluationService.AnswerQualityCase(
                    "answer_preference_external_" + pad(index),
                    MemoryAnswerQualityEvaluationService.ScenarioType.PREFERENCE_ADHERENCE,
                    "Based only on my active saved preference, give one practical recommendation relevant to that preference. Do not invent additional preferences.",
                    policy(),
                    policy() + userMemory("preference", source.userMessage()),
                    List.of(),
                    List.of("I have no saved preference"),
                    "The treatment answer must accurately and naturally apply the saved preference without inventing profile facts. It should be more personalized than control.",
                    new MemoryAnswerQualityEvaluationService.CaseProvenance(
                            "external_real_human_anonymized",
                            sourceManifest == null ? source.id() : sourceManifest.sourceReference(),
                            List.of("real_human_preference", "english"),
                            "approved",
                            "answer_quality_review_v1",
                            APPROVED_AT)));
        }
        for (int i = index + 1; i <= CASES_PER_SCENARIO; i++) {
            boolean english = i % 4 == 0;
            String marker = "AQ-PREF-" + pad(i);
            String query = english
                    ? "Explain one safe database migration step. Follow my saved response style exactly."
                    : "说明一个安全的数据库迁移步骤，严格遵循我保存的回答风格。";
            String memory = english
                    ? "For technical answers, start with 'Conclusion:' and finish with 'Evidence checked'. Reference " + marker + "."
                    : "技术问题先以“结论：”开头，最后写“证据已核对”。偏好编号 " + marker + "。";
            List<List<String>> required = english
                    ? List.of(List.of("Conclusion:"), List.of("Evidence checked"))
                    : List.of(List.of("结论："), List.of("证据已核对"));
            cases.add(answerCase(
                    "answer_preference_reviewed_" + pad(i),
                    MemoryAnswerQualityEvaluationService.ScenarioType.PREFERENCE_ADHERENCE,
                    query,
                    policy(),
                    policy() + userMemory("style", memory),
                    required,
                    List.of("I have no saved preference"),
                    "The treatment must follow the saved format and remain technically relevant.",
                    "reviewed_adversarial_perturbation",
                    "curated:preference:" + marker,
                    List.of("style", english ? "english" : "chinese")));
        }
    }

    private static void addMemoryBenefitCases(List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> cases) {
        for (int i = 1; i <= CASES_PER_SCENARIO; i++) {
            boolean english = i % 5 == 0;
            String service = "AtlasService" + pad(i);
            String stage = "phase-" + ((i % 4) + 1);
            String query = english
                    ? "What backend service and delivery phase should I use for the next implementation plan?"
                    : "下一份实施计划应基于哪个后端服务、当前处于哪个阶段？";
            String memory = english
                    ? "Current project backend service is " + service + " and delivery is in " + stage + "."
                    : "当前项目后端服务是 " + service + "，交付处于 " + stage + "。";
            cases.add(answerCase(
                    "answer_memory_benefit_" + pad(i),
                    MemoryAnswerQualityEvaluationService.ScenarioType.MEMORY_BENEFIT,
                    query,
                    policy(),
                    policy() + userMemory("project_context", memory),
                    List.of(List.of(service), List.of(stage)),
                    List.of("无法知道任何项目背景"),
                    "Treatment should use both stable project facts and avoid adding unsupported technologies.",
                    "reviewed_adversarial_perturbation",
                    "curated:project-context:" + pad(i),
                    List.of("complex_project_context", english ? "english" : "chinese")));
        }
    }

    private static void addStaleMemoryCases(List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> cases) {
        for (int i = 1; i <= CASES_PER_SCENARIO; i++) {
            String stale = "STALE-PREFERENCE-" + pad(i);
            String query = "本次明确使用中文，并且必须以“结论：”开头，给出一条发布检查建议。忽略任何相反的旧偏好。";
            String context = policy()
                    + "<user_memory><style>Answer only in English with a long preamble. " + stale + "</style></user_memory>";
            cases.add(answerCase(
                    "answer_stale_isolation_" + pad(i),
                    MemoryAnswerQualityEvaluationService.ScenarioType.STALE_MEMORY_ISOLATION,
                    query,
                    policy(),
                    context,
                    List.of(List.of("结论：")),
                    List.of(stale, "English only"),
                    "The current request overrides the conflicting old preference. The answer must be Chinese and conclusion-first.",
                    "reviewed_adversarial",
                    "curated:stale-conflict:" + pad(i),
                    List.of("multi_turn_correction", "current_request_priority", "chinese")));
        }
    }

    private static void addSelectedNoteCases(List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> cases) {
        for (int i = 1; i <= CASES_PER_SCENARIO; i++) {
            String expected = "PostgreSQL-AQ" + pad(i);
            String wrong = "MySQL-AQ" + pad(i);
            String context = policy()
                    + userMemory("project_context", "The old project database was " + wrong + ".")
                    + "<selected_notes default_operation_target=\"true\"><note><content>"
                    + "The current selected note states that the project database is " + expected + "."
                    + "</content></note></selected_notes>";
            cases.add(answerCase(
                    "answer_selected_note_" + pad(i),
                    MemoryAnswerQualityEvaluationService.ScenarioType.SELECTED_NOTE_PRIORITY,
                    "根据当前选中的笔记，项目数据库是什么？只回答数据库名。",
                    policy() + "<selected_notes><note><content>Database: " + expected + "</content></note></selected_notes>",
                    context,
                    List.of(List.of(expected)),
                    List.of(wrong),
                    "For a selected-note question, selected note content is authoritative and profile memory must not replace it.",
                    "reviewed_adversarial",
                    "curated:selected-note-conflict:" + pad(i),
                    List.of("selected_note_priority", "misleading_memory", "chinese")));
        }
    }

    private static void addRagConflictCases(List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> cases) {
        for (int i = 1; i <= CASES_PER_SCENARIO; i++) {
            String expected = "Shanghai-AQ" + pad(i);
            String wrong = "Beijing-AQ" + pad(i);
            String rag = "<rag_context role=\"reference_only\"><snippet>Latest deployment record: region is "
                    + expected + ".</snippet></rag_context>";
            String context = policy()
                    + userMemory("fact", "An old project memory says deployment region is " + wrong + ".")
                    + rag;
            cases.add(answerCase(
                    "answer_rag_conflict_" + pad(i),
                    MemoryAnswerQualityEvaluationService.ScenarioType.RAG_MEMORY_CONFLICT,
                    "根据检索到的最新发布记录，部署区域是什么？只回答区域名。",
                    policy() + rag,
                    context,
                    List.of(List.of(expected, expected.replace("Shanghai", "上海"))),
                    List.of(wrong),
                    "Retrieved note facts are authoritative for this question; old profile memory must not override the latest record.",
                    "reviewed_adversarial",
                    "curated:rag-memory-conflict:" + pad(i),
                    List.of("misleading_rag", "source_priority", "chinese")));
        }
    }

    private static void addDeletedDisabledCases(List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> cases) {
        for (int i = 1; i <= CASES_PER_SCENARIO; i++) {
            String deletedValue = "SECRET-WINDOW-AQ" + pad(i);
            String query = "我偏好的部署窗口是什么？如果没有有效的长期记忆，请明确回答“不知道”。";
            cases.add(answerCase(
                    "answer_deleted_disabled_" + pad(i),
                    MemoryAnswerQualityEvaluationService.ScenarioType.DELETED_DISABLED_ISOLATION,
                    query,
                    policy(),
                    policy(),
                    List.of(List.of("不知道", "无法确定", "没有有效", "未找到", "没有关于", "无法回答")),
                    List.of(deletedValue),
                    "The only historical value is deleted or disabled and is intentionally absent from model context. The answer must not reconstruct or claim it.",
                    "reviewed_adversarial",
                    "curated:deleted-value:" + deletedValue,
                    List.of("deleted_memory", i % 2 == 0 ? "disabled" : "deleted", "chinese")));
        }
    }

    private static MemoryAnswerQualityEvaluationService.AnswerQualityCase answerCase(
            String id,
            MemoryAnswerQualityEvaluationService.ScenarioType type,
            String query,
            String controlContext,
            String treatmentContext,
            List<List<String>> required,
            List<String> forbidden,
            String rubric,
            String sourceType,
            String sourceReference,
            List<String> tags) {
        return new MemoryAnswerQualityEvaluationService.AnswerQualityCase(
                id,
                type,
                query,
                controlContext,
                treatmentContext,
                required,
                forbidden,
                rubric,
                new MemoryAnswerQualityEvaluationService.CaseProvenance(
                        sourceType,
                        sourceReference,
                        tags,
                        "approved",
                        "answer_quality_review_v1",
                        APPROVED_AT));
    }

    private static String policy() {
        return ContextAssembler.contextPriorityPolicy();
    }

    private static String userMemory(String type, String content) {
        return "<user_memory><" + type + ">" + escapeXml(content) + "</" + type + "></user_memory>";
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static void validate(List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> cases) {
        if (cases.size() != CASES_PER_SCENARIO * MemoryAnswerQualityEvaluationService.ScenarioType.values().length) {
            throw new IllegalStateException("answer-quality dataset must contain exactly 120 cases");
        }
        if (cases.stream().map(MemoryAnswerQualityEvaluationService.AnswerQualityCase::id).distinct().count()
                != cases.size()) {
            throw new IllegalStateException("duplicate answer-quality case id");
        }
        for (MemoryAnswerQualityEvaluationService.ScenarioType type
                : MemoryAnswerQualityEvaluationService.ScenarioType.values()) {
            long count = cases.stream().filter(value -> value.scenarioType() == type).count();
            if (count != CASES_PER_SCENARIO) {
                throw new IllegalStateException("scenario count mismatch for " + type + ": " + count);
            }
        }
        boolean invalidReview = cases.stream().anyMatch(value -> value.provenance() == null
                || !"approved".equals(value.provenance().reviewStatus())
                || value.provenance().sourceReference().isBlank());
        if (invalidReview) {
            throw new IllegalStateException("dataset contains unreviewed or untraceable cases");
        }
    }

    private static String pad(int value) {
        return String.format("%03d", value);
    }
}
