package com.neuroassist.hist.application.service;

import com.neuroassist.hist.application.port.in.HistoryQueryUseCase;
import com.neuroassist.hist.application.port.out.ActorDirectoryPort;
import com.neuroassist.hist.application.port.out.AuditRecordRepositoryPort;
import com.neuroassist.hist.application.port.out.AuditRecordRepositoryPort.AuditRecordFilter;
import com.neuroassist.hist.application.port.out.AuditRecordRepositoryPort.FilteredPage;
import com.neuroassist.hist.domain.model.ActorDirectoryUnavailableException;
import com.neuroassist.hist.infrastructure.config.HistMetrics;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service — paginated timeline query over the tenant ledger (HIST-008, P2; P4 adds ACS
 * scope filtering via {@link ActorDirectoryPort}).
 *
 * <p>Scope rules (P4, HIST-013):
 *
 * <ul>
 *   <li>{@code hist:read:all} — {@code actorCaregiverId} is null; no ACS call; all tenant records
 *       returned.
 *   <li>{@code hist:read} (caregiver-scoped) — {@code actorCaregiverId} is set; ACS is called to
 *       resolve the allowed patient UUID list, which becomes a {@code scope_paciente_id IN (...)}
 *       predicate <b>in SQL</b> — so pagination and the total count both respect it (C-16). If ACS
 *       is unavailable, {@code ActorDirectoryUnavailableException} propagates (fail-closed, 503).
 * </ul>
 *
 * <p>TenantContextHolder (P5): schema-pinning enforcement is added in P5. For P4 the tenantId comes
 * from the JWT {@code tenant} claim forwarded by the controller.
 *
 * <p>{@code @Transactional(readOnly = true)} only here (RULE-03).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryQueryService implements HistoryQueryUseCase {

    private final AuditRecordRepositoryPort auditRecordRepositoryPort;
    private final ActorDirectoryPort actorDirectoryPort;
    private final HistMetrics histMetrics;

    @Override
    @Transactional(readOnly = true)
    public HistoryPage queryTimeline(Query query) {
        // Resolve the scope BEFORE querying, so it can go into the WHERE clause. Fail-closed:
        // ActorDirectoryUnavailableException propagates to the caller -> HTTP 503 (DIR-03).
        List<UUID> allowedPacientes = null;
        if (query.actorCaregiverId() != null) {
            try {
                allowedPacientes =
                        actorDirectoryPort.resolvePatientScope(
                                query.tenantId(), query.actorCaregiverId());
                histMetrics.incrementAcsScopeResolutionTotal(query.tenantId(), "ok");
            } catch (ActorDirectoryUnavailableException ex) {
                histMetrics.incrementAcsScopeResolutionTotal(query.tenantId(), "unavailable");
                throw ex;
            }
        }

        AuditRecordFilter filter =
                new AuditRecordFilter(
                        query.subjectType(),
                        query.subjectId(),
                        query.factType(),
                        allowedPacientes,
                        query.from(),
                        query.to(),
                        query.order(),
                        query.page(),
                        query.size());

        FilteredPage page = auditRecordRepositoryPort.findFiltered(filter, query.tenantId());

        if (allowedPacientes != null) {
            log.info(
                    "History query [hist:read]: tenant={} caregiver={} allowedPatients={} page={}"
                            + " total={}",
                    query.tenantId(),
                    query.actorCaregiverId(),
                    allowedPacientes.size(),
                    query.page(),
                    page.total());
        } else {
            log.info(
                    "History query [hist:read:all]: tenant={} subjectType={} subjectId={} page={}"
                            + " total={}",
                    query.tenantId(),
                    query.subjectType(),
                    query.subjectId(),
                    query.page(),
                    page.total());
        }

        // total is now the scoped count — no in-memory filtering, so page/size/total agree.
        return new HistoryPage(page.items(), query.page(), query.size(), page.total());
    }
}
