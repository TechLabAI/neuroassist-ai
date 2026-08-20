package com.neuroassist.acs.infrastructure.persistence.adapter;

import com.neuroassist.acs.application.port.out.TenantRepositoryPort;
import com.neuroassist.acs.domain.model.Tenant;
import com.neuroassist.acs.domain.model.TenantStatus;
import com.neuroassist.acs.domain.model.TenantTipo;
import com.neuroassist.acs.infrastructure.persistence.entity.TenantJpaEntity;
import com.neuroassist.acs.infrastructure.persistence.repository.TenantJpaRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adapter for TenantRepositoryPort (neuroassist_global schema). Full implementation in ACS-009
 * (P3). Core save + findById implemented for P2 provisioning.
 */
@Component
@RequiredArgsConstructor
public class TenantRepositoryAdapter implements TenantRepositoryPort {

    private final TenantJpaRepository repository;

    @Override
    public Tenant save(Tenant tenant) {
        TenantJpaEntity entity = toEntity(tenant);
        TenantJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Tenant> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    private TenantJpaEntity toEntity(Tenant tenant) {
        TenantJpaEntity entity = new TenantJpaEntity();
        if (tenant.id() != null) {
            entity.setId(tenant.id());
        }
        entity.setNome(tenant.nome());
        entity.setTipo(TenantTipo.valueOf(tenant.tipo().name()));
        entity.setStatus(TenantStatus.valueOf(tenant.status().name()));
        entity.setSchemaNome(tenant.schemaNome());
        entity.setCriadoEm(
                tenant.criadoEm() != null
                        ? LocalDateTime.ofInstant(tenant.criadoEm(), ZoneOffset.UTC)
                        : LocalDateTime.now());
        return entity;
    }

    private Tenant toDomain(TenantJpaEntity entity) {
        return new Tenant(
                entity.getId(),
                entity.getNome(),
                entity.getTipo(),
                entity.getStatus(),
                entity.getSchemaNome(),
                entity.getCriadoEm() != null
                        ? entity.getCriadoEm().toInstant(ZoneOffset.UTC)
                        : null);
    }
}
