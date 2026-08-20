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

    @Value("${spring.application.name:acs-service}")
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
                        .claim("papel", cuidador.papel().name())
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
