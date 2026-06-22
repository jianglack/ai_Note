package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LinkPreviewServiceSsrfTest {

    @Test
    void fetchPreview_validatesUrlBeforeFetching() {
        UrlSafetyValidator validator = mock(UrlSafetyValidator.class);
        when(validator.requirePublicHttpUri("http://127.0.0.1:8080/admin"))
                .thenThrow(new IllegalArgumentException("URL is not allowed"));

        LinkPreviewService service = new LinkPreviewService(validator);

        Map<String, String> preview = service.fetchPreview("http://127.0.0.1:8080/admin");

        verify(validator).requirePublicHttpUri("http://127.0.0.1:8080/admin");
        assertThat(preview.get("title")).isEqualTo("http://127.0.0.1:8080/admin");
        assertThat(preview.get("description")).isEmpty();
    }
}
