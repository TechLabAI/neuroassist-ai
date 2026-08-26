package com.neuroassist.evnt.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Guards that enum constant names match the values actually written to the database (E2E-025).
 *
 * <p>These tests are deliberately trivial: {@code Enum.name()} returns exactly what Flyway defaults
 * and the application services persist. If someone renames a constant without updating the column
 * values this suite fails before any data is corrupted.
 *
 * <p>Columns and their sources:
 *
 * <ul>
 *   <li>{@code evento.feedback} — VARCHAR(50), Flyway default {@code 'NAO_AVALIADO'}, written by
 *       {@link Feedback}.
 *   <li>{@code notification_dispatch.tier} — VARCHAR(50), written by {@link Tier} (lowercase per DB
 *       inventory).
 *   <li>{@code notification_dispatch.status} — VARCHAR(50), Flyway default {@code 'QUEUED'},
 *       written by {@link DispatchStatuses} (a constants class, not an enum).
 * </ul>
 */
class EnumPersistenceAlignmentTest {

    // -------------------------------------------------------------------------
    // Feedback — evento.feedback column
    // -------------------------------------------------------------------------

    @Test
    void feedback_naoAvaliado_matchesPersisted() {
        assertThat(Feedback.NAO_AVALIADO.name())
                .as("Flyway default for evento.feedback is 'NAO_AVALIADO'")
                .isEqualTo("NAO_AVALIADO");
    }

    @Test
    void feedback_real_matchesPersisted() {
        assertThat(Feedback.REAL.name())
                .as("Verdict 'real' is stored as 'REAL' in evento.feedback")
                .isEqualTo("REAL");
    }

    @Test
    void feedback_falsoPositivo_matchesPersisted() {
        assertThat(Feedback.FALSO_POSITIVO.name())
                .as("Verdict 'falso_positivo' is stored as 'FALSO_POSITIVO' in evento.feedback")
                .isEqualTo("FALSO_POSITIVO");
    }

    // -------------------------------------------------------------------------
    // Tier — notification_dispatch.tier column (lowercase by convention)
    // -------------------------------------------------------------------------

    @Test
    void tier_fullScreenAlarm_matchesPersisted() {
        assertThat(Tier.full_screen_alarm.name())
                .as(
                        "Urgency 'emergencia' maps to 'full_screen_alarm' in notification_dispatch.tier")
                .isEqualTo("full_screen_alarm");
    }

    @Test
    void tier_discreet_matchesPersisted() {
        assertThat(Tier.discreet.name())
                .as("Non-emergency urgency maps to 'discreet' in notification_dispatch.tier")
                .isEqualTo("discreet");
    }

    // -------------------------------------------------------------------------
    // DispatchStatuses — notification_dispatch.status column
    // -------------------------------------------------------------------------

    @Test
    void dispatchStatuses_queued_matchesFlyway() {
        assertThat(DispatchStatuses.QUEUED)
                .as("Flyway default for notification_dispatch.status is 'QUEUED'")
                .isEqualTo("QUEUED");
    }

    @Test
    void dispatchStatuses_delivered_matchesPersisted() {
        assertThat(DispatchStatuses.DELIVERED).isEqualTo("DELIVERED");
    }

    @Test
    void dispatchStatuses_acknowledged_matchesPersisted() {
        assertThat(DispatchStatuses.ACKNOWLEDGED).isEqualTo("ACKNOWLEDGED");
    }

    @Test
    void dispatchStatuses_failed_matchesPersisted() {
        assertThat(DispatchStatuses.FAILED).isEqualTo("FAILED");
    }

    @Test
    void dispatchStatuses_noSession_matchesPersisted() {
        assertThat(DispatchStatuses.NO_SESSION).isEqualTo("NO_SESSION");
    }

    /** ADR-011 §1 — o degrau fechado da escada. A coluna é varchar(50) sem CHECK. */
    @Test
    void dispatchStatuses_escalated_matchesPersisted() {
        assertThat(DispatchStatuses.ESCALATED).isEqualTo("ESCALATED");
    }

    /**
     * ADR-011 §1 — a ordem das constantes de {@link EscalationGrade} É a ordem normativa da escada,
     * e {@code CaregiverLadder} ordena por {@code ordinal()}. Reordenar o enum reordena a escada em
     * produção sem que nenhum outro teste perceba; é por isso que esta asserção existe.
     */
    @Test
    void escalationGrade_ordemNormativaDaEscada() {
        assertThat(EscalationGrade.values())
                .containsExactly(
                        EscalationGrade.CUIDADORA_ORIGINAL,
                        EscalationGrade.CUIDADORA_ADICIONAL,
                        EscalationGrade.COORDENADOR_CLINICA,
                        EscalationGrade.PACIENTE_APOIO,
                        EscalationGrade.DESCONHECIDO);
    }

    /** Os nomes são o que a coluna {@code escalation_grade} (varchar(50), V0502) recebe. */
    @Test
    void escalationGrade_nomesBatemComOsPapeisDoAcs() {
        assertThat(EscalationGrade.COORDENADOR_CLINICA.name()).isEqualTo("COORDENADOR_CLINICA");
        assertThat(EscalationGrade.PACIENTE_APOIO.name()).isEqualTo("PACIENTE_APOIO");
    }

    @Test
    void dispatchStatuses_apenasQueuedENoSessionSaoRetentaveis() {
        assertThat(DispatchStatuses.isRetryable(DispatchStatuses.QUEUED)).isTrue();
        assertThat(DispatchStatuses.isRetryable(DispatchStatuses.NO_SESSION)).isTrue();
        assertThat(DispatchStatuses.isRetryable(DispatchStatuses.DELIVERED)).isFalse();
        assertThat(DispatchStatuses.isRetryable(DispatchStatuses.ACKNOWLEDGED)).isFalse();
        assertThat(DispatchStatuses.isRetryable(DispatchStatuses.ESCALATED)).isFalse();
        assertThat(DispatchStatuses.isRetryable(DispatchStatuses.FAILED)).isFalse();
    }
}
