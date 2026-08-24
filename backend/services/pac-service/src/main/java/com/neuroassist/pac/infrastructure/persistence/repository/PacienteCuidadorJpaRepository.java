package com.neuroassist.pac.infrastructure.persistence.repository;

import com.neuroassist.pac.infrastructure.persistence.entity.PacienteCuidadorId;
import com.neuroassist.pac.infrastructure.persistence.entity.PacienteCuidadorJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@code paciente_cuidador}. */
public interface PacienteCuidadorJpaRepository
        extends JpaRepository<PacienteCuidadorJpaEntity, PacienteCuidadorId> {

    /** Returns all caregiver links for a given patient. */
    List<PacienteCuidadorJpaEntity> findByIdPacienteId(UUID pacienteId);

    /**
     * Apaga todos os vinculos de um paciente (usado no PUT para substituir o conjunto).
     *
     * <p><b>{@code clearAutomatically} e {@code flushAutomatically} nao sao enfeite — sem eles o
     * PUT apagava os vinculos e nao gravava os novos.</b>
     *
     * <p>Um DELETE em massa via {@code @Modifying} vai ao banco na hora, mas passa ao lado do
     * contexto de persistencia: as entidades carregadas continuam gerenciadas e "existindo" para o
     * Hibernate. O {@code saveAll} seguinte chama {@code save()}, e como esta entidade usa
     * {@code @EmbeddedId} e nao implementa {@code Persistable}, o Spring Data decide pelo id — id
     * nao nulo significa {@code merge()}, nao {@code persist()}. O merge encontra a instancia
     * obsoleta ainda gerenciada, considera que nada mudou e <b>nao emite INSERT nenhum</b>.
     *
     * <p>Resultado observado no stack: um PUT com uma cuidadora na lista respondia 200 ecoando
     * essa cuidadora, e deixava {@code paciente_cuidador} VAZIO. O paciente ficava sem cobertura
     * (D-204) sem que o guarda disparasse — porque a lista PEDIDA nao era vazia, so o resultado.
     *
     * <p>E a mesma armadilha que CLAUDE.md descreve duas vezes: consulta em massa que ignora o
     * contexto, e {@code save()} virando {@code merge()} quando o id e atribuido pela aplicacao.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PacienteCuidadorJpaEntity pc WHERE pc.id.pacienteId = :pacienteId")
    void deleteByPacienteId(@Param("pacienteId") UUID pacienteId);
}
