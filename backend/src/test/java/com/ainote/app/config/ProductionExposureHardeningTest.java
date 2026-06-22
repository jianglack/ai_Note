package com.ainote.app.config;

import com.ainote.app.security.CustomUserDetailsService;
import com.ainote.app.security.JwtTokenProvider;
import com.ainote.app.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProductionExposureHardeningTest {

    @Test
    void corsConfigurationUsesConfiguredOriginsWithoutCredentials() {
        SecurityConfig securityConfig = new SecurityConfig(
                mock(CustomUserDetailsService.class),
                mock(JwtTokenProvider.class),
                mock(TokenService.class),
                "https://app.example.com, http://localhost:5173");

        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) securityConfig.corsConfigurationSource();
        CorsConfiguration cors = source.getCorsConfiguration(
                new MockHttpServletRequest("OPTIONS", "/api/notes"));

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOriginPatterns())
                .containsExactly("https://app.example.com", "http://localhost:5173");
        assertThat(cors.getAllowCredentials()).isFalse();
        assertThat(cors.getAllowedHeaders())
                .containsExactlyInAnyOrder("Authorization", "Content-Type", "Accept", "X-Trace-Id");
    }

    @Test
    void prodProfileDisablesSwaggerAndOnlyExposesHealthActuator() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("prod", new ClassPathResource("application-prod.yml"));

        assertThat(property(sources, "springdoc.api-docs.enabled")).isEqualTo(false);
        assertThat(property(sources, "springdoc.swagger-ui.enabled")).isEqualTo(false);
        assertThat(property(sources, "management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(property(sources, "management.endpoint.health.show-details")).isEqualTo("never");
    }

    @Test
    void mvcCorsConfigurationIsNotDuplicatedOutsideSecurityConfig() {
        assertThat(Files.exists(Path.of("src", "main", "java", "com", "ainote", "app", "WebConfig.java")))
                .isFalse();
    }

    private Object property(List<PropertySource<?>> sources, String name) {
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }
}
