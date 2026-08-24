package com.neuroassist.evnt.application.port.out;

import com.neuroassist.evnt.domain.model.NotificationDispatch;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationDispatchRepositoryPort {

    NotificationDispatch save(NotificationDispatch dispatch);

    /**
     * Dispatches a caregiver has not acknowledged yet — what dash-service asks for when a panel
     * session opens and needs to show what was missed.
     *
     * <p>Exclui também os estados terminais {@code ESCALATED} e {@code FAILED}: a primeira já foi
     * passada adiante para outra cuidadora e a segunda expirou por relevância clínica. Mostrar
     * qualquer uma das duas no painel seria reapresentar um alarme que não está mais pendente para
     * ninguém.
     */
    List<NotificationDispatch> findUnacknowledgedFor(String caregiverId);

    Optional<NotificationDispatch> findById(UUID dispatchId);

    /** Todos os degraus já criados para um alarme — a trilha da escada (ADR-011 §1). */
    List<NotificationDispatch> findByEventoId(UUID eventoId);

    /**
     * Dispatches que ainda podem render uma entrega e cuja espera já venceu — {@code QUEUED}
     * (dash-service inalcançável) ou {@code NO_SESSION} (ninguém com painel aberto), com {@code
     * next_attempt_at} nulo ou no passado.
     *
     * <p><b>A ordem importa e é parte da decisão.</b> Primeiro pela rodada da escada, depois pela
     * data da última tentativa. Um alarme antigo que já deu voltas na escada tem {@code
     * escalation_round} alto e vai para o fim da fila; um alarme recém-chegado tem rodada 0 e
     * {@code last_attempt_at} nulo, e é atendido primeiro. Sem isso, um backlog maior que o {@code
     * batch-size} faria os alarmes velhos monopolizarem todos os ticks do sweep — e o alarme que
     * está acontecendo agora ficaria esperando.
     *
     * <p>Estados terminais são excluídos: um alarme entregue, reconhecido, escalado ou expirado
     * nunca é reenviado.
     */
    List<NotificationDispatch> findDueForRetry(Instant now, int limit);
}
