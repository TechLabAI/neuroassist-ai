package com.neuroassist.conv.infrastructure.persistence.repository;

import com.neuroassist.conv.infrastructure.persistence.entity.ProcessedEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for the idempotency tracking table (D-070). Consumers call {@link
 * #existsById(Object)} before processing.
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {}
