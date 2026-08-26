package com.neuroassist.acs.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * E2E-019 — escrita de fato de auditoria no hist-service.
 *
 * <p>RULE-06: {@code @FeignClient} só em {@code infrastructure.clients}.
 *
 * <p>Era uma interface vazia com {@code // TODO: add methods from techlab-ai-integration.md}, e o
 * adapter correspondente um no-op. Consequência: o acs-service <b>nunca</b> escreveu na trilha de
 * auditoria — "quem entrou e quando" não existia. AUD-01 e AUD-02 eram feature ausente, não falha
 * de integração.
 *
 * <p>Espelha {@code RecordAuditFactController} e {@code RecordAuditFactRequest} do hist-service,
 * lidos no produtor. Dois detalhes que o pac-service já pagou (C-5, C-6):
 *
 * <ul>
 *   <li><b>A idempotência é o {@code factId} no corpo</b>, não um header {@code Idempotency-Key}.
 *   <li><b>{@code factType} é vocabulário controlado</b> em {@code hist.fact-types.allowed};
 *       qualquer outro valor é 422. {@code autenticacao.realizada} e {@code autenticacao.falhou}
 *       precisaram ser adicionados lá para este cliente funcionar.
 * </ul>
 *
 * <p>O tenant vem exclusivamente do claim do JWT — por isso o token usado aqui é <b>escopado por
 * tenant</b>, e o campo opcional {@code empresaId} é omitido: enviá-lo em desacordo com o claim é
 * 403 no produtor.
 */
@FeignClient(
        name = "hist-audit-client",
        url = "${neuroassist.hist.url:${HIST_SERVICE_URL:http://hist-service:8089}}",
        configuration = HistAuditFeignConfig.class)
public interface HistAuditClient {

    @PostMapping("/api/v1/hist/audit-facts")
    RecordAuditFactResponse recordFact(@RequestBody RecordAuditFactRequest request);

    /** {@code type} ∈ {@code paciente | comando | cuidador | dispositivo}. */
    record SubjectRefDto(String type, UUID id) {}

    record ActorRefDto(UUID caregiverId, String displayNameSnapshot) {}

    /**
     * {@code scopePacienteId} fica <b>null</b> de propósito: um fato de autenticação não é sobre um
     * paciente. A consequência é deliberada — sem escopo de paciente, o fato só é legível com
     * {@code hist:read:all}, que é do coordenador. Uma cuidadora não deve ler o histórico de login
     * das colegas.
     */
    record RecordAuditFactRequest(
            UUID factId,
            String factType,
            SubjectRefDto subject,
            ActorRefDto actor,
            Map<String, Object> payload,
            Instant occurredAt,
            UUID scopePacienteId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RecordAuditFactResponse(UUID id, UUID factId, String factType, long sequenceNo) {}
}
