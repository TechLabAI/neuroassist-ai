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
                        // tenant_id e o UNICO nome do tenant no token (E2E-021). Ate aqui o mesmo
                        // valor saia tambem como "tenant" e "empresaId", porque quatro servicos
                        // liam so um alias — gest e hist liam "tenant", pac lia "empresaId". Cada
                        // alias era um lugar a mais onde um servico novo podia ler o nome errado e
                        // resolver o tenant como nulo.
                        .claim("tenant_id", cuidador.tenantId().toString())
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
     * <p>{@code tenantId} may be {@code null} for the narrow case of a call whose target resolves
     * the tenant itself and therefore never reads the claim — today only ACS→EDGE {@code
     * /internal/devices/verify-credential} (see {@code EdgeInternalTokenProvider}). A null tenant
     * simply omits the three tenant claims; it never means "any tenant".
     *
     * @param clientId identifier of the calling service
     * @param tenantId tenant the token is scoped to (validated by the caller), or null
     * @param authorities authorities granted to this client
     */
    public AutenticacaoResponse issueServiceToken(
            String clientId, UUID tenantId, List<String> authorities) {
        SigningKey activeKey = signingKeyPort.getActiveSigningKey();

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirySeconds);

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(activeKey.kid()).build();

        JwtClaimsSet.Builder builder =
                JwtClaimsSet.builder()
                        .issuer(issuer)
                        .issuedAt(now)
                        .expiresAt(expiresAt)
                        .subject(clientId)
                        .id(UUID.randomUUID().toString())
                        .claim("token_type", "service")
                        .claim("nome", clientId)
                        .claim("authorities", authorities);
        // Token de servico pode ser legitimamente SEM tenant (E2E-029: o cliente que cria
        // empresas nao tem empresa a que se escopar). Por isso os filtros nao exigem o claim de
        // tenant quando token_type=service — e exigem em todos os outros tipos.
        if (tenantId != null) {
            builder.claim("tenant_id", tenantId.toString());
        }
        JwtClaimsSet claims = builder.build();

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
     * Issues a device-identity access token (E2E-005, {@code standards/service-identity.md} §7).
     *
     * <p>Emitted by {@code POST /oauth2/token/device} <b>after</b> edge-service confirmed the
     * {@code clientId}/{@code clientSecret} pair. Differences from {@link #issueServiceToken}:
     *
     * <ul>
     *   <li>{@code token_type} is {@code device} — the heartbeat is authenticated by a specific
     *       device, not by a service, and a resource server must be able to tell them apart
     *   <li>{@code sub} is the device's {@code clientId} ({@code edge-<deviceId>}), which is what
     *       preserves <i>which</i> device is beating
     *   <li>{@code tenantId} is mandatory and comes from edge-service's answer, never from the
     *       request body — the caller is unauthenticated at that point
     *   <li>no refresh token — the device re-authenticates with its secret
     * </ul>
     *
     * @param clientId device client id, used as {@code sub}
     * @param tenantId tenant resolved by edge-service
     * @param authorities authorities granted to the device (today {@code edge.device})
     */
    public AutenticacaoResponse issueDeviceToken(
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
                        .claim("token_type", "device")
                        .claim("tenant_id", tenantId.toString())
                        .claim("nome", clientId)
                        .claim("authorities", authorities)
                        .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        log.debug(
                "Issued device JWT for client={} tenant={} authorities={} kid={}",
                clientId,
                tenantId,
                authorities,
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
