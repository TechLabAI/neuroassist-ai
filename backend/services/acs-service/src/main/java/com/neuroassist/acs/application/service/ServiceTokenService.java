package com.neuroassist.acs.application.service;

import com.neuroassist.acs.api.dto.AutenticacaoResponse;
import com.neuroassist.acs.api.dto.ServiceTokenRequest;
import com.neuroassist.acs.application.port.out.TenantRepositoryPort;
import com.neuroassist.acs.domain.model.TenantStatus;
import com.neuroassist.acs.infrastructure.config.ServiceClientProperties;
import com.neuroassist.acs.shared.error.InvalidCredentialsException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ACS-030 — issues service-identity tokens for service-to-service calls.
 *
 * <p>Replaces the previous practice of propagating the caller's caregiver JWT downstream, which
 * forced caregivers to hold authorities they should never have ({@code hist:write} being the clear
 * case) and made the heartbeat unreachable, since a device token cannot carry {@code
 * gest.detection-profile.read} (E2E plan §11.2, C-18/C-19).
 *
 * <p>Every failure mode returns the same generic {@link InvalidCredentialsException} (401): unknown
 * client, wrong secret, disabled client and inactive tenant are indistinguishable to the caller, so
 * the endpoint cannot be used to enumerate clients or tenants.
 *
 * <p>RULE-03: {@code @Transactional} lives here, not in the controller or the adapter.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceTokenService {

    private static final String METRIC_SERVICE_TOKEN = "acs_service_token_requests_total";

    private final ServiceClientProperties serviceClientProperties;
    private final TenantRepositoryPort tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final MeterRegistry meterRegistry;

    /**
     * Authenticates a service client and issues a tenant-scoped service token.
     *
     * @param request clientId + secret + target tenant
     * @return access token with the client's configured authorities
     * @throws InvalidCredentialsException (401) for every rejection reason
     */
    @Transactional(readOnly = true)
    public AutenticacaoResponse issueToken(ServiceTokenRequest request) {
        ServiceClientProperties.ServiceClient client =
                serviceClientProperties.getServiceClients().get(request.clientId());

        if (client == null) {
            return reject("unknown client: {}", request.clientId());
        }
        if (!client.isEnabled()) {
            return reject("client disabled: {}", request.clientId());
        }
        // Fail closed: a blank hash means the secret env var was not provided
        if (client.getSecretHash() == null || client.getSecretHash().isBlank()) {
            return reject("client has no secret configured: {}", request.clientId());
        }
        if (!passwordEncoder.matches(request.clientSecret(), client.getSecretHash())) {
            return reject("invalid secret for client: {}", request.clientId());
        }
        if (client.getAuthorities().isEmpty()) {
            return reject("client grants no authorities: {}", request.clientId());
        }

        boolean tenantActive =
                tenantRepository
                        .findById(request.tenantId())
                        .filter(t -> t.status() == TenantStatus.ACTIVE)
                        .isPresent();
        if (!tenantActive) {
            return reject("tenant not found or not active: {}", request.tenantId().toString());
        }

        meterRegistry
                .counter(METRIC_SERVICE_TOKEN, "status", "success", "client", request.clientId())
                .increment();
        log.info(
                "Service token issued client={} tenant={} authorities={}",
                request.clientId(),
                request.tenantId(),
                client.getAuthorities());

        return tokenIssuer.issueServiceToken(
                request.clientId(), request.tenantId(), client.getAuthorities());
    }

    /** Logs the real reason at debug level and returns the same opaque 401 to the caller. */
    private AutenticacaoResponse reject(String reason, String detail) {
        log.debug("Service token rejected — " + reason, detail);
        meterRegistry.counter(METRIC_SERVICE_TOKEN, "status", "failure").increment();
        throw new InvalidCredentialsException();
    }
}
