package com.neuroassist.gest.infrastructure.persistence.repository;

import com.neuroassist.gest.infrastructure.persistence.entity.TipoGestoJpaEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoGestoJpaRepository extends JpaRepository<TipoGestoJpaEntity, String> {

    List<TipoGestoJpaEntity> findByDetectabilityState(String detectabilityState);

    Optional<TipoGestoJpaEntity> findByIdAndDetectabilityState(String id, String detectabilityState);
}
