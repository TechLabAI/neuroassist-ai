package com.neuroassist.hist.api.controller;

import com.neuroassist.hist.api.dto.ActorRefDto;
import com.neuroassist.hist.api.dto.AuditRecordQueryResponse;
import com.neuroassist.hist.api.dto.SubjectRefDto;
import com.neuroassist.hist.application.port.in.AuditRecordQueryUseCase;
import com.neuroassist.hist.domain.model.AuditRecord;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller — single audit record fetch by HIST id (HIST-013, P4).
 *
 * <p>Canonical path: {@code GET /api/v1/hist/audit-records/{id}}.
 *
 * <p>Cross-tenant leakage prevention: the adapter always scopes the query to the caller's tenant. A
 * record in another tenant is treated as not found (never reveals cross-tenant existence).
 *
 * <p>Tenant extraction: P4 stub reads {@code tenant} JWT claim; replaced by {@code
 * TenantContextFilter} in P5 (HIST-015).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/hist/audit-records")
@RequiredArgsConstructor
public class AuditRecordQueryController {

    private final AuditRecordQueryUseCase auditRecordQueryUseCase;

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_hist:read')")
    public ResponseEntity<AuditRecordQueryResponse> findById(
            @PathVariable UUID id, Authentication authentication) {

        String tenantId = extractTenantId(authentication);
        log.info("GET /audit-records/{}: tenant={}", id, tenantId);

        return auditRecordQueryUseCase
                .findById(id, tenantId)
                .map(record -> ResponseEntity.ok(toResponse(record)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ---- helpers ----

    private String extractTenantId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String tenant = jwtAuth.getToken().getClaimAsString("tenant");
            if (tenant != null && !tenant.isBlank()) {
                return tenant;
            }
        }
        return "stub-tenant";
    }

    private AuditRecordQueryResponse toResponse(AuditRecord record) {
        return new AuditRecordQueryResponse(
                record.id(),
                record.factId(),
                record.factType(),
                new SubjectRefDto(record.subject().type().name(), record.subject().id()),
                new ActorRefDto(record.actor().caregiverId(), record.actor().displayNameSnapshot()),
                record.payload(),
                record.occurredAt(),
                record.recordedAt(),
                record.streamKey(),
                record.sequenceNo(),
                record.prevHash(),
                record.recordHash(),
                record.correctsRecordId());
    }
}
