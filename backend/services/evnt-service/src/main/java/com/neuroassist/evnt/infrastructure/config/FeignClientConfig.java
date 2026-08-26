package com.neuroassist.evnt.infrastructure.config;

import com.neuroassist.evnt.infrastructure.security.ServiceTokenProvider;
import com.neuroassist.evnt.infrastructure.security.TenantContextHolder;
import feign.Logger;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign interceptors — propagate correlationId, tenantId and JWT bearer token to downstream
 * services (RULE-06: only in infrastructure.clients/config).
 */
@Configuration
public class FeignClientConfig {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String EMPRESA_HEADER = "X-Empresa-Id";
    private static final String AUTH_HEADER = "Authorization";
    private static final String MDC_CORRELATION_KEY = "correlationId";

    @Bean
    public RequestInterceptor feignCorrelationIdInterceptor() {
        return (RequestTemplate template) -> {
            String correlationId = MDC.get(MDC_CORRELATION_KEY);
            if (correlationId != null && !correlationId.isBlank()) {
                template.header(CORRELATION_HEADER, correlationId);
            }
        };
    }

    @Bean
    public RequestInterceptor feignTenantIdInterceptor() {
        return (RequestTemplate template) -> {
            String tenantId = TenantContextHolder.getTenantIdOrNull();
            if (tenantId != null && !tenantId.isBlank()) {
                template.header(TENANT_HEADER, tenantId);
            }
        };
    }

    /**
     * Propagates {@code X-Empresa-Id} alongside {@code X-Tenant-Id}.
     *
     * <p>Not redundant: pac-service's {@code TenantContextFilter} <b>rejects</b> any authenticated
     * request that does not carry {@code X-Empresa-Id} — 400 {@code missing-tenant}, before the
     * controller runs. Sending only {@code X-Tenant-Id} made every command lookup fail, and the
     * failure surfaced inside evnt-service as a generic 500 on the report. The two headers carry
     * the same value; the duplication is the same compatibility shim as the tripled tenant claim
     * (C-3), and it goes away with it.
     */
    @Bean
    public RequestInterceptor feignEmpresaIdInterceptor() {
        return (RequestTemplate template) -> {
            String tenantId = TenantContextHolder.getTenantIdOrNull();
            if (tenantId != null && !tenantId.isBlank()) {
                template.header(EMPRESA_HEADER, tenantId);
            }
        };
    }

    /**
     * Authenticates every outbound call as evnt-service, using a tenant-scoped service-identity
     * token from acs-service ({@code POST /oauth2/token/service}).
     *
     * <p>Unlike pac-service — which propagates the caller's JWT to ACS/GEST/EDGE and only swaps in
     * a service token for hist-service — <b>all four</b> of this service's outbound calls act on
     * evnt-service's own behalf, so the swap is global here:
     *
     * <ul>
     *   <li>GEST eligibility and PAC command lookup are triggered by a device report, not by a
     *       person; the device token that authenticated the report carries {@code edge.device} and
     *       nothing else, so propagating it would be a guaranteed 403 — the exact shape of C-19,
     *       where an authorization error was laundered into "service degraded".
     *   <li>ACS directory routing and HIST audit writes are the service asking on its own account.
     *       Granting a caregiver {@code hist:write} so that EVNT could write a fact is the debt
     *       service-identity.md exists to remove.
     * </ul>
     *
     * <p>The caregiver never disappears from the record: they travel as an explicit field in the
     * payload (the {@code actor} of an audit fact, the {@code caregiverId} of a dispatch).
     *
     * <p>Fail-closed: {@code ServiceTokenProvider} throws when ACS cannot mint a token, and there
     * is deliberately no fallback to the caller's token. Each adapter decides what that failure
     * means for its own call.
     */
    @Bean
    public RequestInterceptor feignServiceIdentityInterceptor(
            ServiceTokenProvider serviceTokenProvider) {
        return (RequestTemplate template) -> {
            String token = serviceTokenProvider.currentTenantToken();
            // header() appends — remove first so a request never carries two Authorization values.
            template.removeHeader(AUTH_HEADER);
            template.header(AUTH_HEADER, "Bearer " + token);
        };
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
