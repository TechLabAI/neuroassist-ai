package com.neuroassist.evnt.infrastructure.persistence.repository;

import com.neuroassist.evnt.infrastructure.persistence.entity.NotificationDispatchJpaEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationDispatchJpaRepository
        extends JpaRepository<NotificationDispatchJpaEntity, UUID> {

    List<NotificationDispatchJpaEntity> findByTargetCaregiverIdAndStatusNotIn(
            String targetCaregiverId, Collection<String> statuses);

    List<NotificationDispatchJpaEntity> findByEventoIdOrderByEscalationRoundAscEscalationOrderAsc(
            UUID eventoId);

    /**
     * Candidatos do sweep: retentáveis e com a espera vencida.
     *
     * <p>A ordenação é escrita à mão de propósito. {@code escalation_round} primeiro faz um alarme
     * antigo — que já percorreu a escada inteira uma ou mais vezes — ceder a vez a um recém-chegado
     * quando o batch é menor que o backlog. {@code last_attempt_at ASC NULLS FIRST} coloca o que
     * nunca foi tentado à frente do que já foi. {@code id} desempata, para que a varredura seja
     * reproduzível.
     */
    @Query(
            """
            SELECT d FROM NotificationDispatchJpaEntity d
            WHERE d.status IN :statuses
              AND (d.nextAttemptAt IS NULL OR d.nextAttemptAt <= :now)
            ORDER BY d.escalationRound ASC,
                     CASE WHEN d.lastAttemptAt IS NULL THEN 0 ELSE 1 END ASC,
                     d.lastAttemptAt ASC,
                     d.id ASC
            """)
    List<NotificationDispatchJpaEntity> findDueForRetry(
            @Param("statuses") Collection<String> statuses,
            @Param("now") Instant now,
            Pageable pageable);
}
