package com.neuroassist.evnt.infrastructure.persistence.adapter;

import com.neuroassist.evnt.application.port.out.EventoRepositoryPort;
import com.neuroassist.evnt.domain.model.Evento;
import com.neuroassist.evnt.infrastructure.persistence.entity.EventoJpaEntity;
import com.neuroassist.evnt.infrastructure.persistence.repository.EventoJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventoRepositoryAdapter implements EventoRepositoryPort {

    private final EventoJpaRepository repository;

    @Override
    public Evento save(Evento evento) {
        EventoJpaEntity entity = toEntity(evento);
        EventoJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Evento> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Evento> findByFeedback(String feedback) {
        return repository.findByFeedback(feedback).stream().map(this::toDomain).toList();
    }

    private EventoJpaEntity toEntity(Evento e) {
        return EventoJpaEntity.builder()
                .id(e.id())
                .pacienteId(e.pacienteId())
                .comandoRef(e.comandoRef())
                .commandLabelSnapshot(e.commandLabelSnapshot())
                .urgenciaSnapshot(e.urgenciaSnapshot())
                .feedback(e.feedback())
                .confirmadoPor(e.confirmadoPor())
                .confirmadoAt(e.confirmadoAt())
                .recognizedAt(e.recognizedAt())
                .createdAt(e.createdAt())
                .build();
    }

    private Evento toDomain(EventoJpaEntity e) {
        return new Evento(
                e.getId(),
                e.getPacienteId(),
                e.getComandoRef(),
                e.getCommandLabelSnapshot(),
                e.getUrgenciaSnapshot(),
                e.getFeedback(),
                e.getConfirmadoPor(),
                e.getConfirmadoAt(),
                e.getRecognizedAt(),
                e.getCreatedAt());
    }
}
