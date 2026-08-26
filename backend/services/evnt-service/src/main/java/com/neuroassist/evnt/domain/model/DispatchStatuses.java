package com.neuroassist.evnt.domain.model;

/**
 * The values actually written to {@code notification_dispatch.status}.
 *
 * <p><b>Why this is not the {@link DispatchStatus} enum next door.</b> That enum spells its
 * constants in lower case ({@code queued}, {@code delivered}, …) and is referenced by nothing; the
 * column has always been written with the upper-case strings below, and its Flyway default is
 * {@code 'QUEUED'}. Introducing the enum here would silently change every persisted value. The
 * duplication is recorded as EVNT debt — the same shape as {@code Feedback}, whose {@code
 * nao_avaliado} never matched the {@code NAO_AVALIADO} the code writes.
 *
 * <p>The column is {@code varchar(50)} with no CHECK constraint, so these are conventions enforced
 * by the code alone.
 */
public final class DispatchStatuses {

    /** Persisted and not yet handed to dash-service, or handed over and not delivered. */
    public static final String QUEUED = "QUEUED";

    /** dash-service pushed it to an open caregiver session. */
    public static final String DELIVERED = "DELIVERED";

    /** dash-service had no open session for the caregiver — retryable (NTF-02). */
    public static final String NO_SESSION = "NO_SESSION";

    /** The caregiver acknowledged it. Terminal. */
    public static final String ACKNOWLEDGED = "ACKNOWLEDGED";

    /**
     * A janela desta cuidadora esgotou e o alarme subiu para o degrau seguinte da escada (ADR-011
     * §1). Terminal <b>para esta linha</b>, e nunca para o alarme: existe outra linha, do degrau
     * seguinte, viva na fila.
     *
     * <p>Não confundir com {@link #FAILED}. Esta linha registra "tentei a cuidadora X N vezes, não
     * consegui, passei adiante às HH:MM" — é a resposta à pergunta que se faz depois de um
     * incidente.
     */
    public static final String ESCALATED = "ESCALATED";

    /**
     * <b>Expirou por relevância clínica</b> (ADR-011 §3). Terminal.
     *
     * <p>Isto já significou "tentamos 10 vezes e desistimos". Não significa mais: um contador de
     * retry não é motivo para descartar um pedido de ajuda, e o ADR-011 removeu esse caminho.
     * {@code FAILED} agora é um desfecho <i>declarado</i> — a necessidade daquele alarme perdeu o
     * sentido pela política de urgência configurada — e vem sempre acompanhado do motivo em {@code
     * outcome_reason}.
     *
     * <p>Um alarme de ajuda nunca chega aqui, e um alarme de emergência nunca chega aqui por
     * esgotamento.
     */
    public static final String FAILED = "FAILED";

    /**
     * Estados dos quais o sweep ainda pode tirar uma entrega — {@code QUEUED} (dash-service fora do
     * ar) e {@code NO_SESSION} (cuidadora sem painel aberto).
     */
    public static boolean isRetryable(String status) {
        return QUEUED.equals(status) || NO_SESSION.equals(status);
    }

    private DispatchStatuses() {}
}
