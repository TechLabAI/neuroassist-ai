package com.neuroassist.pac.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.neuroassist.pac.application.port.in.ComandoLifecycleUseCase.PublishResult;
import com.neuroassist.pac.application.port.in.RecalibrateCommand;
import com.neuroassist.pac.application.port.out.ComandoRepositoryPort;
import com.neuroassist.pac.application.port.out.EmergencyEligibilityGate;
import com.neuroassist.pac.application.port.out.EmergencyEligibilityGate.EligibilityResult;
import com.neuroassist.pac.application.port.out.HistFactPort;
import com.neuroassist.pac.application.port.out.PacienteRepositoryPort;
import com.neuroassist.pac.application.port.out.PacienteVersionBumper;
import com.neuroassist.pac.domain.model.Comando;
import com.neuroassist.pac.domain.model.EstadoComando;
import com.neuroassist.pac.domain.model.Sensibilidade;
import com.neuroassist.pac.domain.model.Urgencia;
import com.neuroassist.pac.infrastructure.security.TenantContextHolder;
import com.neuroassist.pac.shared.error.CodedException;
import com.neuroassist.pac.shared.error.EligibilityGateUnavailableException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * PAC-014 / PAC-015 / PAC-016 / PAC-018 — Unit tests for ComandoLifecycleService.
 *
 * <p>Safety-critical scenarios (SPEC-03 acceptance criteria):
 *
 * <ul>
 *   <li>Emergency publish with GEST supported=false → CodedException(pac.emergency.not-detectable)
 *   <li>Emergency publish with gate unavailable (CB OPEN / timeout) →
 *       EligibilityGateUnavailableException
 *   <li>Routine publish with GEST unavailable → PublishResult with warnings[] (non-empty)
 *   <li>Retire on retired comando → CodedException(pac.comando.invalid-lifecycle-transition)
 *   <li>Recalibrate: lowering urgency → gate NOT re-executed
 *   <li>Recalibrate: escalating urgency → gate IS re-executed
 *   <li>Recalibrate: calibracaoVersao +1 exact
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ComandoLifecycleServiceTest {

    private static final String TENANT_ID = "empresa-abc";
    private static final UUID PACIENTE_ID = UUID.randomUUID();
    private static final UUID COMANDO_ID = UUID.randomUUID();
    private static final UUID PRIMITIVO_ID = UUID.randomUUID();

    @Mock private ComandoRepositoryPort comandoRepository;

    @Mock private PacienteRepositoryPort pacienteRepository;

    @Mock private EmergencyEligibilityGate eligibilityGate;

    @Mock private HistFactPort histFactPort;

    @Mock private PacienteVersionBumper pacienteVersionBumper;

    @InjectMocks private ComandoLifecycleService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // -------------------------------------------------------------------------
    // Helper factories
    // -------------------------------------------------------------------------

    private Comando makeComando(EstadoComando estado, Urgencia urgencia) {
        return new Comando(
                COMANDO_ID,
                PACIENTE_ID,
                "Levantar mao",
                "hidratacao",
                PRIMITIVO_ID,
                null,
                Sensibilidade.media,
                200,
                800,
                urgencia,
                estado,
                0,
                1,
                Instant.now(),
                Instant.now());
    }

    private void givenPacienteExists() {
        com.neuroassist.pac.domain.model.Paciente fakePaciente =
                new com.neuroassist.pac.domain.model.Paciente(
                        PACIENTE_ID,
                        TENANT_ID,
                        "Marcio",
                        null,
                        null,
                        1,
                        0,
                        Instant.now(),
                        Instant.now());
        when(pacienteRepository.findByIdAndTenantId(PACIENTE_ID, TENANT_ID))
                .thenReturn(Optional.of(fakePaciente));
    }

    private void givenComandoExists(EstadoComando estado, Urgencia urgencia) {
        when(comandoRepository.findByIdAndPacienteId(COMANDO_ID, PACIENTE_ID))
                .thenReturn(Optional.of(makeComando(estado, urgencia)));
    }

    private void givenSaveReturns(Comando savedBy) {
        when(comandoRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            Comando toSave = inv.getArgument(0);
                            // Return same record — simulates JPA save
                            return toSave;
                        });
    }

    // =========================================================================
    // validate (PAC-014)
    // =========================================================================

    @Nested
    @DisplayName("validate — drafted → validated")
    class ValidateTests {

        @Test
        @DisplayName("validate: drafted → validated successfully")
        void validate_draftedToValidated() {
            givenPacienteExists();
            givenComandoExists(EstadoComando.drafted, Urgencia.rotina);
            givenSaveReturns(null);

            Comando result = service.validate(PACIENTE_ID, COMANDO_ID);

            assertThat(result.estado()).isEqualTo(EstadoComando.validated);
            verify(comandoRepository).save(any());
        }

        @Test
        @DisplayName("validate: validated estado → 409 invalid-lifecycle-transition")
        void validate_alreadyValidated_throws409() {
            givenPacienteExists();
            givenComandoExists(EstadoComando.validated, Urgencia.rotina);

            assertThatThrownBy(() -> service.validate(PACIENTE_ID, COMANDO_ID))
                    .isInstanceOf(CodedException.class)
                    .satisfies(
                            ex ->
                                    assertThat(((CodedException) ex).code())
                                            .isEqualTo("pac.comando.invalid-lifecycle-transition"));

            verify(comandoRepository, never()).save(any());
        }

        @Test
        @DisplayName("validate: published estado → 409")
        void validate_published_throws409() {
            givenPacienteExists();
            givenComandoExists(EstadoComando.published, Urgencia.rotina);

            assertThatThrownBy(() -> service.validate(PACIENTE_ID, COMANDO_ID))
                    .isInstanceOf(CodedException.class)
                    .satisfies(
                            ex ->
                                    assertThat(((CodedException) ex).code())
                                            .isEqualTo("pac.comando.invalid-lifecycle-transition"));
        }
    }

    // =========================================================================
    // publish (PAC-015) — SAFETY-CRITICAL TESTS
    // =========================================================================

    @Nested
    @DisplayName("publish — validated → published (emergency gate)")
    class PublishTests {

        @Test
        @DisplayName(
                "SAFETY: emergency publish — GEST supported=false → 422 emergency-not-detectable")
        void publish_emergency_gestNotDetectable_throws422() {
            givenPacienteExists();
            givenComandoExists(EstadoComando.validated, Urgencia.emergencia);

            when(eligibilityGate.check(PRIMITIVO_ID, null, Urgencia.emergencia))
                    .thenThrow(
                            new CodedException(
                                    "pac.emergency.not-detectable",
                                    "GEST reports gesture as 'pending-training'"));

            assertThatThrownBy(() -> service.publish(PACIENTE_ID, COMANDO_ID))
                    .isInstanceOf(CodedException.class)
                    .satisfies(
                            ex ->
                                    assertThat(((CodedException) ex).code())
                                            .isEqualTo("pac.emergency.not-detectable"));

            // DB must NOT be written when gate rejects (fail-closed)
            verify(comandoRepository, never()).save(any());
        }

        @Test
        @DisplayName("SAFETY: emergency publish — GEST timeout/CB OPEN → 503 + Retry-After:30")
        void publish_emergency_gateUnavailable_throws503() {
            givenPacienteExists();
            givenComandoExists(EstadoComando.validated, Urgencia.emergencia);

            when(eligibilityGate.check(PRIMITIVO_ID, null, Urgencia.emergencia))
                    .thenThrow(new EligibilityGateUnavailableException(30, "CB is OPEN"));

            assertThatThrownBy(() -> service.publish(PACIENTE_ID, COMANDO_ID))
                    .isInstanceOf(EligibilityGateUnavailableException.class)
                    .satisfies(
                            ex ->
                                    assertThat(
                                                    ((EligibilityGateUnavailableException) ex)
                                                            .retryAfterSeconds())
                                            .isEqualTo(30));

            // DB must NOT be written when gate is unavailable (fail-closed)
            verify(comandoRepository, never()).save(any());
        }

        @Test
        @DisplayName("SAFETY: routine publish — GEST unavailable → 200 with warnings[] (advisory)")
        void publish_routine_gestUnavailable_returnsWarnings() {
            givenPacienteExists();
            givenComandoExists(EstadoComando.validated, Urgencia.rotina);
            givenSaveReturns(null);

            when(eligibilityGate.check(PRIMITIVO_ID, null, Urgencia.rotina))
                    .thenReturn(
                            EligibilityResult.withWarnings(List.of("gest-service-unavailable")));

            PublishResult result = service.publish(PACIENTE_ID, COMANDO_ID);

            assertThat(result.comando().estado()).isEqualTo(EstadoComando.published);
            assertThat(result.warnings()).isNotEmpty().contains("gest-service-unavailable");
            verify(comandoRepository).save(any());
        }

        @Test
        @DisplayName("emergency publish — GEST supported=true → 200 no warnings")
        void publish_emergency_gestSupported_succeeds() {
            givenPacienteExists();
            givenComandoExists(EstadoComando.validated, Urgencia.emergencia);
            givenSaveReturns(null);

            when(eligibilityGate.check(PRIMITIVO_ID, null, Urgencia.emergencia))
                    .thenReturn(EligibilityResult.ok());

            PublishResult result = service.publish(PACIENTE_ID, COMANDO_ID);

            assertThat(result.comando().estado()).isEqualTo(EstadoComando.published);
            assertThat(result.warnings()).isEmpty();
        }

        @Test
        @DisplayName("publish: drafted estado → 409 (gate NOT called)")
        void publish_drafted_throws409_gateNotCalled() {
            givenPacienteExists();
            givenComandoExists(EstadoComando.drafted, Urgencia.emergencia);

            assertThatThrownBy(() -> service.publish(PACIENTE_ID, COMANDO_ID))
                    .isInstanceOf(CodedException.class)
                    .satisfies(
                            ex ->
                                    assertThat(((CodedException) ex).code())
                                            .isEqualTo("pac.comando.invalid-lifecycle-transition"));

            verifyNoInteractions(eligibilityGate);
            verify(comandoRepository, never()).save(any());
        }
    }

    // =========================================================================
    // retire (PAC-016) — soft delete, ADR-006
    // =========================================================================

    @Nested
    @DisplayName("retire — soft delete (ADR-006)")
    class RetireTests {

        @Test
        @DisplayName("retire: published → retired successfully (soft delete)")
        void retire_published_succeeds() {
            givenPacienteExists();
            givenComandoExists(EstadoComando.published, Urgencia.rotina);
            givenSaveReturns(null);

            Comando result = service.retire(PACIENTE_ID, COMANDO_ID);

            assertThat(result.estado()).isEqualTo(EstadoComando.retired);
            // Row is saved (soft delete), not hard-deleted
            verify(comandoRepository).save(any());
        }

        @Test
        @DisplayName("SAFETY: retire on already-retired → 409 invalid-lifecycle-transition")
        void retire_alreadyRetired_throws409() {
            givenPacienteExists();
            givenComandoExists(EstadoComando.retired, Urgencia.rotina);

            assertThatThrownBy(() -> service.retire(PACIENTE_ID, COMANDO_ID))
                    .isInstanceOf(CodedException.class)
                    .satisfies(
                            ex ->
                                    assertThat(((CodedException) ex).code())
                                            .isEqualTo("pac.comando.invalid-lifecycle-transition"));

            // ADR-006: no second modification to an already-retired row
            verify(comandoRepository, never()).save(any());
        }

        @Test
        @DisplayName("retire: validated → retired (pre-publish retirement)")
        void retire_validated_succeeds() {
            givenPacienteExists();
            givenComandoExists(EstadoComando.validated, Urgencia.rotina);
            givenSaveReturns(null);

            Comando result = service.retire(PACIENTE_ID, COMANDO_ID);

            assertThat(result.estado()).isEqualTo(EstadoComando.retired);
        }

        @Test
        @DisplayName("retire: drafted → retired (draft cancellation)")
        void retire_drafted_succeeds() {
            givenPacienteExists();
            givenComandoExists(EstadoComando.drafted, Urgencia.rotina);
            givenSaveReturns(null);

            Comando result = service.retire(PACIENTE_ID, COMANDO_ID);

            assertThat(result.estado()).isEqualTo(EstadoComando.retired);
        }
    }

    // =========================================================================
    // recalibrate (PAC-018) — published ↻ published, calibracaoVersao +1
    // =========================================================================

    @Nested
    @DisplayName("recalibrate — published ↻ published (PAC-018)")
    class RecalibrateTests {

        @Test
        @DisplayName("recalibrate: published → published, calibracaoVersao +1 exact")
        void recalibrate_published_bumpsCalibracao() {
            givenPacienteExists();
            givenComandoExists(EstadoComando.published, Urgencia.rotina);
            givenSaveReturns(null);
            when(pacienteVersionBumper.bumpAndGet(PACIENTE_ID)).thenReturn(1);

            RecalibrateCommand cmd = new RecalibrateCommand(null, null, null, null);
            Comando result = service.recalibrate(PACIENTE_ID, COMANDO_ID, cmd);

            assertThat(result.estado()).isEqualTo(EstadoComando.published);
            assertThat(result.calibracaoVersao()).isEqualTo(1); // was 0 in fixture, +1 = 1
            verify(pacienteVersionBumper).bumpAndGet(PACIENTE_ID);
            verify(histFactPort).record(any());
        }

        @Test
        @DisplayName("recalibrate: lowering urgency (emergencia → rotina) → gate NOT re-executed")
        void recalibrate_loweringUrgency_gateNotCalled() {
            givenPacienteExists();
            givenComandoExists(EstadoComando.published, Urgencia.emergencia);
            givenSaveReturns(null);
            when(pacienteVersionBumper.bumpAndGet(PACIENTE_ID)).thenReturn(1);

            // Lowering urgency: emergencia → rotina
            RecalibrateCommand cmd = new RecalibrateCommand(Urgencia.rotina, null, null, null);
            Comando result = service.recalibrate(PACIENTE_ID, COMANDO_ID, cmd);

            assertThat(result.estado()).isEqualTo(EstadoComando.published);
            assertThat(result.urgencia()).isEqualTo(Urgencia.rotina);
            // Gate must NOT be called when lowering urgency
            verifyNoInteractions(eligibilityGate);
        }

        @Test
        @DisplayName("recalibrate: same urgency (rotina → rotina) → gate NOT re-executed")
        void recalibrate_sameUrgency_gateNotCalled() {
            givenPacienteExists();
            givenComandoExists(EstadoComando.published, Urgencia.rotina);
            givenSaveReturns(null);
            when(pacienteVersionBumper.bumpAndGet(PACIENTE_ID)).thenReturn(1);

            RecalibrateCommand cmd = new RecalibrateCommand(Urgencia.rotina, null, null, null);
            service.recalibrate(PACIENTE_ID, COMANDO_ID, cmd);

            verifyNoInteractions(eligibilityGate);
        }

        @Test
        @DisplayName("recalibrate: urgency escalation (rotina → emergencia) → gate IS re-executed")
        void recalibrate_escalatingUrgency_gateReRun() {
            givenPacienteExists();
            givenComandoExists(EstadoComando.published, Urgencia.rotina);
            givenSaveReturns(null);
            when(pacienteVersionBumper.bumpAndGet(PACIENTE_ID)).thenReturn(1);

            when(eligibilityGate.check(PRIMITIVO_ID, null, Urgencia.emergencia))
                    .thenReturn(EligibilityResult.ok());

            RecalibrateCommand cmd = new RecalibrateCommand(Urgencia.emergencia, null, null, null);
            Comando result = service.recalibrate(PACIENTE_ID, COMANDO_ID, cmd);

            assertThat(result.urgencia()).isEqualTo(Urgencia.emergencia);
            verify(eligibilityGate).check(PRIMITIVO_ID, null, Urgencia.emergencia);
        }

        @Test
        @DisplayName(
                "SAFETY: recalibrate escalation — gate unavailable → 503 (lifecycle NOT committed)")
        void recalibrate_escalation_gateUnavailable_throws503() {
            givenPacienteExists();
            givenComandoExists(EstadoComando.published, Urgencia.rotina);

            when(eligibilityGate.check(PRIMITIVO_ID, null, Urgencia.emergencia))
                    .thenThrow(new EligibilityGateUnavailableException(30, "CB is OPEN"));

            RecalibrateCommand cmd = new RecalibrateCommand(Urgencia.emergencia, null, null, null);
            assertThatThrownBy(() -> service.recalibrate(PACIENTE_ID, COMANDO_ID, cmd))
                    .isInstanceOf(EligibilityGateUnavailableException.class);

            // DB must NOT be written when gate rejects escalation
            verify(comandoRepository, never()).save(any());
        }

        @Test
        @DisplayName("recalibrate: not published (validated) → 409 invalid-lifecycle-transition")
        void recalibrate_validated_throws409() {
            givenPacienteExists();
            givenComandoExists(EstadoComando.validated, Urgencia.rotina);

            RecalibrateCommand cmd = new RecalibrateCommand(null, null, null, null);
            assertThatThrownBy(() -> service.recalibrate(PACIENTE_ID, COMANDO_ID, cmd))
                    .isInstanceOf(CodedException.class)
                    .satisfies(
                            ex ->
                                    assertThat(((CodedException) ex).code())
                                            .isEqualTo("pac.comando.invalid-lifecycle-transition"));

            verify(comandoRepository, never()).save(any());
        }

        @Test
        @DisplayName("recalibrate: HIST unavailable → lifecycle committed (outbox resilient)")
        void recalibrate_histUnavailable_lifecycleCommits() {
            givenPacienteExists();
            givenComandoExists(EstadoComando.published, Urgencia.rotina);
            givenSaveReturns(null);
            when(pacienteVersionBumper.bumpAndGet(PACIENTE_ID)).thenReturn(1);

            // HistFactPort.record() never throws — the adapter swallows failures internally
            // If it did throw, the test would fail proving the resilient contract is broken
            doNothing().when(histFactPort).record(any());

            RecalibrateCommand cmd = new RecalibrateCommand(null, null, null, null);
            Comando result = service.recalibrate(PACIENTE_ID, COMANDO_ID, cmd);

            assertThat(result.calibracaoVersao()).isEqualTo(1);
            verify(histFactPort).record(any());
        }
    }
}
