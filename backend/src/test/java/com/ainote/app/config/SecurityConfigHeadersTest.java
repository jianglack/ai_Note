package com.ainote.app.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigHeadersTest {

    @Test
    void configuresBaselineSecurityHeaders() throws Exception {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "ainote", "app", "config", "SecurityConfig.java"));

        assertThat(source)
                .contains("contentSecurityPolicy")
                .contains("httpStrictTransportSecurity")
                .contains("frameOptions")
                .contains("referrerPolicy");
    }

    @Test
    void configuresUnauthorizedAuthenticationEntryPoint() throws Exception {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "ainote", "app", "config", "SecurityConfig.java"));

        assertThat(source)
                .contains("authenticationEntryPoint")
                .contains("SC_UNAUTHORIZED");
    }

    @Test
    void enablesCookieCsrfAndUsesExactPublicAuthRoutes() throws Exception {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "ainote", "app", "config", "SecurityConfig.java"));

        assertThat(source)
                .contains("CookieCsrfTokenRepository.withHttpOnlyFalse()")
                .contains("CsrfTokenRequestAttributeHandler")
                .contains("addFilterBefore(jwtAuthenticationFilter(), CsrfFilter.class)")
                .contains("/api/auth/csrf")
                .contains("/api/auth/login")
                .doesNotContain("csrf.disable()")
                .doesNotContain("requestMatchers(\"/api/auth/**\")");
    }
}
