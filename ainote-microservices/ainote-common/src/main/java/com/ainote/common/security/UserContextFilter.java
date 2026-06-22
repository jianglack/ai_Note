package com.ainote.common.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class UserContextFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            HttpServletRequest httpRequest = (HttpServletRequest) request;

            String userIdHeader = httpRequest.getHeader("X-User-Id");
            if (userIdHeader != null && !userIdHeader.isEmpty()) {
                UserContext.setCurrentUserId(Long.parseLong(userIdHeader));
            }

            String usernameHeader = httpRequest.getHeader("X-Username");
            if (usernameHeader != null && !usernameHeader.isEmpty()) {
                UserContext.setCurrentUsername(usernameHeader);
            }

            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}
