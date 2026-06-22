package com.ainote.common.config;

import com.ainote.common.security.UserContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;

public class FeignUserInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        Long userId = UserContext.getCurrentUserId();
        if (userId != null) {
            template.header("X-User-Id", userId.toString());
        }

        String username = UserContext.getCurrentUsername();
        if (username != null) {
            template.header("X-Username", username);
        }
    }
}
