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
                authenticateClient(request.clientId(), request.clientSecret());

        boolean tenantActive =
                tenantRepository
                        .findById(request.tenantId())
                        .filter(t -> t.status() == TenantStatus.ACTIVE)
                        .isPresent();
        if (!tenantActive) {
            throw reject("tenant not found or not active: {}", request.tenantId().toString());
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

    /**
     * Issues a <b>tenant-less</b> service token for an internal call whose target resolves the
     * tenant on its own (E2E-005). Not reachable over HTTP: {@code ServiceTokenRequest} requires a
     * tenant, so this variant exists only for in-process callers inside acs-service.
     *
     * <p>Today the single caller is {@code EdgeInternalTokenProvider}, which needs an {@code
     * edge.internal} identity to ask edge-service whether a device credential is valid — a question
     * asked <i>before</i> the device's tenant is known. Credential checks are exactly the same as
     * {@link #issueToken}; only the tenant validation is skipped, because there is no tenant to
     * validate rather than because any tenant would do.
     *
     * @param clientId the acs-service client id registered in {@code acs.service-clients}
     * @param clientSecret its plaintext secret, matched against the configured BCrypt hash
     * @throws InvalidCredentialsException for every rejection reason
     */
    public AutenticacaoResponse issueTenantlessToken(String clientId, String clientSecret) {
        ServiceClientProperties.ServiceClient client = authenticateClient(clientId, clientSecret);

        meterRegistry
                .counter(METRIC_SERVICE_TOKEN, "status", "success", "client", clientId)
                .increment();
        log.debug(
                "Tenant-less service token issued client={} authorities={}",
                clientId,
                client.getAuthorities());

        return tokenIssuer.issueServiceToken(clientId, null, client.getAuthorities());
    }

    /**
     * Validates clientId + secret against {@code acs.service-clients}, returning the matching
     * client. Every failure raises the same opaque 401; the real reason is logged at debug.
     */
    private ServiceClientProperties.ServiceClient authenticateClient(
            String clientId, String clientSecret) {
        ServiceClientProperties.ServiceClient client =
                serviceClientProperties.getServiceClients().get(clientId);

        if (client == null) {
            throw reject("unknown client: {}", clientId);
        }
        if (!client.isEnabled()) {
            throw reject("client disabled: {}", clientId);
        }
        // Fail closed: a blank hash means the secret env var was not provided
        if (client.getSecretHash() == null || client.getSecretHash().isBlank()) {
            throw reject("client has no secret configured: {}", clientId);
        }
        if (!passwordEncoder.matches(clientSecret, client.getSecretHash())) {
            throw reject("invalid secret for client: {}", clientId);
        }
        if (client.getAuthorities().isEmpty()) {
            throw reject("client grants no authorities: {}", clientId);
        }
        return client;
    }

    /**
     * Logs the real reason at debug level and builds the same opaque 401 for every rejection.
     * Returns the exception (rather than throwing it) so call sites read {@code throw reject(...)}
     * and the compiler sees the branch terminating.
     */
    private InvalidCredentialsException reject(String reason, String detail) {
        log.debug("Service token rejected — " + reason, detail);
        meterRegistry.counter(METRIC_SERVICE_TOKEN, "status", "failure").increment();
        return new InvalidCredentialsException();
    }
}
