package com.neuroassist.edge.application.service;

import com.neuroassist.edge.application.port.in.DeviceRegistrationUseCase;
import com.neuroassist.edge.application.port.out.DispositivoCredentialRepositoryPort;
import com.neuroassist.edge.application.port.out.DispositivoEdgeRepositoryPort;
import com.neuroassist.edge.application.port.out.DispositivoEdgeTransitionRepositoryPort;
import com.neuroassist.edge.application.port.out.DispositivoEnrollmentRepositoryPort;
import com.neuroassist.edge.domain.event.DispositivoRegistrado;
import com.neuroassist.edge.domain.model.DispositivoCredential;
import com.neuroassist.edge.domain.model.DispositivoEdge;
import com.neuroassist.edge.domain.model.DispositivoEdgeTransition;
import com.neuroassist.edge.domain.model.DispositivoEnrollment;
import com.neuroassist.edge.domain.model.PairingState;
import com.neuroassist.edge.domain.service.DispositivoEdgeDomainService;
import com.neuroassist.edge.infrastructure.metrics.EdgeMetrics;
import com.neuroassist.edge.infrastructure.security.TenantContextHolder;
import com.neuroassist.edge.shared.error.CodedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DeviceRegistrationService — EDGE-005 (register), EDGE-006 (enroll), EDGE-015 (retire, P4).
 * RULE-03: @Transactional only in application.service. tenantId obtained via
 * TenantContextHolder.require() — never from caller parameters.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DeviceRegistrationService implements DeviceRegistrationUseCase {

    private final DispositivoEdgeRepositoryPort deviceRepo;
    private final DispositivoEnrollmentRepositoryPort enrollmentRepo;
    private final DispositivoCredentialRepositoryPort credentialRepo;
    private final DispositivoEdgeTransitionRepositoryPort transitionRepo;
    private final DispositivoEdgeDomainService domainService;
    private final ApplicationEventPublisher eventPublisher;
    private final EdgeMetrics edgeMetrics;

    @Value("${neuroassist.edge.enrollment.ttl-seconds:900}")
    private long enrollmentTtlSeconds;

    @Value("${neuroassist.edge.heartbeat.interval-seconds:30}")
    private long heartbeatIntervalSeconds;

    @Value(
            "${neuroassist.acs.token-endpoint:http://localhost:8180/realms/neuroassist-ai/protocol/openid-connect/token}")
    private String acsTokenEndpoint;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ---- EDGE-005: Register ----

    @Override
    public RegisterResult register(String label, String pacienteHint, String actor) {
        String tenantId = TenantContextHolder.require();
        log.info(
                "Registering new device label='{}' tenant='{}' actor='{}'", label, tenantId, actor);

        UUID deviceId = UUID.randomUUID();
        DispositivoEdge device = DispositivoEdge.newDevice(deviceId, tenantId, label, null, actor);
        device = deviceRepo.save(device);

        // Mint enrollment token — plaintext returned once; only hash stored
        String plainToken = "et_" + randomHex(32);
        String tokenHash = sha256Hex(plainToken);
        Instant expiresAt = Instant.now().plusSeconds(enrollmentTtlSeconds);

        DispositivoEnrollment enrollment =
                DispositivoEnrollment.mint(
                        UUID.randomUUID(), tenantId, deviceId, tokenHash, expiresAt);
        enrollmentRepo.save(enrollment);

        log.info("Device registered deviceId='{}' enrollmentExpiresAt='{}'", deviceId, expiresAt);

        // EDGE-016 (P6): publish domain event pos-commit (AFTER_COMMIT listener will not fire on
        // rollback)
        eventPublisher.publishEvent(DispositivoRegistrado.of(deviceId, tenantId, device.label()));

        return new RegisterResult(
                device.id(),
                device.pairingState(),
                device.label(),
                plainToken,
                expiresAt,
                device.createdAt());
    }

    // ---- EDGE-006: Enroll ----

    @Override
    public EnrollResult enroll(String enrollmentToken, String agentVersion) {
        // No JWT on this endpoint — no TenantContextHolder.require() here.
        // Token hash is the only proof of identity; tenant is derived from the stored enrollment.
        log.info("Enrollment attempt agentVersion='{}'", agentVersion);

        String tokenHash = sha256Hex(enrollmentToken);
        DispositivoEnrollment enrollment =
                enrollmentRepo
                        .findByTokenHash(tokenHash)
                        .orElseThrow(
                                () -> {
                                    log.warn("Enrollment failed: token not found (hash lookup)");
                                    // Do NOT reveal whether unknown vs expired vs consumed
                                    // (api-contract §2.2)
                                    return new CodedException(
                                            "edge.enrollment-invalid",
                                            "Enrollment token is invalid, expired, or already used.");
                                });

        if (!enrollment.isValid()) {
            log.warn(
                    "Enrollment failed: token expired={} consumed={} deviceId='{}'",
                    enrollment.isExpired(),
                    enrollment.isConsumed(),
                    enrollment.dispositivoEdgeId());
            throw new CodedException(
                    "edge.enrollment-invalid",
                    "Enrollment token is invalid, expired, or already used.");
        }

        DispositivoEdge device =
                deviceRepo
                        .findById(enrollment.dispositivoEdgeId())
                        .orElseThrow(
                                () ->
                                        new CodedException(
                                                "edge.device-not-found",
                                                "Device not found for enrollment token."));

        if (domainService.isEnrolled(device)) {
            log.warn("Enrollment failed: device already enrolled deviceId='{}'", device.id());
            throw new CodedException("edge.already-enrolled", "Device is already enrolled.");
        }

        // Issue credential — clientSecret returned once; only hash stored
        String clientId = "edge-" + device.id();
        String plainSecret = "cs_" + randomHex(32);
        String secretHash = sha256Hex(plainSecret);

        DispositivoCredential credential =
                DispositivoCredential.issue(
                        UUID.randomUUID(), device.tenantId(), device.id(), clientId, secretHash);
        credentialRepo.save(credential);

        // Record initial agentVersion — api-contract §2.2 ("not yet a heartbeat")
        // Does NOT stamp ultimaSincronizacao (liveness remains offline until first heartbeat)
        DispositivoEdge updated = device.withInitialAgentVersion(agentVersion, "system");
        deviceRepo.save(updated);

        // Mark enrollment consumed atomically with credential issuance
        enrollmentRepo.save(enrollment.withConsumed());

        log.info("Device enrolled deviceId='{}' clientId='{}'", device.id(), clientId);
        return new EnrollResult(
                device.id(),
                device.tenantId(),
                clientId,
                plainSecret,
                acsTokenEndpoint,
                "edge.device",
                (int) heartbeatIntervalSeconds);
    }

    // ---- EDGE-015: Retire (P4 — terminal operation) ----

    @Override
    public void retire(UUID deviceId, String actor) {
        String tenantId = TenantContextHolder.require();
        log.info("Retiring device deviceId='{}' tenant='{}' actor='{}'", deviceId, tenantId, actor);

        DispositivoEdge device =
                deviceRepo
                        .findByIdAndTenantId(deviceId, tenantId)
                        .orElseThrow(
                                () ->
                                        new CodedException(
                                                "edge.device-not-found",
                                                "Device not found: " + deviceId));

        if (domainService.isRetired(device)) {
            throw new CodedException(
                    "edge.device-retired", "Device " + deviceId + " is already retired.");
        }

        // Revoke all active credentials — soft-revoke, never delete (ADR-005 invariant)
        List<DispositivoCredential> credentials = credentialRepo.findByDispositivoEdgeId(deviceId);
        for (DispositivoCredential cred : credentials) {
            if (cred.isActive()) {
                credentialRepo.save(cred.withRevoked());
                log.info(
                        "Credential revoked clientId='{}' deviceId='{}'",
                        cred.clientId(),
                        deviceId);
            }
        }

        // Append audited transition (pairing FSM: current → retired)
        String fromState = device.pairingState().name();
        DispositivoEdgeTransition retireTransition =
                DispositivoEdgeTransition.pairing(
                        deviceId,
                        tenantId,
                        fromState,
                        PairingState.retired.name(),
                        actor,
                        "device-retired");
        transitionRepo.save(retireTransition);

        // Mark device as retired — soft-retire, never hard-delete (ADR-005)
        DispositivoEdge retired = device.withRetired(actor);
        deviceRepo.save(retired);

        log.info("Device retired deviceId='{}' actor='{}'", deviceId, actor);

        // EDGE-017 (P7): pairing transition metric for retire terminal operation
        edgeMetrics.recordPairingTransition(fromState, PairingState.retired.name());
    }

    // ---- Helpers (private) ----

    static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        SECURE_RANDOM.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }
}
