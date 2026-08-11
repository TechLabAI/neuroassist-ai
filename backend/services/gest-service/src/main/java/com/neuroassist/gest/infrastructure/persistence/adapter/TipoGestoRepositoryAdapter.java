package com.neuroassist.gest.infrastructure.persistence.adapter;

import com.neuroassist.gest.application.port.out.TipoGestoRepositoryPort;
import com.neuroassist.gest.domain.model.DetectabilityState;
import com.neuroassist.gest.domain.model.TipoGesto;
import com.neuroassist.gest.infrastructure.persistence.entity.TipoGestoJpaEntity;
import com.neuroassist.gest.infrastructure.persistence.repository.TipoGestoJpaRepository;
import com.neuroassist.gest.infrastructure.security.TenantContextHolder;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * JPA adapter for {@link TipoGestoRepositoryPort}. Bridges the hexagonal boundary between
 * application use-cases and Spring Data JPA. All methods rely on schema-routing via {@link
 * com.neuroassist.gest.infrastructure.security.MultiTenantConnectionProvider} — no tenant_id column
 * is present in the schema. Implemented in P3 (GEST-011).
 */
@Component
@RequiredArgsConstructor
public class TipoGestoRepositoryAdapter implements TipoGestoRepositoryPort {

    private final TipoGestoJpaRepository repository;

    @Override
    public TipoGesto save(TipoGesto tipoGesto) {
        TipoGestoJpaEntity entity = toEntity(tipoGesto);
        TipoGestoJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<TipoGesto> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<TipoGesto> findAllByTenant() {
        // Schema routing is transparent — findAll() already scopes to the current tenant's schema
        // via the search_path set by MultiTenantConnectionProvider.
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByNomeIgnoreCaseAndStateNotDeprecated(String nome) {
        return repository.existsByNomeLowerCaseAndStateNotDeprecated(nome);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Mapping helpers — domain ↔ JPA entity
    // ────────────────────────────────────────────────────────────────────────

    private TipoGestoJpaEntity toEntity(TipoGesto tipoGesto) {
        Instant now = Instant.now();
        return TipoGestoJpaEntity.builder()
                .id(tipoGesto.id())
                .nome(tipoGesto.nome())
                // DB CHECK constraint uses lowercase: 'registered', 'pending_training', etc.
                .detectabilityState(tipoGesto.detectabilityState().name().toLowerCase())
                .engineFeatureRef(tipoGesto.engineFeatureRef())
                .createdAt(tipoGesto.createdAt() != null ? tipoGesto.createdAt() : now)
                .updatedAt(tipoGesto.updatedAt() != null ? tipoGesto.updatedAt() : now)
                .createdBy(tipoGesto.createdBy())
                .updatedBy(tipoGesto.updatedBy())
                .build();
    }

    private TipoGesto toDomain(TipoGestoJpaEntity entity) {
        // detectabilityState stored lowercase in DB (CHECK constraint) — enum is UPPERCASE.
        // e.g. "pending_training" → DetectabilityState.PENDING_TRAINING
        DetectabilityState state =
                DetectabilityState.valueOf(entity.getDetectabilityState().toUpperCase());
        return new TipoGesto(
                entity.getId(),
                entity.getNome(),
                state,
                entity.getEngineFeatureRef(),
                TenantContextHolder.getTenantIdOrNull(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedBy());
    }
}
