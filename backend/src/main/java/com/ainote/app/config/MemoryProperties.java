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
        private double minConfidence = 0.82;

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
            this.minConfidence = minConfidence <= 0 ? 0.82 : minConfidence;
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

        public ChatHistoryWriteMode getWriteMode() {
            return writeMode;
        }

        public void setWriteMode(ChatHistoryWriteMode writeMode) {
            this.writeMode = writeMode == null ? ChatHistoryWriteMode.APPEND : writeMode;
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
}
