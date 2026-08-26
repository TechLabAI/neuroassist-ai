package com.neuroassist.evnt.infrastructure.persistence.adapter;

import com.neuroassist.evnt.application.port.out.NotificationDispatchRepositoryPort;
import com.neuroassist.evnt.domain.model.DispatchStatuses;
import com.neuroassist.evnt.domain.model.NotificationDispatch;
import com.neuroassist.evnt.infrastructure.persistence.entity.NotificationDispatchJpaEntity;
import com.neuroassist.evnt.infrastructure.persistence.repository.NotificationDispatchJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationDispatchRepositoryAdapter implements NotificationDispatchRepositoryPort {

    /** Estados que já não representam um alarme pendente para a cuidadora daquela linha. */
    private static final List<String> NAO_PENDENTES =
            List.of(
                    DispatchStatuses.ACKNOWLEDGED,
                    DispatchStatuses.ESCALATED,
                    DispatchStatuses.FAILED);

    private static final List<String> RETENTAVEIS =
            List.of(DispatchStatuses.QUEUED, DispatchStatuses.NO_SESSION);

    private final NotificationDispatchJpaRepository repository;

    @Override
    public NotificationDispatch save(NotificationDispatch dispatch) {
        NotificationDispatchJpaEntity entity = toEntity(dispatch);
        NotificationDispatchJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<NotificationDispatch> findById(UUID dispatchId) {
        return repository.findById(dispatchId).map(this::toDomain);
    }

    @Override
    public List<NotificationDispatch> findByEventoId(UUID eventoId) {
        return repository
                .findByEventoIdOrderByEscalationRoundAscEscalationOrderAsc(eventoId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<NotificationDispatch> findDueForRetry(Instant now, int limit) {
        return repository.findDueForRetry(RETENTAVEIS, now, PageRequest.of(0, limit)).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<NotificationDispatch> findUnacknowledgedFor(String caregiverId) {
        return repository.findByTargetCaregiverIdAndStatusNotIn(caregiverId, NAO_PENDENTES).stream()
                .map(this::toDomain)
                .toList();
    }

    /**
     * Um grau gravado que esta versão não conhece — porque o ACS passou a devolver um papel novo,
     * ou porque a linha é anterior à V0502 — vira {@code DESCONHECIDO} em vez de derrubar a
     * leitura. Um alarme não pode ficar ilegível por causa de um rótulo.
     */
    private com.neuroassist.evnt.domain.model.EscalationGrade gradeOf(String persisted) {
        if (persisted == null || persisted.isBlank()) {
            return null;
        }
        try {
            return com.neuroassist.evnt.domain.model.EscalationGrade.valueOf(persisted);
        } catch (IllegalArgumentException e) {
            return com.neuroassist.evnt.domain.model.EscalationGrade.DESCONHECIDO;
        }
    }

    private NotificationDispatchJpaEntity toEntity(NotificationDispatch d) {
        return NotificationDispatchJpaEntity.builder()
                .id(d.id())
                .eventoId(d.eventoId())
                .tier(d.tier())
                .targetCaregiverId(d.caregiverId())
                .status(d.status())
                .attempts(d.attempts())
                .lastAttemptAt(d.lastAttemptAt())
                .escalationOrder(d.escalationOrder())
                .escalationGrade(d.escalationGrade() == null ? null : d.escalationGrade().name())
                .escalationRound(d.escalationRound())
                .nextAttemptAt(d.nextAttemptAt())
                .outcomeReason(d.outcomeReason())
                .build();
    }

    private NotificationDispatch toDomain(NotificationDispatchJpaEntity e) {
        return new NotificationDispatch(
                e.getId(),
                e.getEventoId(),
                e.getTier(),
                e.getTargetCaregiverId(),
                e.getStatus(),
                e.getAttempts(),
                e.getLastAttemptAt(),
                e.getEscalationOrder(),
                gradeOf(e.getEscalationGrade()),
                e.getEscalationRound(),
                e.getNextAttemptAt(),
                e.getOutcomeReason());
    }
}
