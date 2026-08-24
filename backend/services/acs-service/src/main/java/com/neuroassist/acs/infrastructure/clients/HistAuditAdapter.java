package com.neuroassist.acs.infrastructure.clients;

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
 * hist-service fora do ar. A perda é registrada em {@code error} — ver a limitação conhecida no
 * final desta classe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistAuditAdapter implements HistAuditPort {

    private static final String SUBJECT_CUIDADOR = "cuidador";

    private final HistAuditClient histAuditClient;

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
            // Best-effort — nunca propaga. `error` e não `warn`: um fato de autenticação perdido é
            // um buraco na trilha, não um contratempo.
            //
            // LIMITAÇÃO CONHECIDA: o acs-service não tem outbox. O PAC e o EVNT têm, e um fato
            // produzido durante uma indisponibilidade do HIST sobrevive lá. Aqui, não. Registrado
            // no corpo de E2E-019 em vez de deixado para alguém descobrir.
            log.error(
                    "Audit fact {} NAO gravado e perdido — acs-service nao tem outbox: {}",
                    fact.fact(),
                    e.getMessage());
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
