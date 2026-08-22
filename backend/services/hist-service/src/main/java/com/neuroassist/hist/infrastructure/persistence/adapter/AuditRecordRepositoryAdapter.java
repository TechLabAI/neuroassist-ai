package com.neuroassist.hist.infrastructure.persistence.adapter;

import com.neuroassist.hist.application.port.out.AuditRecordRepositoryPort;
import com.neuroassist.hist.domain.model.AuditRecord;
import com.neuroassist.hist.infrastructure.persistence.entity.AuditRecordJpaEntity;
import com.neuroassist.hist.infrastructure.persistence.repository.AuditRecordJpaRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter — JPA implementation of {@link AuditRecordRepositoryPort}.
 *
 * <p>All read-path methods implemented using Spring Data JPA derived queries and JPA Specifications
 * for the dynamic filter case. The write path lives in {@link AuditLedgerAdapter}.
 */
@Component
@RequiredArgsConstructor
public class AuditRecordRepositoryAdapter implements AuditRecordRepositoryPort {

    private final AuditRecordJpaRepository repository;
    private final AuditLedgerAdapter auditLedgerAdapter;

    @Override
    public AuditRecord save(AuditRecord entity) {
        // Not used by the primary ingestion path (AuditLedgerAdapter handles inserts).
        throw new UnsupportedOperationException(
                "AuditRecordRepositoryAdapter.save — use AuditLedgerPort.insert for ingestion");
    }

    @Override
    public Optional<AuditRecord> findById(UUID id, String tenantId) {
        // Queries with both id AND tenantId to enforce row-level isolation in addition to
        // schema-level search_path routing (MultiTenantConnectionProvider sets search_path).
        return repository.findByIdAndTenantId(id, tenantId).map(auditLedgerAdapter::toDomain);
    }

    @Override
    public List<AuditRecord> findByStreamKeyOrderedBySequenceAsc(
            String streamKey, String tenantId) {
        return repository
                .findByStreamKeyAndTenantIdOrderBySequenceNoAsc(streamKey, tenantId)
                .stream()
                .map(auditLedgerAdapter::toDomain)
                .toList();
    }

    @Override
    public FilteredPage findFiltered(AuditRecordFilter filter, String tenantId) {
        Sort sort =
                "asc".equalsIgnoreCase(filter.order())
                        ? Sort.by(Sort.Direction.ASC, "recordedAt")
                        : Sort.by(Sort.Direction.DESC, "recordedAt");
        Pageable pageable = PageRequest.of(filter.page(), filter.size(), sort);

        Specification<AuditRecordJpaEntity> spec = buildSpecification(filter, tenantId);
        Page<AuditRecordJpaEntity> page = repository.findAll(spec, pageable);

        List<AuditRecord> items =
                page.getContent().stream().map(auditLedgerAdapter::toDomain).toList();
        return new FilteredPage(items, page.getTotalElements());
    }

    @Override
    public Optional<AuditRecord> findByFactId(UUID factId, String tenantId) {
        return repository
                .findByFactIdAndTenantId(factId, tenantId)
                .map(auditLedgerAdapter::toDomain);
    }

    // ---- helpers ----

    /**
     * Builds a JPA Specification for dynamic filtering. All filter fields are optional; only {@code
     * tenantId} is always applied (row-level isolation complement to schema search_path).
     */
    private static Specification<AuditRecordJpaEntity> buildSpecification(
            AuditRecordFilter filter, String tenantId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (filter.subjectType() != null) {
                predicates.add(cb.equal(root.get("subjectType"), filter.subjectType()));
            }
            if (filter.subjectId() != null) {
                predicates.add(cb.equal(root.get("subjectId"), filter.subjectId()));
            }
            if (filter.factType() != null) {
                predicates.add(cb.equal(root.get("factType"), filter.factType()));
            }
            if (filter.allowedPacientes() != null) {
                // Scope in SQL, so that LIMIT/OFFSET and the total count both see it. Previously
                // this was an in-memory filter applied *after* pagination, which leaked the
                // unscoped total and could return an empty page 0 to a caregiver who had facts on
                // page 3 (C-16).
                if (filter.allowedPacientes().isEmpty()) {
                    // Responsible for no patient — the visible set is empty, not unrestricted.
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("scopePacienteId").in(filter.allowedPacientes()));
                }
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("recordedAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("recordedAt"), filter.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
