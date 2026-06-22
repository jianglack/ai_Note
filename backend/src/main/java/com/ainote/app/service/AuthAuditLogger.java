package com.ainote.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AuthAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuthAuditLogger.class);

    public void passwordResetRequested(String email, String ipAddress, boolean knownAccount) {
        log.info("auth_event=password_reset_requested email={} ip={} known_account={}",
                email, ipAddress, knownAccount);
    }

    public void passwordResetConfirmed(String userId) {
        log.info("auth_event=password_reset_confirmed user_id={}", userId);
    }

    public void passwordResetRejected(String reason) {
        log.warn("auth_event=password_reset_rejected reason={}", reason);
    }
}
