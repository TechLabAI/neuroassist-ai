package com.neuroassist.dash.infrastructure.clients;

import com.neuroassist.dash.infrastructure.clients.dto.HistoryPageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client para o HIST (Histórico e Auditoria).
 *
 * <p><b>Este client chamava um endpoint que não existe.</b> O path era {@code GET
 * /api/v1/patients/&#123;patientId&#125;/history}, e o hist-service expõe {@code GET
 * /api/v1/hist/history} com o paciente informado por {@code subjectType}/{@code subjectId}. A
 * resposta também divergia por completo: {@code &#123;entries, page, totalPages&#125;} contra
 * {@code &#123;items, page, size, total&#125;}. {@code GET /panel/patients/&#123;id&#125;/history}
 * respondia 404 em runtime — 8ª ocorrência do padrão em que o consumidor foi escrito, e testado,
 * contra o contrato que ele próprio imaginou (plan-e2e-integration.md §11).
 *
 * <p><b>Escopo.</b> O hist-service restringe a leitura aos pacientes do cuidador autenticado
 * (C-16), por isso {@code HistFeignConfig} <b>não</b> se aplica aqui: esta chamada propaga o JWT do
 * chamador, porque a autorização depende de <i>quem perguntou</i> (service-identity.md §4).
 */
@FeignClient(name = "hist-service", url = "${dash.hist.base-url:}", contextId = "histClient")
public interface HistClient {

    /**
     * O hist-service pagina por {@code page}/{@code size} e filtra por sujeito. Um paciente é
     * {@code subjectType=paciente}; enviar {@code subjectId} sem {@code subjectType} é 400.
     */
    @GetMapping("/api/v1/hist/history")
    HistoryPageResponse getHistory(
            @RequestParam("subjectType") String subjectType,
            @RequestParam("subjectId") String subjectId,
            @RequestParam("page") int page,
            @RequestParam("size") int size);
}
