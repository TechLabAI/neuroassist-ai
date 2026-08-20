package com.neuroassist.acs.infrastructure.persistence.adapter;

import com.neuroassist.acs.application.port.out.CuidadorRepositoryPort;
import com.neuroassist.acs.domain.model.Cuidador;
import com.neuroassist.acs.domain.model.CuidadorStatus;
import com.neuroassist.acs.infrastructure.persistence.entity.CuidadorJpaEntity;
import com.neuroassist.acs.infrastructure.persistence.repository.CuidadorJpaRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adapter for CuidadorRepositoryPort (empresa schema via Hibernate multi-tenancy). Full CRUD +
 * findByLogin + listByTenant implemented (ACS-010, P3).
 */
@Component
@RequiredArgsConstructor
public class CuidadorRepositoryAdapter implements CuidadorRepositoryPort {

    private final CuidadorJpaRepository repository;

    @Override
    public Cuidador save(Cuidador entity) {
        CuidadorJpaEntity jpaEntity = toEntity(entity);
        CuidadorJpaEntity saved = repository.save(jpaEntity);
        return toDomain(saved);
    }

    @Override
    public Optional<Cuidador> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Cuidador> findByTenantId(UUID tenantId) {
        return repository.findByTenantId(tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Cuidador> findByLogin(String login) {
        return repository.findByLoginIgnoreCase(login).map(this::toDomain);
    }

    @Override
    public List<Cuidador> findAllByIds(List<UUID> ids) {
        return repository.findAllById(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public long countActive() {
        return repository.countByStatus(CuidadorStatus.ACTIVE);
    }

    // -----------------------------------------------------------------------
    // Private mapping
    // -----------------------------------------------------------------------

    private CuidadorJpaEntity toEntity(Cuidador cuidador) {
        CuidadorJpaEntity entity = new CuidadorJpaEntity();
        if (cuidador.id() != null) {
            entity.setId(cuidador.id());
        }
        entity.setTenantId(cuidador.tenantId());
        entity.setNome(cuidador.nome());
        entity.setLogin(cuidador.login().toLowerCase());
        entity.setCredentialHash(cuidador.credentialHash());
        entity.setPapel(cuidador.papel());
        entity.setStatus(cuidador.status());
        entity.setCriadoEm(
                cuidador.criadoEm() != null
                        ? LocalDateTime.ofInstant(cuidador.criadoEm(), ZoneOffset.UTC)
                        : LocalDateTime.now(ZoneOffset.UTC));
        entity.setAtualizadoEm(
                cuidador.atualizadoEm() != null
                        ? LocalDateTime.ofInstant(cuidador.atualizadoEm(), ZoneOffset.UTC)
                        : null);
        return entity;
    }

    private Cuidador toDomain(CuidadorJpaEntity entity) {
        return new Cuidador(
                entity.getId(),
                entity.getTenantId(),
                entity.getNome(),
                entity.getLogin(),
                entity.getCredentialHash(),
                entity.getPapel(),
                entity.getStatus(),
                entity.getCriadoEm() != null
                        ? entity.getCriadoEm().toInstant(ZoneOffset.UTC)
                        : null,
                entity.getAtualizadoEm() != null
                        ? entity.getAtualizadoEm().toInstant(ZoneOffset.UTC)
                        : null);
    }
}
