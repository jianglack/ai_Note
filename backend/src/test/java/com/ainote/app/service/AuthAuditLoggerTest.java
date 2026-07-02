package com.ainote.app.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class AuthAuditLoggerTest {

    private AuthAuditLogger auditLogger;
    private ch.qos.logback.classic.Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        auditLogger = new AuthAuditLogger();
        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AuthAuditLogger.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void passwordResetRequested_writesStructuredAuditFields() {
        auditLogger.passwordResetRequested("user@example.com", "203.0.113.10", true);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .contains("auth_event=password_reset_requested")
                .contains("user@example.com")
                .contains("203.0.113.10")
                .contains("known_account=true");
    }

    @Test
    void passwordResetConfirmed_writesUserId() {
        auditLogger.passwordResetConfirmed("user-1");

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("auth_event=password_reset_confirmed")
                .contains("user_id=user-1");
    }

    @Test
    void passwordResetRejected_writesWarningReason() {
        auditLogger.passwordResetRejected("expired-token");

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("auth_event=password_reset_rejected")
                .contains("reason=expired-token");
    }
}
