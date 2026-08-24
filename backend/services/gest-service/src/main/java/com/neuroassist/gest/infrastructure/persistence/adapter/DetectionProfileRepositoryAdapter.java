package com.neuroassist.gest.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.gest.application.port.out.DetectionProfileRepositoryPort;
import com.neuroassist.gest.domain.model.DetectionProfile;
import com.neuroassist.gest.domain.model.DetectionThresholdTable;
import com.neuroassist.gest.infrastructure.persistence.entity.DetectionProfileJpaEntity;
import com.neuroassist.gest.infrastructure.persistence.repository.DetectionProfileJpaRepository;
import com.neuroassist.gest.infrastructure.security.TenantContextHolder;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * JPA adapter for {@link DetectionProfileRepositoryPort}. Converte a coluna JSONB
 * {@code classification_params} de e para {@link DetectionThresholdTable} via Jackson. Schema routing is
 * transparent via {@link
 * com.neuroassist.gest.infrastructure.security.MultiTenantConnectionProvider}. Implemented in P3
 * (GEST-012).
 */
@Component
@RequiredArgsConstructor
public class DetectionProfileRepositoryAdapter implements DetectionProfileRepositoryPort {

    private final DetectionProfileJpaRepository repository;
    private final ObjectMapper objectMapper;

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
        String thresholdsJson;
        try {
            thresholdsJson = objectMapper.writeValueAsString(profile.thresholds());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Falha ao serializar os limiares do perfil " + profile.id(), e);
        }
        return DetectionProfileJpaEntity.builder()
                .id(profile.id())
                .version(profile.version())
                .active(profile.active())
                .contractVersion(profile.contractVersion())
                .classificationParams(thresholdsJson)
                .publishedAt(profile.publishedAt())
                .publishedBy(profile.publishedBy())
                .build();
    }

    private DetectionProfile toDomain(DetectionProfileJpaEntity entity) {
        DetectionThresholdTable thresholds;
        try {
            thresholds =
                    objectMapper.readValue(
                            entity.getClassificationParams(), DetectionThresholdTable.class);
        } catch (JsonProcessingException e) {
            // Um perfil gravado antes da V0401 pode trazer o mapa livre antigo. A migration faz o
            // backfill; se algo escapou, falhar aqui e melhor que servir limiar inventado a borda.
            throw new IllegalStateException(
                    "Perfil "
                            + entity.getId()
                            + " tem limiares ilegiveis — esperado o formato de DetectionThresholdTable"
                            + " (ver migration V0401)",
                    e);
        }
        return new DetectionProfile(
                entity.getId(),
                entity.getVersion(),
                Boolean.TRUE.equals(entity.getActive()),
                entity.getContractVersion(),
                thresholds,
                entity.getPublishedAt(),
                entity.getPublishedBy(),
                TenantContextHolder.getTenantIdOrNull());
    }
}
