package com.neuroassist.hist.infrastructure.persistence.repository;

import com.neuroassist.hist.infrastructure.persistence.entity.AuditRecordJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditRecordJpaRepository
        extends JpaRepository<AuditRecordJpaEntity, UUID>,
                JpaSpecificationExecutor<AuditRecordJpaEntity> {

    List<AuditRecordJpaEntity> findByTenantId(String tenantId);

    /**
     * Looks up an audit record by its primary key AND tenant — enforces row-level tenant isolation
     * at the query boundary (in addition to schema-level search_path isolation).
     */
    Optional<AuditRecordJpaEntity> findByIdAndTenantId(UUID id, String tenantId);

    /**
     * Fetches all records for a given (streamKey, tenantId) pair ordered by sequenceNo ascending.
     * Used by ChainVerificationService to walk the hash chain from genesis.
     */
    List<AuditRecordJpaEntity> findByStreamKeyAndTenantIdOrderBySequenceNoAsc(
            String streamKey, String tenantId);

    /**
     * Looks up a record by its producer-minted factId within the given tenant. Used to resolve
     * correctsFactId references and to implement findByFactId port method.
     */
    Optional<AuditRecordJpaEntity> findByFactIdAndTenantId(UUID factId, String tenantId);
}
