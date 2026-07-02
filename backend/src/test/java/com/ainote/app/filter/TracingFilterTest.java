package com.ainote.app.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TracingFilterTest {

    private TracingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TracingFilter();
        MDC.clear();
    }

    @Test
    void doFilter_withIncomingTraceId_setsResponseHeaderAndMdcDuringChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notes");
        request.addHeader(TracingFilter.TRACE_ID_HEADER, "trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcInsideChain = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                mdcInsideChain.set(MDC.get(TracingFilter.MDC_TRACE_ID));

        filter.doFilter(request, response, chain);

        assertThat(mdcInsideChain.get()).isEqualTo("trace-123");
        assertThat(response.getHeader(TracingFilter.TRACE_ID_HEADER)).isEqualTo("trace-123");
        assertThat(MDC.get(TracingFilter.MDC_TRACE_ID)).isNull();
    }

    @Test
    void doFilter_withoutIncomingTraceId_generatesTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcInsideChain = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                mdcInsideChain.set(MDC.get(TracingFilter.MDC_TRACE_ID));

        filter.doFilter(request, response, chain);

        assertThat(mdcInsideChain.get()).hasSize(16);
        assertThat(response.getHeader(TracingFilter.TRACE_ID_HEADER)).isEqualTo(mdcInsideChain.get());
        assertThat(MDC.get(TracingFilter.MDC_TRACE_ID)).isNull();
    }

    @Test
    void doFilter_whenChainThrows_stillClearsMdc() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notes");
        request.addHeader(TracingFilter.TRACE_ID_HEADER, "trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            throw new ServletException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(ServletException.class)
                .hasMessageContaining("boom");
        assertThat(MDC.get(TracingFilter.MDC_TRACE_ID)).isNull();
    }
}
