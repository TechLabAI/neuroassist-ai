package com.neuroassist.evnt.infrastructure.persistence.repository;

import com.neuroassist.evnt.infrastructure.persistence.entity.NotificationDispatchJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDispatchJpaRepository
        extends JpaRepository<NotificationDispatchJpaEntity, UUID> {

    List<NotificationDispatchJpaEntity> findByTargetCaregiverIdAndStatusNot(
            String targetCaregiverId, String status);

    List<NotificationDispatchJpaEntity> findByStatusInOrderByLastAttemptAtAscIdAsc(
            Collection<String> statuses, Pageable pageable);
}
