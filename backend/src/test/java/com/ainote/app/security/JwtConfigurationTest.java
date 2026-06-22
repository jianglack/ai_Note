package com.ainote.app.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JwtConfigurationTest {

    @Test
    void applicationYml_doesNotShipWithDefaultJwtSecret() throws Exception {
        String yaml = Files.readString(Path.of("src", "main", "resources", "application.yml"));

        assertThat(yaml)
                .contains("secret: ${JWT_SECRET:}")
                .doesNotContain("ainote-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm-security");
    }
}
