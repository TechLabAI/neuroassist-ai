package com.neuroassist.pac.infrastructure.persistence.repository;

import com.neuroassist.pac.infrastructure.persistence.entity.PacienteJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@code paciente}. */
public interface PacienteJpaRepository extends JpaRepository<PacienteJpaEntity, UUID> {

    /** Returns all patients belonging to a specific tenant (multi-tenancy isolation). */
    List<PacienteJpaEntity> findByTenantId(String tenantId);

    /**
     * Finds a patient by id AND tenant — ensures cross-tenant data leakage is impossible at the
     * query level (defense in depth on top of TenantContextHolder.require()).
     */
    Optional<PacienteJpaEntity> findByIdAndTenantId(UUID id, String tenantId);

    /**
     * Atomically increments {@code catalogo_versao} for the given patient. Called by {@link
     * com.neuroassist.pac.infrastructure.persistence.adapter.PacienteVersionBumperAdapter} within
     * the caller's {@code @Transactional} boundary.
     *
     * @return number of rows updated (1 if found, 0 if patient not found in this schema)
     */
    // Mesma razao do DELETE em PacienteCuidadorJpaRepository: um UPDATE em massa passa ao lado do
    // contexto, entao uma leitura posterior de catalogoVersao na mesma transacao devolveria o valor
    // ANTIGO — e catalogoVersao e a semente do ETag do catalogo ativo. Servir o ETag velho faria o
    // consumidor receber 304 depois de o catalogo ter mudado.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "UPDATE PacienteJpaEntity p SET p.catalogoVersao = p.catalogoVersao + 1 WHERE p.id = :pacienteId")
    int bumpCatalogoVersao(@Param("pacienteId") UUID pacienteId);

    /**
     * Returns the current {@code catalogoVersao} for a patient — used to build the ETag for {@code
     * GET .../catalogo-ativo} without loading the full entity.
     */
    @Query("SELECT p.catalogoVersao FROM PacienteJpaEntity p WHERE p.id = :pacienteId")
    Optional<Integer> findCatalogoVersaoById(@Param("pacienteId") UUID pacienteId);
}
