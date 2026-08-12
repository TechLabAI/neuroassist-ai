package com.neuroassist.gest.application.service;

import com.neuroassist.gest.api.dto.TipoGestoRequest;
import com.neuroassist.gest.api.dto.TipoGestoResponse;
import com.neuroassist.gest.api.dto.TransitionRequest;
import com.neuroassist.gest.api.exception.DuplicateTipoGestoException;
import com.neuroassist.gest.api.exception.IllegalTransitionException;
import com.neuroassist.gest.api.exception.InvalidNomeException;
import com.neuroassist.gest.api.exception.TipoGestoNotFoundException;
import com.neuroassist.gest.application.port.in.TipoGestoUseCase;
import com.neuroassist.gest.application.port.out.TipoGestoRepositoryPort;
import com.neuroassist.gest.application.port.out.TipoGestoTransitionRepositoryPort;
import com.neuroassist.gest.domain.event.PrimitiveDeprecated;
import com.neuroassist.gest.domain.event.PrimitiveRegistered;
import com.neuroassist.gest.domain.event.PrimitiveTransitioned;
import com.neuroassist.gest.domain.model.DetectabilityState;
import com.neuroassist.gest.domain.model.TipoGesto;
import com.neuroassist.gest.domain.model.TipoGestoTransition;
import com.neuroassist.gest.domain.service.TipoGestoDomainService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TipoGestoService implements TipoGestoUseCase {

    private final TipoGestoRepositoryPort tipoGestoRepositoryPort;
    private final TipoGestoTransitionRepositoryPort tipoGestoTransitionRepositoryPort;
    private final TipoGestoDomainService tipoGestoDomainService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public List<TipoGestoResponse> listAll() {
        // Tenant isolation: resolved implicitly by TipoGestoRepositoryPort.findAllByTenant()
        // (infrastructure layer reads TenantContextHolder set by TenantContextFilter).
        return tipoGestoRepositoryPort.findAllByTenant().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TipoGestoResponse findById(UUID id) {
        return tipoGestoRepositoryPort
                .findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new TipoGestoNotFoundException(id));
    }

    @Override
    public TipoGestoResponse register(TipoGestoRequest request) {
        // Tenant isolation: resolved implicitly by repository (TenantContextFilter sets context).
        String nome = request.nome();

        // Defensive validation (in addition to Bean Validation on DTO)
        if (nome == null || nome.isBlank()) {
            throw new InvalidNomeException("nome must not be blank");
        }
        if (nome.length() > 120) {
            throw new InvalidNomeException("nome must not exceed 120 characters");
        }

        if (tipoGestoRepositoryPort.existsByNomeIgnoreCaseAndStateNotDeprecated(nome)) {
            throw new DuplicateTipoGestoException(nome);
        }

        // Tenant placeholder: in P5, TenantContextHolder.require() will provide the real tenantId.
        // For P2 (mock-first), the tenant is surfaced through the repository port.
        String tenantRef = "tenant-pending-p5";

        TipoGesto tipoGesto = TipoGesto.register(nome, tenantRef);
        TipoGesto saved = tipoGestoRepositoryPort.save(tipoGesto);

        eventPublisher.publishEvent(
                new PrimitiveRegistered(
                        saved.id(), saved.nome(), saved.tenantId(), null, Instant.now()));

        log.info("TipoGesto registered: id={} nome={}", saved.id(), saved.nome());
        return toResponse(saved);
    }

    @Override
    public TipoGestoResponse transition(UUID id, TransitionRequest request) {
        TipoGesto tipoGesto =
                tipoGestoRepositoryPort
                        .findById(id)
                        .orElseThrow(() -> new TipoGestoNotFoundException(id));

        DetectabilityState toState;
        try {
            toState = DetectabilityState.valueOf(request.toState().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalTransitionException(
                    tipoGesto.detectabilityState().name(), request.toState());
        }

        // Delegate state-machine guard to domain service (ADR-006 invariant)
        tipoGestoDomainService.validateTransition(tipoGesto.detectabilityState(), toState);

        // Append-only transition audit record
        TipoGestoTransition transition =
                TipoGestoTransition.of(
                        id,
                        tipoGesto.detectabilityState(),
                        toState,
                        request.actor(),
                        request.reason());
        tipoGestoTransitionRepositoryPort.save(transition);

        // Update domain object with new state
        TipoGesto updated = tipoGesto.withState(toState, request.actor());
        TipoGesto saved = tipoGestoRepositoryPort.save(updated);

        // Publish transition event
        eventPublisher.publishEvent(
                new PrimitiveTransitioned(
                        id,
                        transition.fromState().name(),
                        toState.name(),
                        request.actor(),
                        Instant.now()));

        // Publish specialised deprecated event if transitioning to DEPRECATED
        if (toState == DetectabilityState.DEPRECATED) {
            eventPublisher.publishEvent(
                    new PrimitiveDeprecated(id, saved.nome(), request.actor(), Instant.now()));
        }

        log.info(
                "TipoGesto transitioned: id={} from={} to={}", id, transition.fromState(), toState);
        return toResponse(saved);
    }

    // ── Mapping ──────────────────────────────────────────────────────────

    private TipoGestoResponse toResponse(TipoGesto tg) {
        return new TipoGestoResponse(
                tg.id(),
                tg.nome(),
                tg.detectabilityState().name().toLowerCase(),
                tg.engineFeatureRef(),
                tg.createdAt(),
                tg.updatedAt());
    }
}
