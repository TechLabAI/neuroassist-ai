package com.neuroassist.edge.application.port.in;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Inbound port — paged listing of devices for a tenant (EDGE P9). tenantId is resolved internally
 * via TenantContextHolder (JWT, never from body/path). livenessStatus is derived at read time
 * (ADR-006).
 */
public interface DeviceListUseCase {

    /**
     * Returns a paginated view of all devices belonging to the current tenant (tenant resolved
     * internally via TenantContextHolder), with liveness status derived at read time. {@code GET
     * /api/v1/edge/devices}
     */
    Page<DeviceListResult> listDevices(Pageable pageable);

    /** Projected result for the device list — does NOT expose credentials or JPA entities. */
    record DeviceListResult(
            UUID deviceId,
            String label,
            String pairingState,
            String livenessStatus,
            Instant lastSeen,
            Instant registeredAt) {}
}
