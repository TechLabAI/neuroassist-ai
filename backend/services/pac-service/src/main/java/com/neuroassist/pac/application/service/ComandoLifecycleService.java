package com.neuroassist.pac.application.service;

import com.neuroassist.pac.application.port.in.ComandoLifecycleUseCase;
import com.neuroassist.pac.application.port.in.RecalibrateCommand;
import com.neuroassist.pac.application.port.out.ComandoRepositoryPort;
import com.neuroassist.pac.application.port.out.EmergencyEligibilityGate;
import com.neuroassist.pac.application.port.out.EmergencyEligibilityGate.EligibilityResult;
import com.neuroassist.pac.application.port.out.HistFactPort;
import com.neuroassist.pac.application.port.out.PacienteRepositoryPort;
import com.neuroassist.pac.application.port.out.PacienteVersionBumper;
import com.neuroassist.pac.domain.model.Comando;
import com.neuroassist.pac.domain.model.EstadoComando;
import com.neuroassist.pac.domain.model.HistFact;
import com.neuroassist.pac.domain.model.Sensibilidade;
import com.neuroassist.pac.domain.model.Urgencia;
import com.neuroassist.pac.infrastructure.security.TenantContextHolder;
import com.neuroassist.pac.shared.error.CodedException;
import com.neuroassist.pac.shared.error.EligibilityGateUnavailableException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PAC-014 / PAC-015 / PAC-016 / PAC-018 — Comando lifecycle use cases: validate, publish, retire,
 * recalibrate.
 *
 * <p>Hexagonal rules enforced:
 *
 * <ul>
 *   <li>RULE-03: {@code @Transactional} ONLY here — never in controllers or adapters.
 *   <li>RULE-07: {@code TenantContextHolder.require()} is the first call in every public method.
 *   <li>RULE-01/02: no Spring/JPA in domain layer.
 *   <li>RULE-04/05: only port interfaces injected — no infrastructure.* dependency.
 * </ul>
 *
 * <p>Emergency gate contract (ADR-005, SPEC-03):
 *
 * <ul>
 *   <li>The gate is called BEFORE the DB state change. If it throws, the transaction is not
 *       committed and the Comando stays in its previous state (fail-closed guarantee).
 *   <li>For {@code urgencia=emergencia}: gate throws on unavailability (503) or not-detectable
 *       (422).
 *   <li>For {@code urgencia=rotina}: gate never blocks publish — unavailability = advisory warning.
 *   <li>Recalibrate: gate re-runs ONLY on urgency escalation (rotina → emergencia).
 * </ul>
 *
 * <p>HIST recording (INT-PAC-004): domain facts (publish, recalibrate, retire) are recorded via
 * {@link HistFactPort} after the DB write. The port implementation uses the outbox pattern — HIST
 * unavailability never causes a rollback. {@code calibracaoVersao} and {@code catalogoVersao} are
 * bumped atomically with the lifecycle change.
 *
 * <p>Soft delete (ADR-006): {@link #retire(UUID, UUID)} sets {@code estado=retired} but never
 * hard-deletes the row. History is preserved.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComandoLifecycleService implements ComandoLifecycleUseCase {

    private final ComandoRepositoryPort comandoRepository;
    private final PacienteRepositoryPort pacienteRepository;
    private final EmergencyEligibilityGate eligibilityGate;
    private final HistFactPort histFactPort;
    private final PacienteVersionBumper pacienteVersionBumper;

    // -------------------------------------------------------------------------
    // PAC-014: validate — drafted → validated
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public Comando validate(UUID pacienteId, UUID comandoId) {
        String tenantId = TenantContextHolder.require(); // RULE-07

        assertPacienteBelongsToTenant(pacienteId, tenantId);

        Comando existing = loadComando(comandoId, pacienteId);

        // Guard: only drafted → validated transition is allowed
        if (existing.estado() != EstadoComando.drafted) {
            log.warn(
                    "validate rejected: id={} estado={} — only drafted can be validated",
                    comandoId,
                    existing.estado());
            throw invalidTransition(existing.estado(), EstadoComando.validated);
        }

        // Check that referenced primitives are known to GEST (advisory for unavailability)
        try {
            eligibilityGate.check(
                    existing.primitivoPrincipalId(),
                    existing.primitivoSecundarioId(),
                    Urgencia.rotina); // rotina → unavailability is advisory (not blocking)
        } catch (EligibilityGateUnavailableException e) {
            log.warn(
                    "GEST unavailable during validate id={} — proceeding without primitive check: {}",
                    comandoId,
                    e.getMessage());
            // GEST unavailability is advisory for validate — allow the transition
        }
        // CodedException (pac.primitive.unknown-to-gest) propagates as 422

        Comando updated = copyWithState(existing, EstadoComando.validated);
        Comando saved = comandoRepository.save(updated);

        log.info(
                "Comando validated id={} pacienteId={} tenant={}",
                saved.id(),
                pacienteId,
                tenantId);
        return saved;
    }

    // -------------------------------------------------------------------------
    // PAC-015: publish — validated → published (+ emergency gate + HIST fact)
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public PublishResult publish(UUID pacienteId, UUID comandoId) {
        String tenantId = TenantContextHolder.require(); // RULE-07

        assertPacienteBelongsToTenant(pacienteId, tenantId);

        Comando existing = loadComando(comandoId, pacienteId);

        // Guard: only validated → published transition is allowed
        if (existing.estado() != EstadoComando.validated) {
            log.warn(
                    "publish rejected: id={} estado={} — only validated can be published",
                    comandoId,
                    existing.estado());
            throw invalidTransition(existing.estado(), EstadoComando.published);
        }

        // Emergency gate — MUST run before DB write (fail-closed for emergencia, advisory for
        // rotina)
        EligibilityResult eligibility =
                eligibilityGate.check(
                        existing.primitivoPrincipalId(),
                        existing.primitivoSecundarioId(),
                        existing.urgencia());

        Comando updated = copyWithState(existing, EstadoComando.published);
        Comando saved = comandoRepository.save(updated);

        // Bump catalog version atomically with publish
        pacienteVersionBumper.bumpAndGet(pacienteId);

        // Record HIST fact — resilient (outbox pattern, never blocks lifecycle)
        histFactPort.record(HistFact.published(saved, tenantId));

        log.info(
                "Comando published id={} pacienteId={} urgencia={} tenant={} warnings={}",
                saved.id(),
                pacienteId,
                existing.urgencia(),
                tenantId,
                eligibility.warnings());

        return new PublishResult(saved, eligibility.warnings());
    }

    // -------------------------------------------------------------------------
    // PAC-016: retire — any non-retired state → retired (soft delete, ADR-006)
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public Comando retire(UUID pacienteId, UUID comandoId) {
        String tenantId = TenantContextHolder.require(); // RULE-07

        assertPacienteBelongsToTenant(pacienteId, tenantId);

        Comando existing = loadComando(comandoId, pacienteId);

        // Guard: already retired is the only invalid transition (ADR-006: terminal state)
        if (existing.estado() == EstadoComando.retired) {
            log.warn("retire rejected: id={} already in estado=retired", comandoId);
            throw invalidTransition(existing.estado(), EstadoComando.retired);
        }

        // Soft delete — update estado to retired, keep the row (ADR-006)
        Comando updated = copyWithState(existing, EstadoComando.retired);
        Comando saved = comandoRepository.save(updated);

        // Bump catalog version if this was a published command being retired
        if (existing.estado() == EstadoComando.published) {
            pacienteVersionBumper.bumpAndGet(pacienteId);
        }

        // Record HIST fact — resilient (outbox pattern, never blocks lifecycle)
        histFactPort.record(HistFact.retired(saved, tenantId));

        log.info(
                "Comando retired id={} pacienteId={} tenant={} (soft-delete, row preserved ADR-006)",
                saved.id(),
                pacienteId,
                tenantId);
        return saved;
    }

    // -------------------------------------------------------------------------
    // PAC-018: recalibrate — published ↻ published (calibracaoVersao +1)
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public Comando recalibrate(UUID pacienteId, UUID comandoId, RecalibrateCommand command) {
        String tenantId = TenantContextHolder.require(); // RULE-07

        assertPacienteBelongsToTenant(pacienteId, tenantId);

        Comando existing = loadComando(comandoId, pacienteId);

        // Guard: only published → published transition is allowed for recalibrate
        if (existing.estado() != EstadoComando.published) {
            log.warn(
                    "recalibrate rejected: id={} estado={} — only published can be recalibrated",
                    comandoId,
                    existing.estado());
            throw invalidTransition(existing.estado(), EstadoComando.published);
        }

        // Resolve effective new values (null = keep existing)
        Urgencia effectiveUrgencia =
                command.urgencia() != null ? command.urgencia() : existing.urgencia();
        Sensibilidade effectiveSensibilidade =
                command.sensibilidade() != null
                        ? command.sensibilidade()
                        : existing.sensibilidade();
        int effectiveTempoMin =
                command.tempoMinGestoMs() != null
                        ? command.tempoMinGestoMs()
                        : existing.tempoMinGestoMs();
        int effectiveTempoMax =
                command.tempoMaxGestoMs() != null
                        ? command.tempoMaxGestoMs()
                        : existing.tempoMaxGestoMs();

        // Re-run gate ONLY on urgency escalation: rotina → emergencia (SPEC-02 §5.4)
        boolean isEscalation = isUrgencyEscalation(existing.urgencia(), effectiveUrgencia);
        if (isEscalation) {
            log.info(
                    "recalibrate: urgency escalation detected rotina→emergencia, re-running gate id={}",
                    comandoId);
            eligibilityGate.check(
                    existing.primitivoPrincipalId(),
                    existing.primitivoSecundarioId(),
                    effectiveUrgencia);
        } else {
            log.debug(
                    "recalibrate: no urgency escalation (existing={} effective={}), gate NOT re-run id={}",
                    existing.urgencia(),
                    effectiveUrgencia,
                    comandoId);
        }

        // Build updated Comando with calibracaoVersao +1, new params, stays published
        Comando updated =
                new Comando(
                        existing.id(),
                        existing.pacienteId(),
                        existing.rotulo(),
                        existing.necessidade(),
                        existing.primitivoPrincipalId(),
                        existing.primitivoSecundarioId(),
                        effectiveSensibilidade,
                        effectiveTempoMin,
                        effectiveTempoMax,
                        effectiveUrgencia,
                        EstadoComando.published, // state unchanged
                        existing.calibracaoVersao()
                                + 1, // calibracaoVersao +1 exactly per recalibration
                        existing.rowVersion() + 1,
                        existing.createdAt(),
                        Instant.now());

        Comando saved = comandoRepository.save(updated);

        // Bump patient catalog version atomically
        pacienteVersionBumper.bumpAndGet(pacienteId);

        // Record HIST fact — resilient (outbox pattern, never blocks lifecycle)
        histFactPort.record(HistFact.recalibrated(saved, tenantId));

        log.info(
                "Comando recalibrated id={} pacienteId={} calibracaoVersao={} urgencia={} tenant={}",
                saved.id(),
                pacienteId,
                saved.calibracaoVersao(),
                saved.urgencia(),
                tenantId);
        return saved;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void assertPacienteBelongsToTenant(UUID pacienteId, String tenantId) {
        pacienteRepository
                .findByIdAndTenantId(pacienteId, tenantId)
                .orElseThrow(
                        () ->
                                new CodedException(
                                        "not_found.paciente", "Paciente not found: " + pacienteId));
    }

    private Comando loadComando(UUID comandoId, UUID pacienteId) {
        return comandoRepository
                .findByIdAndPacienteId(comandoId, pacienteId)
                .orElseThrow(
                        () ->
                                new CodedException(
                                        "not_found.comando", "Comando not found: " + comandoId));
    }

    /**
     * Creates a new Comando record with the given state and a bumped {@code rowVersion}. All other
     * fields are preserved. {@code updatedAt} is refreshed to now.
     */
    private static Comando copyWithState(Comando source, EstadoComando newEstado) {
        return new Comando(
                source.id(),
                source.pacienteId(),
                source.rotulo(),
                source.necessidade(),
                source.primitivoPrincipalId(),
                source.primitivoSecundarioId(),
                source.sensibilidade(),
                source.tempoMinGestoMs(),
                source.tempoMaxGestoMs(),
                source.urgencia(),
                newEstado,
                source.calibracaoVersao(),
                source.rowVersion() + 1,
                source.createdAt(),
                Instant.now());
    }

    /**
     * Returns {@code true} when urgency is escalating: from {@code rotina} to {@code emergencia}.
     * Lowering urgency ({@code emergencia → rotina}) or no change returns {@code false}.
     */
    private static boolean isUrgencyEscalation(Urgencia from, Urgencia to) {
        return from == Urgencia.rotina && to == Urgencia.emergencia;
    }

    private static CodedException invalidTransition(EstadoComando from, EstadoComando to) {
        return new CodedException(
                "pac.comando.invalid-lifecycle-transition",
                "Invalid lifecycle transition: '" + from + "' cannot transition to '" + to + "'");
    }
}
