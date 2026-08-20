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

    /** JWT claim that carries the empresa identifier for the authenticated principal. */
    public static final String JWT_CLAIM_EMPRESA_ID = "empresaId";

    /** Request header the caller is expected to send with the empresa identifier. */
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
                Object empresaClaim = jwt.getClaim(JWT_CLAIM_EMPRESA_ID);
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
