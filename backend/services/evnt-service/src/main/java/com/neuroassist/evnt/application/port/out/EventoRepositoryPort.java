package com.neuroassist.evnt.application.port.out;

import com.neuroassist.evnt.domain.model.Evento;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventoRepositoryPort {

    Evento save(Evento evento);

    Optional<Evento> findById(UUID id);

    /**
     * Eventos com este feedback, <b>do mais recente para o mais antigo</b>.
     *
     * <p>O nome carrega a ordenação de propósito. A versão anterior chamava-se {@code
     * findByFeedback} e não prometia ordem nenhuma — e o {@code EventQueryService} fazia {@code
     * .get(0)} sobre o resultado. "O alarme ativo" era, na prática, a linha que o Postgres
     * devolvesse primeiro: com dois alarmes abertos, qual deles a cuidadora via era arbitrário e
     * podia mudar entre duas chamadas idênticas. Um nome que não promete ordem é exatamente o que
     * torna esse tipo de defeito invisível na revisão.
     */
    List<Evento> findByFeedbackMaisRecentePrimeiro(String feedback);

    /**
     * Quais <b>destes</b> pacientes têm ao menos um evento com este feedback.
     *
     * <p><b>Por que a pergunta é sobre o conjunto e não sobre um paciente.</b> Quem consome isto é
     * o painel da cuidadora, que lista N pacientes e precisa acender o indicador de "pedido de
     * ajuda em aberto" em cada linha. Uma porta {@code temAlarmeAtivo(pacienteId)} obrigaria N
     * chamadas — um N+1 sobre HTTP no caminho mais quente do sistema, justamente aquele que a
     * cuidadora abre quando o alarme chega. Aqui é um SELECT só.
     *
     * @return os ids que têm alarme, sem repetição; ausência na lista significa "perguntei e não
     *     há", nunca "não consegui perguntar" — falha de infraestrutura sobe como exceção
     */
    List<String> findPacienteIdsByFeedback(String feedback, Collection<String> pacienteIds);

    /** Returns all eventos with seq > lastSeq for Last-Event-ID backfill. */
    List<Evento> findBySeqGreaterThan(long lastSeq);
}
