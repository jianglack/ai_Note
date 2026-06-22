package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlSafetyValidatorTest {

    private final UrlSafetyValidator validator = new UrlSafetyValidator();

    @Test
    void requirePublicHttpUri_acceptsPublicHttpsIp() {
        URI uri = validator.requirePublicHttpUri("https://93.184.216.34/path");

        assertThat(uri.getScheme()).isEqualTo("https");
        assertThat(uri.getHost()).isEqualTo("93.184.216.34");
    }

    @Test
    void requirePublicHttpUri_rejectsLoopbackHost() {
        assertThatThrownBy(() -> validator.requirePublicHttpUri("http://127.0.0.1:8080/admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URL is not allowed");
    }

    @Test
    void requirePublicHttpUri_rejectsLocalhostName() {
        assertThatThrownBy(() -> validator.requirePublicHttpUri("http://localhost:8080/admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URL is not allowed");
    }

    @Test
    void requirePublicHttpUri_rejectsCloudMetadataAddress() {
        assertThatThrownBy(() -> validator.requirePublicHttpUri("http://169.254.169.254/latest/meta-data"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URL is not allowed");
    }

    @Test
    void requirePublicHttpUri_rejectsPrivateRanges() {
        assertThatThrownBy(() -> validator.requirePublicHttpUri("http://10.0.0.10"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URL is not allowed");
        assertThatThrownBy(() -> validator.requirePublicHttpUri("http://172.16.0.10"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URL is not allowed");
        assertThatThrownBy(() -> validator.requirePublicHttpUri("http://192.168.1.10"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URL is not allowed");
    }

    @Test
    void requirePublicHttpUri_rejectsNonHttpSchemes() {
        assertThatThrownBy(() -> validator.requirePublicHttpUri("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only http and https URLs are allowed");
    }
}
