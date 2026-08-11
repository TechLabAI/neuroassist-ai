package com.neuroassist.gest.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.gest.application.port.out.DetectionProfileRepositoryPort;
import com.neuroassist.gest.domain.model.DetectionProfile;
import com.neuroassist.gest.infrastructure.persistence.entity.DetectionProfileJpaEntity;
import com.neuroassist.gest.infrastructure.persistence.repository.DetectionProfileJpaRepository;
import com.neuroassist.gest.infrastructure.security.TenantContextHolder;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * JPA adapter for {@link DetectionProfileRepositoryPort}. Handles the JSONB ↔ {@code
 * Map<String,Object>} conversion for {@code classificationParams} using Jackson. Schema routing is
 * transparent via {@link
 * com.neuroassist.gest.infrastructure.security.MultiTenantConnectionProvider}. Implemented in P3
 * (GEST-012).
 */
@Component
@RequiredArgsConstructor
public class DetectionProfileRepositoryAdapter implements DetectionProfileRepositoryPort {

    private final DetectionProfileJpaRepository repository;
    private final ObjectMapper objectMapper;

    private static final TypeReference<Map<String, Object>> PARAMS_TYPE = new TypeReference<>() {};

    @Override
    public DetectionProfile save(DetectionProfile profile) {
        DetectionProfileJpaEntity entity = toEntity(profile);
        DetectionProfileJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<DetectionProfile> findActive() {
        return repository.findByActiveTrue().map(this::toDomain);
    }

    @Override
    public long nextVersion() {
        return repository.nextVersion();
    }

    @Override
    public void deactivateCurrent() {
        repository.deactivateCurrent();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Mapping helpers — domain ↔ JPA entity
    // ────────────────────────────────────────────────────────────────────────

    private DetectionProfileJpaEntity toEntity(DetectionProfile profile) {
        String paramsJson;
        try {
            paramsJson = objectMapper.writeValueAsString(profile.classificationParams());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Failed to serialize classificationParams for profile " + profile.id(), e);
        }
        return DetectionProfileJpaEntity.builder()
                .id(profile.id())
                .version(profile.version())
                .active(profile.active())
                .contractVersion(profile.contractVersion())
                .classificationParams(paramsJson)
                .publishedAt(profile.publishedAt())
                .publishedBy(profile.publishedBy())
                .build();
    }

    private DetectionProfile toDomain(DetectionProfileJpaEntity entity) {
        Map<String, Object> params;
        try {
            params = objectMapper.readValue(entity.getClassificationParams(), PARAMS_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to deserialize classificationParams for profile " + entity.getId(), e);
        }
        return new DetectionProfile(
                entity.getId(),
                entity.getVersion(),
                Boolean.TRUE.equals(entity.getActive()),
                entity.getContractVersion(),
                params,
                entity.getPublishedAt(),
                entity.getPublishedBy(),
                TenantContextHolder.getTenantIdOrNull());
    }
}
