package com.neuroassist.evnt.application.port.in;

/**
 * Inbound port — reprocessa dispatches de notificação que nunca foram entregues (defeito C-45) e
 * conduz a escada de escalonamento do ADR-011.
 *
 * <p>Os dois métodos já lançaram {@code UnsupportedOperationException}. Isso importava mais do que
 * parecia: {@code ReportIngressService} deixa um dispatch {@code QUEUED} quando o dash-service está
 * inalcançável e {@code NO_SESSION} quando a cuidadora não tinha painel aberto, e os comentários
 * chamavam ambos de "retryable" — mas nada os retentava.
 */
public interface NotificationOrchestrationUseCase {

    /**
     * Re-tenta um lote de dispatches para o tenant ligado à thread atual.
     *
     * <p>Não recebe argumentos porque o scheduler liga o tenant antes de a transação abrir.
     */
    SweepResult retrySweep();

    /**
     * @param escalated linhas cuja janela esgotou e que passaram o alarme ao degrau seguinte da
     *     escada — ou reiniciaram a escada numa nova rodada. Nenhum alarme é perdido aqui
     * @param expired linhas que saíram da fila por relevância clínica (ADR-011 §3). Um pedido de
     *     ajuda nunca é contado aqui
     */
    record SweepResult(
            int attempted, int delivered, int stillPending, int escalated, int expired) {}
}
