package com.neuroassist.gest.application.service;

import com.neuroassist.gest.api.dto.DetectionProfileRequest;
import com.neuroassist.gest.api.dto.DetectionProfileResponse;
import com.neuroassist.gest.application.port.in.DetectionProfileUseCase;
import com.neuroassist.gest.application.port.out.DetectionProfilePrimitiveRepositoryPort;
import com.neuroassist.gest.application.port.out.DetectionProfileRepositoryPort;
import com.neuroassist.gest.application.port.out.TenantContextPort;
import com.neuroassist.gest.application.port.out.TipoGestoRepositoryPort;
import com.neuroassist.gest.domain.event.DetectionProfilePublished;
import com.neuroassist.gest.domain.model.DetectabilityState;
import com.neuroassist.gest.domain.model.DetectionProfile;
import com.neuroassist.gest.domain.model.DetectionProfilePrimitive;
import com.neuroassist.gest.domain.model.TipoGesto;
import com.neuroassist.gest.domain.service.DetectionProfileDomainService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for DetectionProfile operations (GEST-009). Publishes versioned detection
 * profiles for the edge and exposes read operations. Tenant isolation is implicit via schema
 * routing set by TenantContextFilter.
 *
 * <p>Publish invariant (ADR-006): at least one primitive must be in SUPPORTED state. The published
 * profile is immutable after creation (only {@code active} flag can be flipped by a subsequent
 * publish).
 *
 * <p>NOTE — RULE-07: TenantContextHolder.require() is deferred to P5.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DetectionProfileService implements DetectionProfileUseCase {

    private final DetectionProfileRepositoryPort detectionProfileRepositoryPort;
    private final DetectionProfilePrimitiveRepositoryPort detectionProfilePrimitiveRepositoryPort;
    private final TipoGestoRepositoryPort tipoGestoRepositoryPort;
    private final DetectionProfileDomainService detectionProfileDomainService;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantContextPort tenantContextPort;

    /**
     * Publishes a new versioned DetectionProfile. Steps (GEST-009):
     *
     * <ol>
     *   <li>Load all SUPPORTED primitives — throw if none (invariant: empty-profile).
     *   <li>Increment monotonic version.
     *   <li>Deactivate any currently-active profile.
     *   <li>Persist the new profile and its primitive snapshot.
     *   <li>Emit {@link DetectionProfilePublished} in-process domain event.
     * </ol>
     */
    @Override
    public DetectionProfileResponse publish(DetectionProfileRequest request) {
        // 1. Load SUPPORTED primitives (tenant implicit via port schema routing)
        List<TipoGesto> supported =
                tipoGestoRepositoryPort.findAllByTenant().stream()
                        .filter(tg -> tg.detectabilityState() == DetectabilityState.SUPPORTED)
                        .toList();
        if (supported.isEmpty()) {
            throw new IllegalStateException("no supported primitives");
        }

        // 2. Next monotonic version + deactivate current (atomic from service perspective)
        long version = detectionProfileRepositoryPort.nextVersion();
        detectionProfileRepositoryPort.deactivateCurrent();

        // 3. Create and persist new DetectionProfile
        DetectionProfile profile =
                DetectionProfile.publish(
                        version,
                        "v1.0.0",
                        request.classificationParams(),
                        supported,
                        tenantContextPort.getTenantIdOrNull(),
                        null);
        DetectionProfile saved = detectionProfileRepositoryPort.save(profile);

        // 4. Snapshot SUPPORTED primitives via domain service and persist
        List<DetectionProfilePrimitive> primitives =
                detectionProfileDomainService.createSnapshot(supported, saved.id());
        detectionProfilePrimitiveRepositoryPort.saveAll(primitives);

        // 5. Publish in-process domain event (AFTER_COMMIT listener in P6)
        eventPublisher.publishEvent(
                new DetectionProfilePublished(saved.id(), saved.version(), Instant.now()));

        log.info("DetectionProfile published: id={} version={}", saved.id(), saved.version());
        return toResponse(saved);
    }

    /** Returns the currently active profile for the tenant, if any. */
    @Override
    @Transactional(readOnly = true)
    public Optional<DetectionProfileResponse> findActive() {
        return detectionProfileRepositoryPort.findActive().map(this::toResponse);
    }

    /**
     * Returns the profile for a specific version. Port query deferred to P3 (requires
     * DetectionProfileRepositoryPort.findByVersion() added when persistence is connected).
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<DetectionProfileResponse> findByVersion(long version) {
        // P3: add DetectionProfileRepositoryPort.findByVersion(long) and implement here
        return Optional.empty();
    }

    /**
     * Returns the active profile for a device identified by its paired deviceId. Device-lookup via
     * EdgePairingClient deferred to P4.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<DetectionProfileResponse> findForDevice(UUID deviceId) {
        // P4: requires EdgePairingClient.getTenant(deviceId) + findActive() for that tenant
        return Optional.empty();
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private DetectionProfileResponse toResponse(DetectionProfile profile) {
        return new DetectionProfileResponse(
                profile.id(),
                profile.version(),
                profile.active(),
                profile.contractVersion(),
                profile.classificationParams(),
                profile.publishedAt(),
                "\"" + profile.version() + "\"");
    }
}
