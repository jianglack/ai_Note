package com.ainote.app.service;

public record MemoryScore(double semanticSimilarity,
                          double confidence,
                          double recencyDecay,
                          double reinforcement,
                          double scopePriority) {

    public double total() {
        return 0.45 * clamp(semanticSimilarity)
                + 0.20 * clamp(confidence)
                + 0.15 * clamp(recencyDecay)
                + 0.10 * clamp(reinforcement)
                + 0.10 * clamp(scopePriority);
    }

    private double clamp(double value) {
        if (Double.isNaN(value) || value < 0) {
            return 0;
        }
        if (value > 1) {
            return 1;
        }
        return value;
    }
}
