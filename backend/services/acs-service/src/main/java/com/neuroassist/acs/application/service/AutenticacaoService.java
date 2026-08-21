package com.neuroassist.acs.application.service;

import com.neuroassist.acs.api.dto.AutenticacaoRequest;
import com.neuroassist.acs.api.dto.AutenticacaoResponse;
import com.neuroassist.acs.api.dto.RefreshTokenRequest;
import com.neuroassist.acs.application.port.in.AutenticacaoUseCase;
import com.neuroassist.acs.application.port.out.CuidadorRepositoryPort;
import com.neuroassist.acs.application.port.out.HistAuditPort;
import com.neuroassist.acs.application.port.out.HistAuditPort.AuditFact;
import com.neuroassist.acs.application.port.out.TenantRepositoryPort;
import com.neuroassist.acs.domain.model.Cuidador;
import com.neuroassist.acs.domain.model.CuidadorStatus;
import com.neuroassist.acs.domain.model.TenantStatus;
import com.neuroassist.acs.shared.error.CaregiverNotFoundException;
import com.neuroassist.acs.shared.error.InvalidCredentialsException;
import com.neuroassist.acs.shared.error.InvalidTokenTypeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements credential-based authentication and token refresh (ACS-006, ACS-007, ACS-026).
 *
 * <p>ACS-006: authenticate (grant_type=password flow) ACS-007: refresh (token rotation via refresh
 * JWT) ACS-026: real JWT validation in refresh() + InvalidTokenTypeException (400) +
 * CaregiverNotFoundException (404) + Micrometer Observation spans
 *
 * <p>RULE-03: @Transactional only here (not in controller, repo, or adapter). SECURITY: never log
 * credentialHash or refresh token value.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AutenticacaoService implements AutenticacaoUseCase {

    private static final String METRIC_AUTH_ATTEMPTS = "acs.auth.attempts";
    private static final String OBS_AUTHENTICATE = "acs.auth.authenticate";
    private static final String OBS_REFRESH = "acs.auth.refresh";

    private final CuidadorRepositoryPort cuidadorRepository;
    private final TenantRepositoryPort tenantRepository;
    private final HistAuditPort histAuditPort;
    private final TokenIssuer tokenIssuer;
    private final PasswordEncoder passwordEncoder;
    private final JwtDecoder jwtDecoder;
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;

    /**
     * Authenticates a caregiver by login + senha. Grants access only if grant_type=password,
     * credentials valid, and tenant ACTIVE. Wrapped in a Micrometer Observation span (C12).
     *
     * @param request login credentials
     * @return AutenticacaoResponse with JWT access + refresh tokens
     * @throws InvalidCredentialsException if login not found or senha wrong (401,
     *     non-discriminating)
     */
    @Override
    public AutenticacaoResponse authenticate(AutenticacaoRequest request) {
        return Observation.createNotStarted(OBS_AUTHENTICATE, observationRegistry)
                .observe(() -> doAuthenticate(request));
    }

    /**
     * Refreshes an access token using a valid refresh token (token rotation). Validates signature,
     * expiry, and token_type claim.
     *
     * @param request the refresh token
     * @return new AutenticacaoResponse with rotated access + refresh tokens
     * @throws InvalidCredentialsException if refresh token is invalid or expired (401)
     * @throws InvalidTokenTypeException if token_type claim is not "refresh" (400)
     * @throws CaregiverNotFoundException if caregiver from sub claim is not found (404)
     */
    @Override
    public AutenticacaoResponse refresh(RefreshTokenRequest request) {
        Jwt refreshJwt;
        try {
            refreshJwt = jwtDecoder.decode(request.refreshToken());
        } catch (JwtException e) {
            log.debug("Invalid or expired refresh token: {}", e.getMessage());
            throw new InvalidCredentialsException();
        }

        // B7: verify token_type == "refresh" — wrong type → 400 InvalidTokenTypeException
        String tokenType = refreshJwt.getClaimAsString("token_type");
        if (!"refresh".equals(tokenType)) {
            log.warn(
                    "Token presented for refresh is not a refresh token (token_type={})",
                    tokenType);
            throw new InvalidTokenTypeException("refresh", tokenType);
        }

        String subStr = refreshJwt.getSubject();
        UUID cuidadorId = UUID.fromString(subStr);

        // B7+B8: load cuidador — not-found → 404 CaregiverNotFoundException (not 401)
        Cuidador cuidador =
                cuidadorRepository
                        .findById(cuidadorId)
                        .orElseThrow(
                                () -> {
                                    log.warn(
                                            "Caregiver not found during refresh: id={}",
                                            cuidadorId);
                                    return new CaregiverNotFoundException(cuidadorId);
                                });

        // B8: sliding window — issue new access + new refresh (stateless)
        AutenticacaoResponse response = tokenIssuer.issue(cuidador);
        log.debug("Token refreshed (sliding window) for sub={}", cuidadorId);
        return response;
    }

    // -----------------------------------------------------------------------
    // Private
    // -----------------------------------------------------------------------

    private AutenticacaoResponse doAuthenticate(AutenticacaoRequest request) {
        if (!"password".equals(request.grantType())) {
            log.warn("Unsupported grant_type: {}", request.grantType());
            meterRegistry.counter(METRIC_AUTH_ATTEMPTS, "status", "failure").increment();
            throw new InvalidCredentialsException();
        }

        Cuidador cuidador;
        try {
            cuidador =
                    cuidadorRepository
                            .findByLogin(request.login())
                            .orElseThrow(InvalidCredentialsException::new);
        } catch (InvalidCredentialsException e) {
            log.debug("Login not found: {} (returning 401 generic)", request.login());
            meterRegistry.counter(METRIC_AUTH_ATTEMPTS, "status", "failure").increment();
            throw e;
        }

        if (!passwordEncoder.matches(request.senha(), cuidador.credentialHash())) {
            log.debug("Invalid senha for login: {} (returning 401 generic)", request.login());
            meterRegistry.counter(METRIC_AUTH_ATTEMPTS, "status", "failure").increment();
            throw new InvalidCredentialsException();
        }

        if (cuidador.status() != CuidadorStatus.ACTIVE) {
            log.debug(
                    "Caregiver not active: {} status={} (returning 401 generic)",
                    request.login(),
                    cuidador.status());
            meterRegistry.counter(METRIC_AUTH_ATTEMPTS, "status", "failure").increment();
            throw new InvalidCredentialsException();
        }

        tenantRepository
                .findById(cuidador.tenantId())
                .filter(t -> t.status() == TenantStatus.ACTIVE)
                .orElseThrow(
                        () -> {
                            log.debug(
                                    "Tenant not active for login: {} (returning 401 generic)",
                                    request.login());
                            meterRegistry
                                    .counter(METRIC_AUTH_ATTEMPTS, "status", "failure")
                                    .increment();
                            return new InvalidCredentialsException();
                        });

        AutenticacaoResponse response = tokenIssuer.issue(cuidador);

        // Fire-and-forget audit — never blocks main transaction
        recordLoginAudit(cuidador, "LOGIN_SUCCESS");

        meterRegistry.counter(METRIC_AUTH_ATTEMPTS, "status", "success").increment();
        log.info(
                "Authentication successful: sub={} tenant={} papel={}",
                cuidador.id(),
                cuidador.tenantId(),
                cuidador.papel());

        return response;
    }

    private void recordLoginAudit(Cuidador cuidador, String fact) {
        try {
            histAuditPort.recordFact(
                    new AuditFact(
                            fact,
                            cuidador.tenantId(),
                            cuidador.id(),
                            MDC.get("correlationId"),
                            Instant.now(),
                            Map.of("login", cuidador.login(), "papel", cuidador.papel().name())));
        } catch (Exception e) {
            // Fire-and-forget: audit failure never blocks authentication
            log.warn("Failed to record audit fact LOGIN_SUCCESS: {}", e.getMessage());
        }
    }
}
