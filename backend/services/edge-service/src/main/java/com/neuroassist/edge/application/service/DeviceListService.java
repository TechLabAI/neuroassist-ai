package com.neuroassist.edge.application.service;

import com.neuroassist.edge.application.port.in.DeviceListUseCase;
import com.neuroassist.edge.application.port.out.DispositivoEdgeRepositoryPort;
import com.neuroassist.edge.domain.model.DispositivoEdge;
import com.neuroassist.edge.domain.service.DispositivoEdgeDomainService;
import com.neuroassist.edge.infrastructure.security.TenantContextHolder;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DeviceListService — EDGE P9 paged device listing. tenantId obtained from caller (already resolved
 * by controller via TenantContextHolder.require()). livenessStatus derived at read time per
 * ADR-006. RULE-03: @Transactional only in application.service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceListService implements DeviceListUseCase {

    private final DispositivoEdgeRepositoryPort deviceRepo;
    private final DispositivoEdgeDomainService domainService;

    @Value("${neuroassist.edge.heartbeat.interval-seconds:30}")
    private long heartbeatIntervalSeconds;

    @Value("${neuroassist.edge.heartbeat.staleness-factor:3}")
    private long stalenessFactor;

    @Override
    public Page<DeviceListResult> listDevices(Pageable pageable) {
        String tenantId = TenantContextHolder.require();
        log.debug(
                "Listing devices tenant='{}' page={} size={}",
                tenantId,
                pageable.getPageNumber(),
                pageable.getPageSize());
        Instant serverNow = Instant.now();
        long threshold = heartbeatIntervalSeconds * stalenessFactor;
        return deviceRepo
                .findAllByTenant(tenantId, pageable)
                .map(device -> toResult(device, serverNow, threshold));
    }

    private DeviceListResult toResult(DispositivoEdge device, Instant serverNow, long threshold) {
        String livenessStatus = domainService.deriveLivenessStatus(device, serverNow, threshold);
        return new DeviceListResult(
                device.id(),
                device.label(),
                device.pairingState().name(),
                livenessStatus,
                device.ultimaSincronizacao(),
                device.createdAt());
    }
}
