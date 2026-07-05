package com.ainote.app.service;

import java.util.List;

public interface MemorySignalAdvisor {

    AdvisorResult advise(MemoryCapturePolicy.CaptureRequest request);

    static MemorySignalAdvisor disabled() {
        return request -> AdvisorResult.unavailable(List.of("advisor_disabled"), "advisor disabled");
    }

    record AdvisorResult(boolean available,
                         boolean shouldCapture,
                         String memoryType,
                         double confidence,
                         List<String> signals,
                         String reason) {
        public AdvisorResult {
            memoryType = memoryType == null ? "none" : memoryType;
            signals = signals == null ? List.of() : List.copyOf(signals);
            reason = reason == null ? "" : reason;
        }

        public static AdvisorResult capture(String memoryType,
                                            double confidence,
                                            List<String> signals,
                                            String reason) {
            return new AdvisorResult(true, true, memoryType, confidence, signals, reason);
        }

        public static AdvisorResult noCapture(String memoryType,
                                              double confidence,
                                              List<String> signals,
                                              String reason) {
            return new AdvisorResult(true, false, memoryType, confidence, signals, reason);
        }

        public static AdvisorResult unavailable(List<String> signals, String reason) {
            return new AdvisorResult(false, false, "none", 0.0, signals, reason);
        }
    }
}
