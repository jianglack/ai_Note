package com.ainote.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InternalTokenValidator {

    @Value("${app.internal.token:}")
    private String internalToken;

    public boolean validate(String token) {
        if (token == null || token.isEmpty() || internalToken.isEmpty()) {
            return false;
        }
        return internalToken.equals(token);
    }
}
