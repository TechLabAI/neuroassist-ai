package com.neuroassist.hist.api.controller;

import com.neuroassist.hist.api.dto.ActorRefDto;
import com.neuroassist.hist.api.dto.HistoryQueryResponse;
import com.neuroassist.hist.api.dto.RecordAuditFactResponse;
import com.neuroassist.hist.api.dto.SubjectRefDto;
import com.neuroassist.hist.application.port.in.HistoryQueryUseCase;
import com.neuroassist.hist.domain.model.AuditRecord;
import com.neuroassist.hist.domain.model.IncompleteSubjectFilterException;
import com.neuroassist.hist.domain.model.InvalidTimeWindowException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller — paginated history timeline query (HIST-013, P4).
 *
 * <p>Canonical path: {@code GET /api/v1/hist/history}.
 *
 * <p>Scope rules:
 *
 * <ul>
 *   <li>{@code hist:read:all} — returns all tenant records; no ACS call.
 *   <li>{@code hist:read} — caregiver-scoped; ACS resolves allowed patient list; results are
 *       filtered. ACS unavailable → 503 via {@code ActorDirectoryUnavailableException}.
 * </ul>
 *
 * <p>Tenant extraction: P4 stub reads {@code tenant} JWT claim with fallback to "stub-tenant";
 * replaced by {@code TenantContextFilter} in P5 (HIST-015).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/hist/history")
@RequiredArgsConstructor
public class HistoryQueryController {

    private final HistoryQueryUseCase historyQueryUseCase;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_hist:read', 'SCOPE_hist:read:all')")
    public ResponseEntity<HistoryQueryResponse> queryTimeline(
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) String factType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        // Validate: subjectId requires subjectType — both or neither (400 if only one supplied)
        if (subjectId != null && (subjectType == null || subjectType.isBlank())) {
            throw new IncompleteSubjectFilterException();
        }

        // Validate: from must be before to when both are provided
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidTimeWindowException(from, to);
        }

        boolean isReadAll =
                authentication.getAuthorities().stream()
                        .anyMatch(a -> "SCOPE_hist:read:all".equals(a.getAuthority()));

        String tenantId = extractTenantId(authentication);
        // hist:read:all → no caregiver restriction; hist:read → extract caregiverId from JWT sub
        UUID caregiverId = isReadAll ? null : extractCaregiverId(authentication);

        log.info(
                "GET /history: tenant={} readAll={} subjectType={} subjectId={} page={} size={}",
                tenantId,
                isReadAll,
                subjectType,
                subjectId,
                page,
                size);

        HistoryQueryUseCase.Query query =
                new HistoryQueryUseCase.Query(
                        subjectType,
                        subjectId,
                        factType,
                        caregiverId,
                        from,
                        to,
                        order,
                        page,
                        size,
                        tenantId);

        HistoryQueryUseCase.HistoryPage result = historyQueryUseCase.queryTimeline(query);

        List<RecordAuditFactResponse> dtoItems =
                result.items().stream().map(this::toResponse).toList();

        return ResponseEntity.ok(
                new HistoryQueryResponse(dtoItems, result.page(), result.size(), result.total()));
    }

    // ---- helpers ----

    private String extractTenantId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String tenant = jwt.getClaimAsString("tenant");
            if (tenant != null && !tenant.isBlank()) {
                return tenant;
            }
        }
        // P4 stub — TenantContextFilter (P5, HIST-015) replaces this
        return "stub-tenant";
    }

    private UUID extractCaregiverId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String sub = jwtAuth.getToken().getSubject();
            if (sub != null && !sub.isBlank()) {
                try {
                    return UUID.fromString(sub);
                } catch (IllegalArgumentException ignored) {
                    // subject is not a UUID (e.g. Keycloak username) — no restriction
                }
            }
        }
        return null;
    }

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
