package com.ainote.app.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusScrapeTokenFilterTest {

    @Test
    void correctDedicatedTokenAllowsPrometheusScrape() throws Exception {
        PrometheusScrapeTokenFilter filter = new PrometheusScrapeTokenFilter("monitor-secret");
        MockHttpServletRequest request = request("/actuator/prometheus");
        request.addHeader(PrometheusScrapeTokenFilter.HEADER_NAME, "monitor-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void missingOrWrongTokenIsRejectedWithoutCallingApplication() throws Exception {
        PrometheusScrapeTokenFilter filter = new PrometheusScrapeTokenFilter("monitor-secret");

        MockHttpServletResponse missing = invoke(filter, null);
        MockHttpServletResponse wrong = invoke(filter, "wrong-secret");

        assertThat(missing.getStatus()).isEqualTo(401);
        assertThat(wrong.getStatus()).isEqualTo(401);
        assertThat(missing.getErrorMessage()).isEqualTo("Monitoring authentication required");
    }

    @Test
    void blankServerTokenFailsClosed() throws Exception {
        PrometheusScrapeTokenFilter filter = new PrometheusScrapeTokenFilter("  ");

        MockHttpServletResponse response = invoke(filter, "anything");

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getErrorMessage()).isEqualTo("Monitoring endpoint unavailable");
    }

    @Test
    void filterDoesNotInterceptOtherActuatorOrApplicationEndpoints() throws Exception {
        PrometheusScrapeTokenFilter filter = new PrometheusScrapeTokenFilter("monitor-secret");
        MockHttpServletRequest request = request("/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    private MockHttpServletResponse invoke(PrometheusScrapeTokenFilter filter, String token) throws Exception {
        MockHttpServletRequest request = request("/actuator/prometheus");
        if (token != null) {
            request.addHeader(PrometheusScrapeTokenFilter.HEADER_NAME, token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("GET", path);
    }
}
