package com.neuroassist.pac.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads {@code empresaId} from the JWT claim and binds it to {@link TenantContextHolder} for the
 * request duration (PAC-021, P7 — SPEC-06 multi-tenancy).
 *
 * <p>Validates the optional {@code X-Empresa-Id} request header against the JWT claim. If both are
 * present and differ, the request is rejected with {@code 403 application/problem+json} (RFC 7807
 * /errors/tenant-mismatch) to prevent tenant-spoofing.
 *
 * <p>This filter is registered INSIDE Spring Security's {@code FilterChainProxy}, after {@code
 * BearerTokenAuthenticationFilter}, so the {@code SecurityContext} (JWT claims) is guaranteed to be
 * populated. It is NOT registered as a standalone Servlet filter — see {@code
 * SecurityConfig.tenantContextFilterRegistration}.
 *
 * <p>Also populates MDC keys {@code actor} (JWT {@code sub}) for structured logging; cleared in
 * {@code finally} to prevent ThreadLocal leak across pooled threads.
 *
 * <p>RULE-03: no {@code @Transactional}. RULE-05: no {@code api.*} imports.
 */
@Slf4j
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    static final String MDC_ACTOR = "actor";
    static final String MDC_EMPRESA_ID = "empresaId";

    private final TenantJwtValidator tenantJwtValidator;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        TenantJwtValidator.JwtClaims claims = tenantJwtValidator.extractClaims();

        if (claims.empresaId() != null) {
            // Canonico primeiro; o legado segue aceito por uma versao (E2E-021). Aceitar os dois
            // aqui e o que permite ao resto da frota falar UM nome so.
            String headerEmpresa = request.getHeader(TenantJwtValidator.HEADER_TENANT_ID);
            if (headerEmpresa == null || headerEmpresa.isBlank()) {
                headerEmpresa = request.getHeader(TenantJwtValidator.HEADER_EMPRESA_ID);
                if (headerEmpresa != null && !headerEmpresa.isBlank()) {
                    log.warn(
                            "Chamador usou o header legado X-Empresa-Id em vez de X-Tenant-Id"
                                    + " path={} — aceito por esta versao (E2E-021)",
                            request.getRequestURI());
                }
            }
            if (headerEmpresa == null) {
                log.warn(
                        "Missing X-Empresa-Id header for authenticated request path={}",
                        request.getRequestURI());
                writeMissingTenantResponse(request, response);
                return;
            }
            if (tenantJwtValidator.isTenantMismatch(claims.empresaId(), headerEmpresa)) {
                log.warn(
                        "Tenant mismatch: jwt.empresaId={} header.X-Empresa-Id={} path={}",
                        claims.empresaId(),
                        headerEmpresa,
                        request.getRequestURI());
                writeTenantMismatchResponse(request, response);
                return;
            }
            TenantContextHolder.set(claims.empresaId());
            // PAC-024 (P9) — populate MDC with empresaId for structured logging (SPEC-07).
            MDC.put(MDC_EMPRESA_ID, claims.empresaId());
        } else if (requiresTenantClaim()) {
            // O bloco acima era condicionado ao claim, e sem ele a requisicao seguia INTEIRA sem
            // tenant ligado — sem 400, sem 403. Ia morrer adiante com "relation ... does not
            // exist", porque o resolver cai em neuroassist_global, onde comando/paciente nao
            // existem. Nao vazava dado, mas a seguranca dependia dessa coincidencia, e era o que
            // tornava perigoso o ACS parar de emitir o alias "empresaId" que este servico lia
            // (E2E-021).
            log.warn(
                    "Requisicao autenticada sem claim tenant_id path={} — recusada",
                    request.getRequestURI());
            writeMissingTenantClaimResponse(request, response);
            return;
        }
        if (claims.actor() != null) {
            MDC.put(MDC_ACTOR, claims.actor());
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            MDC.remove(MDC_ACTOR);
            MDC.remove(MDC_EMPRESA_ID);
        }
    }

    private void writeMissingTenantResponse(
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        String correlationId = resolveCorrelationId(request);
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("X-Correlation-Id", correlationId);
        Map<String, Object> body =
                Map.of(
                        "type",
                        "/errors/missing-tenant",
                        "title",
                        "Missing tenant identifier",
                        "status",
                        HttpStatus.BAD_REQUEST.value(),
                        "detail",
                        "X-Tenant-Id header is required for authenticated requests"
                                + " (X-Empresa-Id still accepted for one version)",
                        "instance",
                        request.getRequestURI(),
                        "error",
                        Map.of(
                                "code",
                                "missing-tenant",
                                "category",
                                "validation",
                                "correlationId",
                                correlationId,
                                "timestamp",
                                OffsetDateTime.now().toString(),
                                "context",
                                Map.of()));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private void writeTenantMismatchResponse(
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        String correlationId = resolveCorrelationId(request);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("X-Correlation-Id", correlationId);
        Map<String, Object> body =
                Map.of(
                        "type",
                        "/errors/tenant-mismatch",
                        "title",
                        "Tenant identifier mismatch",
                        "status",
                        HttpStatus.FORBIDDEN.value(),
                        "detail",
                        "JWT empresaId does not match X-Empresa-Id header",
                        "instance",
                        request.getRequestURI(),
                        "error",
                        Map.of(
                                "code",
                                "tenant-mismatch",
                                "category",
                                "forbidden",
                                "correlationId",
                                correlationId,
                                "timestamp",
                                OffsetDateTime.now().toString(),
                                "context",
                                Map.of()));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        // MDC may already have correlationId from CorrelationIdInterceptor if it ran before this
        // filter; for filter-chain 403 it has not run yet, so fall back to request header or
        // generate.
        String fromMdc = MDC.get("correlationId");
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        String fromHeader = request.getHeader("X-Correlation-Id");
        return (fromHeader != null && !fromHeader.isBlank())
                ? fromHeader
                : UUID.randomUUID().toString();
    }

    /**
     * {@code token_type=service} pode legitimamente nao ter tenant (E2E-029 — o cliente que cria
     * empresas nao tem empresa a que se escopar). Qualquer outro token autenticado precisa do
     * claim.
     */
    private boolean requiresTenantClaim() {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (!(current instanceof JwtAuthenticationToken jwtAuth)) {
            return false; // sem JWT: quem decide o acesso e o SecurityConfig
        }
        return !"service".equals(jwtAuth.getToken().getClaimAsString("token_type"));
    }

    /** 403 — o token esta valido, mas nao diz a que empresa pertence. */
    private void writeMissingTenantClaimResponse(
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        String correlationId = resolveCorrelationId(request);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("X-Correlation-Id", correlationId);
        Map<String, Object> body =
                Map.of(
                        "type",
                        "/errors/tenant-claim-missing",
                        "title",
                        "Missing tenant claim",
                        "status",
                        HttpStatus.FORBIDDEN.value(),
                        "detail",
                        "Authenticated token carries no tenant_id claim",
                        "instance",
                        request.getRequestURI(),
                        "error",
                        Map.of(
                                "code",
                                "tenant-claim-missing",
                                "category",
                                "forbidden",
                                "correlationId",
                                correlationId,
                                "timestamp",
                                OffsetDateTime.now().toString(),
                                "context",
                                Map.of()));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
