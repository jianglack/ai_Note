package com.ainote.app.model.memory;

public record MemoryCompliancePostureResponse(
        boolean piiDetectionEnabled,
        boolean exportRedactionEnabled,
        int deletedMemoryRetentionDays,
        boolean atRestEncryptionRequired,
        boolean atRestEncryptionConfirmed,
        String atRestEncryptionKeyRef,
        String status
) {
}
