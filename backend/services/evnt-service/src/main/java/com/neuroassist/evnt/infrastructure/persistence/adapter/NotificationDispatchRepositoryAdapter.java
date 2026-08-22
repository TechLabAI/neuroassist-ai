package com.neuroassist.evnt.infrastructure.persistence.adapter;

import com.neuroassist.evnt.application.port.out.NotificationDispatchRepositoryPort;
import com.neuroassist.evnt.domain.model.DispatchStatuses;
import com.neuroassist.evnt.domain.model.NotificationDispatch;
import com.neuroassist.evnt.infrastructure.persistence.entity.NotificationDispatchJpaEntity;
import com.neuroassist.evnt.infrastructure.persistence.repository.NotificationDispatchJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationDispatchRepositoryAdapter implements NotificationDispatchRepositoryPort {

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
    public List<NotificationDispatch> findUndelivered(int limit) {
        return repository
                .findByStatusInOrderByLastAttemptAtAscIdAsc(
                        List.of(DispatchStatuses.QUEUED, DispatchStatuses.NO_SESSION),
                        PageRequest.of(0, limit))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<NotificationDispatch> findUnacknowledgedFor(String caregiverId) {
        return repository
                .findByTargetCaregiverIdAndStatusNot(caregiverId, DispatchStatuses.ACKNOWLEDGED)
                .stream()
                .map(this::toDomain)
                .toList();
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
                e.getLastAttemptAt());
    }
}
