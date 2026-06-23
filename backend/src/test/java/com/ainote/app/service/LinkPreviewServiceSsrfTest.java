package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void fetchPreviewConnectsToValidatedResolvedAddress() throws Exception {
        UrlSafetyValidator validator = mock(UrlSafetyValidator.class);
        URI original = URI.create("http://example.com/page");
        when(validator.requirePublicHttpUri("http://example.com/page")).thenReturn(original);
        when(validator.requirePublicHttpUri("http://127.0.0.1/"))
                .thenThrow(new IllegalArgumentException("URL is not allowed"));
        when(validator.requirePublicHttpUri("http://93.184.216.34/"))
                .thenReturn(URI.create("http://93.184.216.34/"));

        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        InetAddress publicAddress = InetAddress.getByName("93.184.216.34");
        AtomicReference<InetAddress> fetchedAddress = new AtomicReference<>();
        LinkPreviewService service = new LinkPreviewService(
                validator,
                host -> new InetAddress[]{loopback, publicAddress},
                (uri, fixedAddress) -> {
                    fetchedAddress.set(fixedAddress);
                    return "<html><head><title>Fixed IP</title></head></html>";
                }
        );

        Map<String, String> preview = service.fetchPreview("http://example.com/page");

        assertThat(fetchedAddress.get()).isEqualTo(publicAddress);
        assertThat(preview.get("title")).isEqualTo("Fixed IP");
        verify(validator).requirePublicHttpUri("http://example.com/page");
        verify(validator).requirePublicHttpUri("http://127.0.0.1/");
        verify(validator).requirePublicHttpUri("http://93.184.216.34/");
    }
}
