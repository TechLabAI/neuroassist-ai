package com.neuroassist.evnt.infrastructure.persistence.repository;

import com.neuroassist.evnt.infrastructure.persistence.entity.ReportIdempotencyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportIdempotencyJpaRepository
        extends JpaRepository<ReportIdempotencyJpaEntity, String> {
    // existsById(String) e findById(String) herdados de JpaRepository
}
