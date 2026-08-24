package com.neuroassist.dash.application.port.out;

import com.neuroassist.dash.domain.model.NotificationPayload;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Outbound port — interacao com o EVNT para eventos e notificacoes. */
public interface EventStatePort {

    /** Confirma um evento no EVNT (write-through). */
    void confirmEvent(String eventId, String caregiverId);

    /** Retorna notificacoes nao reconhecidas para o cuidador. */
    List<NotificationPayload> getUnacknowledgedNotifications(String caregiverId);

    /** Registra o reconhecimento (ack) de uma notificacao no EVNT. */
    void acknowledgeNotification(String dispatchId, String caregiverId);

    /**
     * Quais destes pacientes têm pedido de ajuda em aberto.
     *
     * <p><b>Uma pergunta só para o conjunto.</b> O painel lista N pacientes e precisa acender o
     * indicador em cada linha; uma chamada por paciente seria um N+1 sobre HTTP justamente no
     * caminho que a cuidadora abre quando o alarme chega.
     *
     * <p>Falha de transporte <b>propaga</b>. Devolver conjunto vazio aqui faria "não consegui
     * perguntar ao EVNT" ficar indistinguível de "ninguém está pedindo ajuda" — e essa é a
     * afirmação mais perigosa que este painel pode fazer errado. Quem decide degradar é o serviço
     * de aplicação, que tem onde registrar a diferença ({@code hasActiveEvent = null}).
     */
    Set<String> findPatientsWithActiveEvent(Collection<String> patientIds);
}
