package com.ainote.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class PrometheusScrapeTokenFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-AiNote-Monitoring-Token";
    private static final String ENDPOINT = "/actuator/prometheus";

    private final byte[] expectedToken;

    public PrometheusScrapeTokenFilter(String configuredToken) {
        this.expectedToken = StringUtils.hasText(configuredToken)
                ? configuredToken.getBytes(StandardCharsets.UTF_8)
                : new byte[0];
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String applicationPath = request.getRequestURI().substring(request.getContextPath().length());
        return !ENDPOINT.equals(applicationPath);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (expectedToken.length == 0) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Monitoring endpoint unavailable");
            return;
        }

        String supplied = request.getHeader(HEADER_NAME);
        byte[] suppliedToken = supplied == null
                ? new byte[0]
                : supplied.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedToken, suppliedToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Monitoring authentication required");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
