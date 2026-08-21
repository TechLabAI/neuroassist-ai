package com.neuroassist.acs.application.service;

import com.neuroassist.acs.api.dto.AutenticacaoResponse;
import com.neuroassist.acs.application.port.out.SigningKeyPort;
import com.neuroassist.acs.domain.model.Cuidador;
import com.neuroassist.acs.domain.model.SigningKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues RS256 JWT access tokens for authenticated caregivers.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Loads the active signing key via SigningKeyPort (kid for header)
 *   <li>Assembles claims: sub, tenant, papel, authorities, exp, iss, iat
 *   <li>Signs with NimbusJwtEncoder (RS256)
 *   <li>Returns AutenticacaoResponse (no credential fields)
 * </ul>
 *
 * <p>SECURITY: Never logs or propagates the RSA private key. RULE-03: @Transactional ONLY in
 * AutenticacaoService — not here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final SigningKeyPort signingKeyPort;
    private final RoleAuthorityPolicy roleAuthorityPolicy;

    @Value("${acs.jwt.expiry-seconds:900}")
    private long expirySeconds;

    @Value("${acs.jwt.refresh-expiry-seconds:604800}")
    private long refreshExpirySeconds;

    @Value("${acs.jwt.issuer:${spring.application.name:acs-service}}")
    private String issuer;

    /**
     * Issues an access token (JWT RS256) for the given caregiver.
     *
     * @param cuidador the authenticated caregiver
     * @return AutenticacaoResponse with accessToken, tokenType, expiresIn, claims
     */
    public AutenticacaoResponse issue(Cuidador cuidador) {
        SigningKey activeKey = signingKeyPort.getActiveSigningKey();
        List<String> authorities = roleAuthorityPolicy.toAuthorities(cuidador.papel());

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirySeconds);

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(activeKey.kid()).build();

        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(issuer)
                        .issuedAt(now)
                        .expiresAt(expiresAt)
                        .subject(cuidador.id().toString())
                        .claim("token_type", "access")
                        .claim("tenant_id", cuidador.tenantId().toString())
                        // Compatibility aliases for the same tenant value: gest-service and the
                        // hist-service controllers read "tenant", pac-service reads "empresaId".
                        // Canonical claim is tenant_id — see plan-e2e-integration.md §11 for the
                        // follow-up to converge the resource servers on it (E2E-003).
                        .claim("tenant", cuidador.tenantId().toString())
                        .claim("empresaId", cuidador.tenantId().toString())
                        .claim("papel", cuidador.papel().name())
                        .claim("nome", cuidador.nome())
                        .claim("authorities", authorities)
                        .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        // Issue a refresh token (separate JWT with longer TTL and type=refresh)
        String refreshToken = issueRefreshToken(cuidador, now);

        log.debug(
                "Issued JWT for sub={} tenant={} papel={} kid={}",
                cuidador.id(),
                cuidador.tenantId(),
                cuidador.papel(),
                activeKey.kid());

        return new AutenticacaoResponse(
                token,
                refreshToken,
                "Bearer",
                expirySeconds,
                cuidador.tenantId(),
                cuidador.id(),
                cuidador.papel().name(),
                authorities);
    }

    /**
     * Issues a service-identity access token (ACS-030).
     *
     * <p>Used for service-to-service calls that must not ride on a caregiver's token — e.g.
     * EDGE→GEST profile pointer, PAC→HIST audit facts. Differences from {@link #issue(Cuidador)}:
     *
     * <ul>
     *   <li>{@code sub} is the clientId, not a caregiver id
     *   <li>{@code token_type} is {@code service}, so a resource server can tell the two apart
     *   <li>no refresh token — the client re-authenticates with its secret when the token expires
     *   <li>{@code papel} is absent: a service has no caregiver role
     * </ul>
     *
     * <p>The tenant claims are populated exactly as for a caregiver token, because downstream
     * services route the {@code search_path} by them.
     *
     * @param clientId identifier of the calling service
     * @param tenantId tenant the token is scoped to (validated by the caller)
     * @param authorities authorities granted to this client
     */
    public AutenticacaoResponse issueServiceToken(
            String clientId, UUID tenantId, List<String> authorities) {
        SigningKey activeKey = signingKeyPort.getActiveSigningKey();

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirySeconds);

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(activeKey.kid()).build();

        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(issuer)
                        .issuedAt(now)
                        .expiresAt(expiresAt)
                        .subject(clientId)
                        .id(UUID.randomUUID().toString())
                        .claim("token_type", "service")
                        .claim("tenant_id", tenantId.toString())
                        .claim("tenant", tenantId.toString())
                        .claim("empresaId", tenantId.toString())
                        .claim("nome", clientId)
                        .claim("authorities", authorities)
                        .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        log.debug(
                "Issued service JWT for client={} tenant={} authorities={} kid={}",
                clientId,
                tenantId,
                authorities.size(),
                activeKey.kid());

        return new AutenticacaoResponse(
                token, null, "Bearer", expirySeconds, tenantId, null, null, authorities);
    }

    /**
     * Issues a refresh token (JWT, type=refresh, TTL=7 days). Used internally by TokenIssuer only —
     * not exposed directly to callers.
     */
    String issueRefreshToken(Cuidador cuidador, Instant now) {
        SigningKey activeKey = signingKeyPort.getActiveSigningKey();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(activeKey.kid()).build();

        JwtClaimsSet refreshClaims =
                JwtClaimsSet.builder()
                        .issuer(issuer)
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(refreshExpirySeconds))
                        .subject(cuidador.id().toString())
                        .id(UUID.randomUUID().toString())
                        .claim("token_type", "refresh")
                        .claim("tenant_id", cuidador.tenantId().toString())
                        .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, refreshClaims)).getTokenValue();
    }
}
