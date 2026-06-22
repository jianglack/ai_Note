package com.ainote.app.service;

import com.ainote.app.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class LoggingPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetNotifier.class);

    @Override
    public void sendPasswordResetToken(User user, String rawToken) {
        log.warn("Password reset requested for user {}, but no delivery provider is configured", user.getId());
        throw new UnsupportedOperationException("Password reset delivery is not configured");
    }
}
