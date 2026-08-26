package com.neuroassist.evnt.application.service;

import com.neuroassist.evnt.application.port.out.NotificationDispatchRepositoryPort;
import com.neuroassist.evnt.domain.model.DispatchStatuses;
import com.neuroassist.evnt.domain.model.NotificationDispatch;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório em memória para os testes de unidade do sweep.
 *
 * <p>Reproduz o filtro e a ordem do {@code findDueForRetry} real. Reproduzir não é provar: a query
 * de verdade é exercida contra PostgreSQL em {@code NotificationDispatchSweepOrderingIT}, e é lá
 * que a justiça entre um alarme antigo e um recém-chegado fica demonstrada. Aqui o objetivo é outro
 * — rodar dezenas de varreduras seguidas sem Spring, para ver o que acontece com um alarme ao longo
 * do tempo.
 */
final class InMemoryDispatchRepository implements NotificationDispatchRepositoryPort {

    private final Map<UUID, NotificationDispatch> rows = new LinkedHashMap<>();

    @Override
    public NotificationDispatch save(NotificationDispatch dispatch) {
        rows.put(dispatch.id(), dispatch);
        return dispatch;
    }

    @Override
    public Optional<NotificationDispatch> findById(UUID dispatchId) {
        return Optional.ofNullable(rows.get(dispatchId));
    }

    @Override
    public List<NotificationDispatch> findByEventoId(UUID eventoId) {
        return rows.values().stream()
                .filter(d -> eventoId.equals(d.eventoId()))
                .sorted(
                        Comparator.comparingInt(NotificationDispatch::escalationRound)
                                .thenComparingInt(NotificationDispatch::escalationOrder))
                .toList();
    }

    @Override
    public List<NotificationDispatch> findDueForRetry(Instant now, int limit) {
        return rows.values().stream()
                .filter(d -> DispatchStatuses.isRetryable(d.status()))
                .filter(d -> d.nextAttemptAt() == null || !d.nextAttemptAt().isAfter(now))
                .sorted(
                        Comparator.comparingInt(NotificationDispatch::escalationRound)
                                .thenComparing(
                                        NotificationDispatch::lastAttemptAt,
                                        Comparator.nullsFirst(Comparator.naturalOrder()))
                                .thenComparing(d -> d.id().toString()))
                .limit(limit)
                .toList();
    }

    @Override
    public List<NotificationDispatch> findUnacknowledgedFor(String caregiverId) {
        return rows.values().stream()
                .filter(d -> caregiverId.equals(d.caregiverId()))
                .filter(
                        d ->
                                !DispatchStatuses.ACKNOWLEDGED.equals(d.status())
                                        && !DispatchStatuses.ESCALATED.equals(d.status())
                                        && !DispatchStatuses.FAILED.equals(d.status()))
                .toList();
    }

    List<NotificationDispatch> all() {
        return List.copyOf(rows.values());
    }
}
