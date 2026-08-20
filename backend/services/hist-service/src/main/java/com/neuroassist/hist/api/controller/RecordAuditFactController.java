package com.neuroassist.hist.api.controller;

import com.neuroassist.hist.api.dto.ActorRefDto;
import com.neuroassist.hist.api.dto.RecordAuditFactRequest;
import com.neuroassist.hist.api.dto.RecordAuditFactResponse;
import com.neuroassist.hist.api.dto.SubjectRefDto;
import com.neuroassist.hist.application.port.in.AuditRecordQueryUseCase;
import com.neuroassist.hist.application.port.in.RecordAuditFactUseCase;
import com.neuroassist.hist.domain.model.AuditRecord;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * REST controller — audit fact ingestion surface.
 *
 * <p>Surfaces:
 *
 * <ul>
 *   <li>{@code POST /api/v1/hist/audit-facts} — append (or replay) an audit fact (HIST-005)
 *   <li>{@code GET /api/v1/hist/audit-facts/{id}} — fetch a single record (HIST-013)
 * </ul>
 *
 * <p>Security: {@code hist:write} for POST; {@code hist:read} for GET. Tenant is derived from the
 * JWT {@code tenant} claim (P1 stub: uses header fallback).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/hist/audit-facts")
@RequiredArgsConstructor
public class RecordAuditFactController {

    private final RecordAuditFactUseCase recordAuditFactUseCase;
    private final AuditRecordQueryUseCase auditRecordQueryUseCase;

    /**
     * Append (or replay) an audit fact. Returns {@code 201 Created} for a new record, {@code 200
     * OK} for an idempotent replay.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_hist:write')")
    public ResponseEntity<RecordAuditFactResponse> append(
            @RequestBody @Valid RecordAuditFactRequest request, UriComponentsBuilder uriBuilder) {

        // P1 stub: tenantId extracted from header; P5 will replace with JWT tenant claim
        // TenantContextHolder.require() is intentionally NOT called here (P5 — HIST-015)
        String tenantId = "stub-tenant";

        RecordAuditFactUseCase.Command command =
                new RecordAuditFactUseCase.Command(
                        request.factId(),
                        request.factType(),
                        new RecordAuditFactUseCase.SubjectCommand(
                                request.subject().type(), request.subject().id()),
                        new RecordAuditFactUseCase.ActorCommand(
                                request.actor().caregiverId(),
                                request.actor().displayNameSnapshot()),
                        request.payload(),
                        request.occurredAt(),
                        request.correctsFactId(),
                        tenantId);

        RecordAuditFactUseCase.AppendResult result = recordAuditFactUseCase.append(command);
        RecordAuditFactResponse body = toResponse(result.record());

        if (result.created()) {
            return ResponseEntity.created(
                            uriBuilder
                                    .path("/api/v1/hist/audit-facts/{id}")
                                    .buildAndExpand(result.record().id())
                                    .toUri())
                    .body(body);
        }
        return ResponseEntity.ok(body);
    }

    /**
     * Fetch a single audit record by its HIST id. Returns {@code 200 OK} if found in the caller's
     * tenant; {@code 404 Not Found} otherwise.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_hist:read')")
    public ResponseEntity<RecordAuditFactResponse> findById(@PathVariable UUID id) {
        // P1 stub: tenantId from header; P5 replaces with JWT tenant claim
        String tenantId = "stub-tenant";

        return auditRecordQueryUseCase
                .findById(id, tenantId)
                .map(record -> ResponseEntity.ok(toResponse(record)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ---- mapping ----

    private RecordAuditFactResponse toResponse(AuditRecord record) {
        return new RecordAuditFactResponse(
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
