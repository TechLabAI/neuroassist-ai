package com.neuroassist.acs.infrastructure.clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.acs.application.port.out.HistAuditOutboxPort;
import com.neuroassist.acs.application.port.out.HistAuditPort;
import com.neuroassist.acs.application.port.out.HistAuditPort.AuditFact;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter de {@link HistAuditPort} sobre {@link HistAuditClient} (E2E-019).
 *
 * <p><b>O que isto substituiu.</b> Um {@code // TODO (ACS-017/P3)} e um {@code log.debug} — o
 * acs-service registrava o fato num log e chamava isso de auditoria. Login e falha de login não
 * existiam na trilha, e para dado de saúde sob LGPD "quem entrou e quando" é o primeiro registro
 * que uma auditoria pede.
 *
 * <p><b>Fire-and-forget, e assim deve continuar.</b> Uma falha ao auditar nunca pode impedir alguém
 * de autenticar: a cuidadora precisa entrar no painel quando o alarme chega, mesmo com o
 * hist-service fora do ar.
 *
 * <p><b>Fail-open, mas não fail-forgetful</b> (E2E-031). Até aqui a falha era registrada em {@code
 * error} e o fato se ia — e um fato de autenticação perdido é lacuna em "quem entrou e quando", que
 * é o registro que uma auditoria LGPD pede primeiro. Agora vai para {@code acs_hist_outbox} e
 * {@code HistAuditOutboxDrainService} o reentrega.
 *
 * <p>O request é serializado e guardado inteiro, e não re-derivado depois: o {@code factId} tem de
 * ser reenviado idêntico para o hist-service tratar a reentrega como replay em vez de um segundo
 * fato (defeito C-5).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistAuditAdapter implements HistAuditPort {

    private static final String SUBJECT_CUIDADOR = "cuidador";

    private final HistAuditClient histAuditClient;
    private final HistAuditOutboxPort outboxPort;
    private final ObjectMapper objectMapper;

    @Override
    public void recordFact(AuditFact fact) {
        if (fact.empresaId() == null) {
            // Sem tenant não há schema a que o fato pertença. Acontece numa tentativa de login com
            // usuário inexistente: não há de quem ser o fato. Inventar um tenant seria pior —
            // deixaria um desconhecido escrever na trilha de uma empresa qualquer.
            log.warn(
                    "Audit fact {} sem tenant — nao enviado ao hist-service (sem schema de"
                            + " destino)",
                    fact.fact());
            return;
        }

        HistAuditClient.RecordAuditFactRequest request =
                new HistAuditClient.RecordAuditFactRequest(
                        // factId cunhado aqui, uma vez. É a chave de idempotência do hist-service:
                        // o mesmo valor reenviado devolve o registro original em vez de acrescentar
                        // um segundo (HIST ADR-005, defeito C-5).
                        UUID.randomUUID(),
                        fact.fact(),
                        new HistAuditClient.SubjectRefDto(SUBJECT_CUIDADOR, fact.actorId()),
                        new HistAuditClient.ActorRefDto(fact.actorId(), displayName(fact)),
                        fact.payload(),
                        fact.occurredAt(),
                        // Fato de autenticação não é sobre paciente — ver HistAuditClient.
                        null);

        try {
            HistAuditClient.RecordAuditFactResponse response =
                    HistAuditRequestInterceptor.forTenant(
                            fact.empresaId(), () -> histAuditClient.recordFact(request));
            log.info(
                    "Audit fact {} gravado em sequenceNo={} tenant={}",
                    fact.fact(),
                    response == null ? null : response.sequenceNo(),
                    fact.empresaId());
        } catch (Exception e) {
            log.warn(
                    "Audit fact {} nao entregue, enfileirado para retry: {}",
                    fact.fact(),
                    e.getMessage());
            enqueueForRetry(request, fact.empresaId());
        }
    }

    /**
     * Última linha de defesa. Se o próprio enfileiramento falhar, o fato está mesmo perdido — e
     * isso precisa ser barulhento, porque é o estado que esta issue existe para eliminar.
     *
     * <p>Propagar aqui derrubaria a autenticação por causa da auditoria, que é o contrário do que
     * fire-and-forget significa.
     */
    private void enqueueForRetry(HistAuditClient.RecordAuditFactRequest request, UUID tenantId) {
        try {
            outboxPort.enqueue(
                    request.factId(),
                    request.factType(),
                    tenantId,
                    objectMapper.writeValueAsString(request));
        } catch (Exception queueFailure) {
            log.error(
                    "Audit fact {} NAO pode ser enfileirado e esta perdido: {}",
                    request.factType(),
                    queueFailure.getMessage(),
                    queueFailure);
        }
    }

    /**
     * O hist-service guarda o nome como ele estava no momento do fato. Um fato de login anterior a
     * uma troca de nome deve continuar mostrando o nome de então.
     */
    private String displayName(AuditFact fact) {
        Object nome = fact.payload() == null ? null : fact.payload().get("nome");
        if (nome instanceof String s && !s.isBlank()) {
            return s;
        }
        Object login = fact.payload() == null ? null : fact.payload().get("login");
        return login instanceof String s ? s : String.valueOf(fact.actorId());
    }
}
