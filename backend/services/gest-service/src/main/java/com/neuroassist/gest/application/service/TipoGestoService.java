package com.neuroassist.gest.application.service;

import com.neuroassist.gest.application.port.in.TipoGestoUseCase;
import com.neuroassist.gest.application.port.out.TipoGestoRepositoryPort;
import com.neuroassist.gest.domain.model.TipoGesto;
import com.neuroassist.gest.domain.model.TipoGestoTransition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TipoGestoService implements TipoGestoUseCase {

    private final TipoGestoRepositoryPort tipoGestoRepositoryPort;

    @Override
    public List<TipoGesto> listAll() {
        log.debug("Listing all tipo_gesto records");
        return tipoGestoRepositoryPort.findByTenantId("poc");
    }

    @Override
    public TipoGesto findById(String id) {
        log.debug("Finding tipo_gesto by id={}", id);
        return tipoGestoRepositoryPort.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TipoGesto not found: " + id));
    }

    @Override
    public TipoGesto register(String nome) {
        log.info("Registering new tipo_gesto: {}", nome);
        TipoGesto tg = new TipoGesto(
                UUID.randomUUID().toString(),
                nome,
                "REGISTERED",
                null,
                Instant.now(),
                Instant.now());
        return tipoGestoRepositoryPort.save(tg);
    }

    @Override
    public TipoGesto transition(String id, String toState, String engineFeatureRef, String reason) {
        log.info("Transitioning tipo_gesto id={} to state={}", id, toState);
        TipoGesto tg = tipoGestoRepositoryPort.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TipoGesto not found: " + id));
        TipoGestoTransition t = new TipoGestoTransition(
                UUID.randomUUID(),
                id,
                tg.detectabilityState(),
                toState,
                "system",
                reason,
                Instant.now());
        tipoGestoRepositoryPort.saveTransition(t);
        TipoGesto updated = new TipoGesto(
                tg.id(), tg.nome(), toState, engineFeatureRef, tg.createdAt(), Instant.now());
        return tipoGestoRepositoryPort.save(updated);
    }
}
