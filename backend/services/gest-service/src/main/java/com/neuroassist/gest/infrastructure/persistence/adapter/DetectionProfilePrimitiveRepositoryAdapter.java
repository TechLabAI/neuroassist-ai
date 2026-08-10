package com.neuroassist.gest.infrastructure.persistence.adapter;

import com.neuroassist.gest.application.port.out.DetectionProfilePrimitiveRepositoryPort;
import com.neuroassist.gest.domain.model.DetectionProfilePrimitive;
import com.neuroassist.gest.infrastructure.persistence.entity.DetectionProfilePrimitiveJpaEntity;
import com.neuroassist.gest.infrastructure.persistence.repository.DetectionProfilePrimitiveJpaRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DetectionProfilePrimitiveRepositoryAdapter
        implements DetectionProfilePrimitiveRepositoryPort {

    private final DetectionProfilePrimitiveJpaRepository repository;

    @Override
    public DetectionProfilePrimitive save(DetectionProfilePrimitive entity) {
        DetectionProfilePrimitiveJpaEntity jpa = DetectionProfilePrimitiveJpaEntity.builder()
                .profileId(entity.profileId())
                .tipoGestoId(entity.tipoGestoId())
                .nome(entity.nome())
                .engineFeatureRef(entity.engineFeatureRef())
                .build();
        return toDomain(repository.save(jpa));
    }

    @Override
    public Optional<DetectionProfilePrimitive> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<DetectionProfilePrimitive> findByTenantId(String tenantId) {
        return Collections.emptyList();
    }

    @Override
    public List<DetectionProfilePrimitive> findByProfileId(UUID profileId) {
        return repository.findByProfileId(profileId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    private DetectionProfilePrimitive toDomain(DetectionProfilePrimitiveJpaEntity e) {
        return new DetectionProfilePrimitive(
                e.getId(),
                e.getProfileId(),
                e.getTipoGestoId(),
                e.getNome(),
                e.getEngineFeatureRef());
    }
}
