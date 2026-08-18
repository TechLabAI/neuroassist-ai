package com.neuroassist.edge.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.edge.infrastructure.security.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for CorrelationIdFilter (EDGE-013, P5).
 *
 * <p>Verifies: - X-Correlation-Id echoed from request to response - UUID generated when header
 * absent - MDC populated during filter execution - MDC cleared in finally (no ThreadLocal leak)
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    @DisplayName("X-Correlation-Id presente na request e ecoado na response")
    void correlationId_presenteNaRequest_ecoado() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(CorrelationIdFilter.HEADER, "my-corr-id-001");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = (request, response) -> {};

        filter.doFilter(req, res, chain);

        assertThat(res.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("my-corr-id-001");
    }

    @Test
    @DisplayName("X-Correlation-Id ausente na request — UUID gerado na response")
    void correlationId_ausente_uuidGerado() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = (request, response) -> {};

        filter.doFilter(req, res, chain);

        String generatedId = res.getHeader(CorrelationIdFilter.HEADER);
        assertThat(generatedId).isNotBlank();
        // UUID format: 8-4-4-4-12 hex digits
        assertThat(generatedId).matches("[0-9a-f\\-]{36}");
    }

    @Test
    @DisplayName("MDC correlationId populado durante execucao do chain")
    void mdc_populadoDuranteChain() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(CorrelationIdFilter.HEADER, "mdc-test-id");
        MockHttpServletResponse res = new MockHttpServletResponse();

        String[] capturedMdc = new String[1];
        FilterChain chain =
                (request, response) -> {
                    capturedMdc[0] = MDC.get(CorrelationIdFilter.MDC_KEY);
                };

        filter.doFilter(req, res, chain);

        assertThat(capturedMdc[0]).isEqualTo("mdc-test-id");
    }

    @Test
    @DisplayName("MDC correlationId limpo apos filtro — sem leak entre requests")
    void mdc_limpoAposFiltro() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(CorrelationIdFilter.HEADER, "cleanup-test");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = (request, response) -> {};

        filter.doFilter(req, res, chain);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("MDC limpo no finally mesmo com excecao no chain")
    void mdc_limpoNoFinally_comExcecao() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(CorrelationIdFilter.HEADER, "exception-test");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain =
                (request, response) -> {
                    throw new RuntimeException("downstream failure");
                };

        try {
            filter.doFilter(req, res, chain);
        } catch (RuntimeException ignored) {
            // expected
        }

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
