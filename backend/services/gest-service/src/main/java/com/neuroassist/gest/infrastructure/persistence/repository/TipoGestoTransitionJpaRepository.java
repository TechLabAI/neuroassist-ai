package com.neuroassist.gest.infrastructure.persistence.repository;

import com.neuroassist.gest.infrastructure.persistence.entity.TipoGestoTransitionJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoGestoTransitionJpaRepository
        extends JpaRepository<TipoGestoTransitionJpaEntity, UUID> {
    // JpaRepository.save() is sufficient for PoC
}
