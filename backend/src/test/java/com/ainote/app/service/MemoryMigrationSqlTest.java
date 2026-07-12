package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryMigrationSqlTest {

    @Test
    void legacySemanticMemoryBackfillMigrationPreservesGovernanceIntent() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V57__backfill_legacy_semantic_memories.sql"));

        assertThat(sql).contains("status = COALESCE(status, 'active')");
        assertThat(sql).contains("source = 'legacy_ai_extracted'");
        assertThat(sql).contains("memory_type = LOWER(category)");
        assertThat(sql).contains("content_hash = md5");
        assertThat(sql).contains("\"legacy\": true");
        assertThat(sql).contains("WHERE metadata_json IS NULL");
    }

    @Test
    void memoryReviewCaseMigrationCreatesHumanReviewWorkflowSchema() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V58__memory_review_cases.sql"));

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS memory_review_cases");
        assertThat(sql).contains("feedback_type VARCHAR(40) NOT NULL");
        assertThat(sql).contains("status VARCHAR(40) NOT NULL DEFAULT 'pending_review'");
        assertThat(sql).contains("reviewer_decision VARCHAR(40)");
        assertThat(sql).contains("replay_case_id VARCHAR(128)");
        assertThat(sql).contains("replay_case_json TEXT");
        assertThat(sql).contains("manifest_json TEXT");
        assertThat(sql).contains("memory_before_json TEXT");
        assertThat(sql).contains("source_context_json TEXT");
        assertThat(sql).contains("policy_snapshot_json TEXT");
        assertThat(sql).contains("idx_memory_review_cases_status_created");
        assertThat(sql).contains("idx_memory_review_cases_user_created");
        assertThat(sql).contains("idx_memory_review_cases_memory_id");
        assertThat(sql).contains("idx_memory_review_cases_replay_case_id");
    }

    @Test
    void memoryPrivacyComplianceMigrationCreatesAuditAndRetentionIndexes() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V59__memory_privacy_compliance.sql"));

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS admin_access_audits");
        assertThat(sql).contains("user_id VARCHAR(128) NOT NULL");
        assertThat(sql).contains("action VARCHAR(128) NOT NULL");
        assertThat(sql).contains("decision VARCHAR(32) NOT NULL");
        assertThat(sql).contains("idx_admin_access_audits_user_time");
        assertThat(sql).contains("idx_admin_access_audits_action_time");
        assertThat(sql).contains("idx_admin_access_audits_decision_time");
        assertThat(sql).contains("idx_semantic_memories_status_updated");
    }

    @Test
    void shortTermChatMemoryMigrationEnforcesConcurrencyAndCompactionInvariants() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V60__short_term_chat_memory_reliability.sql"));

        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS trimmed_at TIMESTAMP");
        assertThat(sql).contains("ROW_NUMBER() OVER");
        assertThat(sql).contains("ALTER COLUMN sequence_number SET NOT NULL");
        assertThat(sql).contains("uq_user_memories_user_sequence");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS chat_memory_heads");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS chat_memory_compaction_jobs");
        assertThat(sql).contains("uq_chat_memory_compaction_range");
        assertThat(sql).contains("uq_episodic_user_source_range");
        assertThat(sql).doesNotContain("DELETE FROM user_memories");
    }
}
