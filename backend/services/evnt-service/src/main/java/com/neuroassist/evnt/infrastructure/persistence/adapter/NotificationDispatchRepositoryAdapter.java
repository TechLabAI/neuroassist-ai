package com.neuroassist.evnt.infrastructure.persistence.adapter;

import com.neuroassist.evnt.application.port.out.NotificationDispatchRepositoryPort;
import com.neuroassist.evnt.domain.model.NotificationDispatch;
import com.neuroassist.evnt.infrastructure.persistence.entity.NotificationDispatchJpaEntity;
import com.neuroassist.evnt.infrastructure.persistence.repository.NotificationDispatchJpaRepository;
import lombok.RequiredArgsConstructor;
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
