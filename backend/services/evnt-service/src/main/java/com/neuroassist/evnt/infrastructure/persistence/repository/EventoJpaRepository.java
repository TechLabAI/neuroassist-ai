package com.neuroassist.evnt.infrastructure.persistence.repository;

import com.neuroassist.evnt.infrastructure.persistence.entity.EventoJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoJpaRepository extends JpaRepository<EventoJpaEntity, UUID> {

    List<EventoJpaEntity> findByFeedback(String feedback);
}
