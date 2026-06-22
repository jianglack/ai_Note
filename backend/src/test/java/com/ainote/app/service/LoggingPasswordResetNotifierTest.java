package com.ainote.app.service;

import com.ainote.app.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoggingPasswordResetNotifierTest {

    @Test
    void defaultNotifierFailsClosedWhenDeliveryIsNotConfigured() {
        User user = new User();
        user.setId("user-1");
        user.setEmail("user@example.com");

        LoggingPasswordResetNotifier notifier = new LoggingPasswordResetNotifier();

        assertThatThrownBy(() -> notifier.sendPasswordResetToken(user, "raw-token"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Password reset delivery is not configured");
    }
}
