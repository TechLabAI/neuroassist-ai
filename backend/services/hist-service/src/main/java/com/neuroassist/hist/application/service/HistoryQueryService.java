package com.neuroassist.hist.application.service;

import com.neuroassist.hist.application.port.in.HistoryQueryUseCase;
import com.neuroassist.hist.application.port.out.ActorDirectoryPort;
import com.neuroassist.hist.application.port.out.AuditRecordRepositoryPort;
import com.neuroassist.hist.application.port.out.AuditRecordRepositoryPort.AuditRecordFilter;
import com.neuroassist.hist.application.port.out.AuditRecordRepositoryPort.FilteredPage;
import com.neuroassist.hist.domain.model.ActorDirectoryUnavailableException;
import com.neuroassist.hist.domain.model.AuditRecord;
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
 *       resolve the allowed patient UUID list; results are filtered in-memory. If ACS is
 *       unavailable, {@code ActorDirectoryUnavailableException} propagates (fail-closed, HTTP 503).
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
        AuditRecordFilter filter =
                new AuditRecordFilter(
                        query.subjectType(),
                        query.subjectId(),
                        query.factType(),
                        query.actorCaregiverId(),
                        query.from(),
                        query.to(),
                        query.order(),
                        query.page(),
                        query.size());

        FilteredPage page = auditRecordRepositoryPort.findFiltered(filter, query.tenantId());
        List<AuditRecord> items = page.items();

        if (query.actorCaregiverId() != null) {
            // hist:read scope — apply ACS caregiver→patient scope restriction (fail-closed).
            // ActorDirectoryUnavailableException propagates to the caller → HTTP 503.
            List<UUID> allowedPatients;
            try {
                allowedPatients =
                        actorDirectoryPort.resolvePatientScope(
                                query.tenantId(), query.actorCaregiverId());
                histMetrics.incrementAcsScopeResolutionTotal(query.tenantId(), "ok");
            } catch (ActorDirectoryUnavailableException ex) {
                histMetrics.incrementAcsScopeResolutionTotal(query.tenantId(), "unavailable");
                throw ex;
            }
            items = items.stream().filter(r -> allowedPatients.contains(r.subject().id())).toList();
            log.info(
                    "History query [hist:read]: tenant={} caregiver={} allowedPatients={} page={}"
                            + " total={}",
                    query.tenantId(),
                    query.actorCaregiverId(),
                    allowedPatients.size(),
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

        return new HistoryPage(items, query.page(), query.size(), page.total());
    }
}
