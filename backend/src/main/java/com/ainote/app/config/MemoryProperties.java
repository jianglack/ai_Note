package com.ainote.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.memory")
public class MemoryProperties {

    private int maxPerUser = 50;
    private double similarityThreshold = 0.85;
    private double decayHalfLifeDays = 30.0;
    private Orchestrator orchestrator = new Orchestrator();
    private Capture capture = new Capture();
    private Retrieval retrieval = new Retrieval();
    private Audit audit = new Audit();
    private ChatHistory chatHistory = new ChatHistory();
    private Privacy privacy = new Privacy();

    public enum CaptureMode {
        LEGACY,
        POLICY
    }

    public enum RetrievalMode {
        LEGACY,
        QUERY_RELEVANT
    }

    public enum ChatHistoryWriteMode {
        APPEND,
        LEGACY_REWRITE
    }

    public static class Orchestrator {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Capture {
        private boolean enabled = true;
        private CaptureMode mode = CaptureMode.LEGACY;
        private Advisor advisor = new Advisor();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public CaptureMode getMode() {
            return mode;
        }

        public void setMode(CaptureMode mode) {
            this.mode = mode == null ? CaptureMode.LEGACY : mode;
        }

        public Advisor getAdvisor() {
            return advisor;
        }

        public void setAdvisor(Advisor advisor) {
            this.advisor = advisor == null ? new Advisor() : advisor;
        }
    }

    public static class Advisor {
        private boolean enabled = false;
        private double minConfidence = 0.80;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getMinConfidence() {
            return minConfidence;
        }

        public void setMinConfidence(double minConfidence) {
            this.minConfidence = minConfidence <= 0 ? 0.80 : minConfidence;
        }
    }

    public static class Retrieval {
        private RetrievalMode mode = RetrievalMode.LEGACY;

        public RetrievalMode getMode() {
            return mode;
        }

        public void setMode(RetrievalMode mode) {
            this.mode = mode == null ? RetrievalMode.LEGACY : mode;
        }
    }

    public static class Audit {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class ChatHistory {
        private ChatHistoryWriteMode writeMode = ChatHistoryWriteMode.APPEND;
        private int modelWindowMaxMessages = 256;
        private int flushMaxAttempts = 3;
        private long flushRetryDelayMs = 25;
        private boolean compactionEnabled = true;
        private int compactionMinUserTurns = 3;
        private int compactionWorkerBatchSize = 4;
        private int compactionMaxAttempts = 5;
        private int compactionLeaseSeconds = 300;
        private int compactionMaxSourceMessages = 64;
        private int compactionMaxSourceCharacters = 12000;
        private long compactionPollMs = 5000;

        public ChatHistoryWriteMode getWriteMode() {
            return writeMode;
        }

        public void setWriteMode(ChatHistoryWriteMode writeMode) {
            this.writeMode = writeMode == null ? ChatHistoryWriteMode.APPEND : writeMode;
        }

        public int getModelWindowMaxMessages() { return modelWindowMaxMessages; }
        public void setModelWindowMaxMessages(int value) { modelWindowMaxMessages = Math.max(8, value); }
        public int getFlushMaxAttempts() { return flushMaxAttempts; }
        public void setFlushMaxAttempts(int value) { flushMaxAttempts = Math.max(1, value); }
        public long getFlushRetryDelayMs() { return flushRetryDelayMs; }
        public void setFlushRetryDelayMs(long value) { flushRetryDelayMs = Math.max(0, value); }
        public boolean isCompactionEnabled() { return compactionEnabled; }
        public void setCompactionEnabled(boolean value) { compactionEnabled = value; }
        public int getCompactionMinUserTurns() { return compactionMinUserTurns; }
        public void setCompactionMinUserTurns(int value) { compactionMinUserTurns = Math.max(1, value); }
        public int getCompactionWorkerBatchSize() { return compactionWorkerBatchSize; }
        public void setCompactionWorkerBatchSize(int value) { compactionWorkerBatchSize = Math.max(1, value); }
        public int getCompactionMaxAttempts() { return compactionMaxAttempts; }
        public void setCompactionMaxAttempts(int value) { compactionMaxAttempts = Math.max(1, value); }
        public int getCompactionLeaseSeconds() { return compactionLeaseSeconds; }
        public void setCompactionLeaseSeconds(int value) { compactionLeaseSeconds = Math.max(30, value); }
        public int getCompactionMaxSourceMessages() { return compactionMaxSourceMessages; }
        public void setCompactionMaxSourceMessages(int value) { compactionMaxSourceMessages = Math.max(8, value); }
        public int getCompactionMaxSourceCharacters() { return compactionMaxSourceCharacters; }
        public void setCompactionMaxSourceCharacters(int value) {
            compactionMaxSourceCharacters = Math.max(1000, value);
        }
        public long getCompactionPollMs() { return compactionPollMs; }
        public void setCompactionPollMs(long value) { compactionPollMs = Math.max(1000, value); }
    }

    public static class Privacy {
        private boolean redactExports = true;
        private int exportMaxItems = 1000;
        private int deletedMemoryRetentionDays = 30;
        private boolean atRestEncryptionRequired = true;
        private boolean atRestEncryptionConfirmed = false;
        private String atRestEncryptionKeyRef = "";

        public boolean isRedactExports() {
            return redactExports;
        }

        public void setRedactExports(boolean redactExports) {
            this.redactExports = redactExports;
        }

        public int getExportMaxItems() {
            return exportMaxItems;
        }

        public void setExportMaxItems(int exportMaxItems) {
            this.exportMaxItems = exportMaxItems <= 0 ? 1000 : exportMaxItems;
        }

        public int getDeletedMemoryRetentionDays() {
            return deletedMemoryRetentionDays;
        }

        public void setDeletedMemoryRetentionDays(int deletedMemoryRetentionDays) {
            this.deletedMemoryRetentionDays = deletedMemoryRetentionDays < 1 ? 30 : deletedMemoryRetentionDays;
        }

        public boolean isAtRestEncryptionRequired() {
            return atRestEncryptionRequired;
        }

        public void setAtRestEncryptionRequired(boolean atRestEncryptionRequired) {
            this.atRestEncryptionRequired = atRestEncryptionRequired;
        }

        public boolean isAtRestEncryptionConfirmed() {
            return atRestEncryptionConfirmed;
        }

        public void setAtRestEncryptionConfirmed(boolean atRestEncryptionConfirmed) {
            this.atRestEncryptionConfirmed = atRestEncryptionConfirmed;
        }

        public String getAtRestEncryptionKeyRef() {
            return atRestEncryptionKeyRef;
        }

        public void setAtRestEncryptionKeyRef(String atRestEncryptionKeyRef) {
            this.atRestEncryptionKeyRef = atRestEncryptionKeyRef == null ? "" : atRestEncryptionKeyRef.trim();
        }
    }

    public int getMaxPerUser() {
        return maxPerUser;
    }

    public void setMaxPerUser(int maxPerUser) {
        this.maxPerUser = maxPerUser;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public double getDecayHalfLifeDays() {
        return decayHalfLifeDays;
    }

    public void setDecayHalfLifeDays(double decayHalfLifeDays) {
        this.decayHalfLifeDays = decayHalfLifeDays;
    }

    public Orchestrator getOrchestrator() {
        return orchestrator;
    }

    public void setOrchestrator(Orchestrator orchestrator) {
        this.orchestrator = orchestrator == null ? new Orchestrator() : orchestrator;
    }

    public Capture getCapture() {
        return capture;
    }

    public void setCapture(Capture capture) {
        this.capture = capture == null ? new Capture() : capture;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public void setRetrieval(Retrieval retrieval) {
        this.retrieval = retrieval == null ? new Retrieval() : retrieval;
    }

    public Audit getAudit() {
        return audit;
    }

    public void setAudit(Audit audit) {
        this.audit = audit == null ? new Audit() : audit;
    }

    public ChatHistory getChatHistory() {
        return chatHistory;
    }

    public void setChatHistory(ChatHistory chatHistory) {
        this.chatHistory = chatHistory == null ? new ChatHistory() : chatHistory;
    }

    public Privacy getPrivacy() {
        return privacy;
    }

    public void setPrivacy(Privacy privacy) {
        this.privacy = privacy == null ? new Privacy() : privacy;
    }
}
