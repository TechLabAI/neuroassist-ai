package com.neuroassist.pac.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Validates that the JWT {@code empresaId} claim matches the {@code X-Empresa-Id} request header
 * (PAC-021, P7 — SPEC-06 multi-tenancy guard).
 *
 * <p>Extracts claims from the already-validated JWT in the {@link
 * org.springframework.security.core.context.SecurityContext} (populated by {@code
 * BearerTokenAuthenticationFilter} before {@link TenantContextFilter} runs).
 *
 * <p>Invariant: this component is only called from within the Spring Security filter chain, AFTER
 * JWT authentication has completed. Calling it before authentication returns {@link
 * JwtClaims#EMPTY}.
 */
@Component
public class TenantJwtValidator {

    /**
     * Claim canonico do tenant — o unico que o ACS emite (E2E-021).
     *
     * <p>Este validador lia {@code "empresaId"}, um alias que o ACS mantinha vivo <b>por causa
     * dele</b>. Bastava o ACS parar de emitir o alias para toda requisicao autenticada ao PAC
     * resolver o tenant como nulo — e, como o bloco inteiro do filtro e condicionado a esse valor,
     * a requisicao seguiria sem tenant ligado, sem 400 e sem 403.
     */
    public static final String JWT_CLAIM_TENANT_ID = "tenant_id";

    /** Header canonico, o mesmo dos outros seis servicos. */
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";

    /**
     * Header legado, aceito por uma versao (E2E-021, item 3 do escopo).
     *
     * <p>O PAC era o unico servico a exigir este nome, e a exigi-lo em vez do canonico: um
     * consumidor que mandasse {@code X-Tenant-Id} — como todos os outros seis fazem — levava {@code
     * 400 missing-tenant}. Foi assim que o gest-service esbarrou nisto na E2E-034.
     */
    public static final String HEADER_EMPRESA_ID = "X-Empresa-Id";

    /**
     * Extracts {@link JwtClaims} from the current {@code SecurityContext}.
     *
     * @return claims with {@code empresaId} and {@code actor} (JWT {@code sub}); {@link
     *     JwtClaims#EMPTY} if no JWT authentication is present.
     */
    public JwtClaims extractClaims() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            if (jwt != null) {
                Object empresaClaim = jwt.getClaim(JWT_CLAIM_TENANT_ID);
                Object subClaim = jwt.getClaim("sub");
                String empresaId = empresaClaim == null ? null : empresaClaim.toString();
                String actor = subClaim == null ? null : subClaim.toString();
                return new JwtClaims(empresaId, actor);
            }
        }
        return JwtClaims.EMPTY;
    }

    /**
     * Returns {@code true} when the header value is present, non-blank, and differs from the JWT
     * claim. A null JWT claim (token does not carry {@code empresaId}) is treated as permissive —
     * the filter falls back to schema-level isolation.
     *
     * @param jwtEmpresaId empresa identifier from JWT (may be {@code null})
     * @param headerEmpresaId value from {@code X-Empresa-Id} request header (may be {@code null})
     * @return {@code true} if a spoofing attempt is detected
     */
    public boolean isTenantMismatch(String jwtEmpresaId, String headerEmpresaId) {
        return jwtEmpresaId != null
                && headerEmpresaId != null
                && !headerEmpresaId.isBlank()
                && !headerEmpresaId.equals(jwtEmpresaId);
    }

    /** Value object for extracted JWT claims (empresaId + actor/sub). */
    public record JwtClaims(String empresaId, String actor) {
        /** Singleton empty claims — no JWT present in SecurityContext. */
        public static final JwtClaims EMPTY = new JwtClaims(null, null);
    }
}
