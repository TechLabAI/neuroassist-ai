package com.neuroassist.acs.infrastructure.persistence.adapter;

import com.neuroassist.acs.application.port.out.CaregiverPatientLinkRepositoryPort;
import com.neuroassist.acs.domain.model.CaregiverPatientLink;
import com.neuroassist.acs.domain.model.LinkStatus;
import com.neuroassist.acs.infrastructure.persistence.entity.CaregiverPatientLinkJpaEntity;
import com.neuroassist.acs.infrastructure.persistence.entity.CuidadorJpaEntity;
import com.neuroassist.acs.infrastructure.persistence.repository.CaregiverPatientLinkJpaRepository;
import com.neuroassist.acs.infrastructure.persistence.repository.CuidadorJpaRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adapter for CaregiverPatientLinkRepositoryPort (empresa schema via Hibernate multi-tenancy). Full
 * implementation including coverage invariant support (ACS-015/016, P3).
 */
@Component
@RequiredArgsConstructor
public class CaregiverPatientLinkRepositoryAdapter implements CaregiverPatientLinkRepositoryPort {

    private final CaregiverPatientLinkJpaRepository repository;
    private final CuidadorJpaRepository cuidadorRepository;

    @Override
    public CaregiverPatientLink save(CaregiverPatientLink entity) {
        CaregiverPatientLinkJpaEntity jpaEntity = toEntity(entity);
        CaregiverPatientLinkJpaEntity saved = repository.save(jpaEntity);
        return toDomain(saved);
    }

    @Override
    public Optional<CaregiverPatientLink> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<CaregiverPatientLink> findByTenantId(UUID tenantId) {
        return repository.findByTenantId(tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<CaregiverPatientLink> findActiveByPacienteId(UUID pacienteId) {
        return repository.findByPacienteIdAndStatus(pacienteId, LinkStatus.ACTIVE).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<CaregiverPatientLink> findActiveByCuidadorIdAndPacienteId(
            UUID cuidadorId, UUID pacienteId) {
        return repository
                .findByCuidador_IdAndPacienteIdAndStatus(cuidadorId, pacienteId, LinkStatus.ACTIVE)
                .map(this::toDomain);
    }

    @Override
    public long countActiveByPacienteId(UUID pacienteId) {
        return repository.countByPacienteIdAndStatus(pacienteId, LinkStatus.ACTIVE);
    }

    @Override
    public List<CaregiverPatientLink> findActiveByCuidadorId(UUID cuidadorId) {
        return repository.findByCuidador_IdAndStatus(cuidadorId, LinkStatus.ACTIVE).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<CaregiverPatientLink> findByCuidadorIdAndPacienteId(
            UUID cuidadorId, UUID pacienteId) {
        return repository
                .findFirstByCuidador_IdAndPacienteIdOrderByCriadoEmDesc(cuidadorId, pacienteId)
                .map(this::toDomain);
    }

    // -----------------------------------------------------------------------
    // Private mapping
    // -----------------------------------------------------------------------

    private CaregiverPatientLinkJpaEntity toEntity(CaregiverPatientLink link) {
        CaregiverPatientLinkJpaEntity entity = new CaregiverPatientLinkJpaEntity();
        if (link.id() != null) {
            entity.setId(link.id());
        }
        entity.setTenantId(link.tenantId());

        // Resolve CuidadorJpaEntity for @ManyToOne mapping
        CuidadorJpaEntity cuidador =
                cuidadorRepository
                        .findById(link.cuidadorId())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Cuidador not found for link persistence: "
                                                        + link.cuidadorId()));
        entity.setCuidador(cuidador);

        entity.setPacienteId(link.pacienteId());
        entity.setStatus(link.status());
        entity.setRevogadoEm(
                link.revogadoEm() != null
                        ? LocalDateTime.ofInstant(link.revogadoEm(), ZoneOffset.UTC)
                        : null);
        entity.setCriadoEm(
                link.criadoEm() != null
                        ? LocalDateTime.ofInstant(link.criadoEm(), ZoneOffset.UTC)
                        : LocalDateTime.now(ZoneOffset.UTC));
        return entity;
    }

    private CaregiverPatientLink toDomain(CaregiverPatientLinkJpaEntity entity) {
        return new CaregiverPatientLink(
                entity.getId(),
                entity.getTenantId(),
                entity.getCuidador() != null ? entity.getCuidador().getId() : null,
                entity.getPacienteId(),
                entity.getStatus(),
                entity.getCriadoEm() != null
                        ? entity.getCriadoEm().toInstant(ZoneOffset.UTC)
                        : null,
                entity.getRevogadoEm() != null
                        ? entity.getRevogadoEm().toInstant(ZoneOffset.UTC)
                        : null);
    }
}
