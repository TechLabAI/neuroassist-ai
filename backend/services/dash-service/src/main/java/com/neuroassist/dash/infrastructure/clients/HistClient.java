package com.neuroassist.dash.infrastructure.clients;

import com.neuroassist.dash.infrastructure.clients.dto.HistoryPageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client para o HIST (Historico e Auditoria).
 * Consulta historico paginado de eventos de um paciente.
 * RULE-06: FeignClient apenas em infrastructure.clients.
 */
@FeignClient(name = "hist-service", url = "${dash.hist.base-url:}", contextId = "histClient")
public interface HistClient {

    @GetMapping("/api/v1/patients/{patientId}/history")
    HistoryPageResponse getHistory(
            @PathVariable("patientId") String patientId,
            @RequestParam("page") int page,
            @RequestParam("size") int size);
}
