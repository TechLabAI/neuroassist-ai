package com.neuroassist.evnt.infrastructure.persistence.repository;

import com.neuroassist.evnt.infrastructure.persistence.entity.EventoJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventoJpaRepository extends JpaRepository<EventoJpaEntity, UUID> {

    /**
     * Ordenado por {@code seq} decrescente — {@code seq} é o {@code bigserial} do banco, então é a
     * ordem de chegada real e não depende de relógio de aplicação (ao contrário de {@code
     * recognizedAt}, que vem do dispositivo e pode estar atrasado ou repetido).
     */
    List<EventoJpaEntity> findByFeedbackOrderBySeqDesc(String feedback);

    /**
     * Projeção de um campo só: devolve os {@code paciente_id} distintos que têm evento com este
     * feedback, dentro do conjunto perguntado. Uma ida ao banco para os N pacientes do painel.
     */
    @Query(
            "select distinct e.pacienteId from EventoJpaEntity e "
                    + "where e.feedback = :feedback and e.pacienteId in :pacienteIds")
    List<String> findPacienteIdsByFeedback(
            @Param("feedback") String feedback,
            @Param("pacienteIds") Collection<String> pacienteIds);

    List<EventoJpaEntity> findBySeqGreaterThan(long lastSeq);
}
