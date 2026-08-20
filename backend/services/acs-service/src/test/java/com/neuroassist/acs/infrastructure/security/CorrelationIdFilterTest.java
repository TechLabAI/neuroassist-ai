package com.neuroassist.acs.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for CorrelationIdFilter (ACS-021). Verifies MDC population, response header
 * propagation, and MDC cleanup.
 */
class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
    }

    @Test
    void doFilter_withCorrelationIdHeader_propagatesHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "test-correlation-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(
                request,
                response,
                (req, res) -> {
                    // Verify MDC populated during filter execution
                    assertThat(MDC.get("correlationId")).isEqualTo("test-correlation-123");
                });

        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("test-correlation-123");
    }

    @Test
    void doFilter_withoutCorrelationIdHeader_generatesNewUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(
                request,
                response,
                (req, res) -> {
                    String correlationId = MDC.get("correlationId");
                    assertThat(correlationId).isNotNull().isNotBlank();
                });

        String responseCorrelationId = response.getHeader("X-Correlation-Id");
        assertThat(responseCorrelationId).isNotNull().isNotBlank();
    }

    @Test
    void doFilter_mdcClearedAfterFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "clear-test-456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(
                request,
                response,
                (req, res) -> {
                    // MDC is set during chain
                    assertThat(MDC.get("correlationId")).isNotNull();
                });

        // MDC must be cleared after filter completes
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void doFilter_tenantIdInitializedToNA() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(
                request,
                response,
                (req, res) -> {
                    assertThat(MDC.get("tenantId")).isEqualTo("N/A");
                });
    }

    @Test
    void doFilter_mdcClearedEvenOnException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilterInternal(
                    request,
                    response,
                    (req, res) -> {
                        throw new RuntimeException("chain failure");
                    });
        } catch (RuntimeException e) {
            // Expected
        }

        // MDC must be cleared even when filter chain throws
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("tenantId")).isNull();
    }
}
