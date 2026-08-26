package com.neuroassist.dash.infrastructure.clients;

import com.neuroassist.dash.infrastructure.clients.dto.DeviceStatusResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client para o EDGE (Integração de Dispositivo Edge).
 *
 * <p><b>Este client chamava um endpoint que não existe.</b> O path era {@code GET
 * /api/v1/devices/patient/&#123;patientId&#125;/liveness}; o edge-service expõe {@code GET
 * /api/v1/edge/patients/&#123;pacienteId&#125;/liveness}. O DTO também tinha um campo {@code
 * degraded} que o produtor nunca devolveu — 9ª ocorrência do padrão
 * consumidor-contra-o-próprio-mock (C-34).
 *
 * <p><b>Sobre ETag.</b> O edge-service implementa {@code If-None-Match} → 304 corretamente, e este
 * client deliberadamente não o usa: guardar o ETag entre chamadas exigiria estado no BFF, que é
 * zero-state por desenho (ADR-004). O ganho de banda pertence a quem tem onde guardar o cache — o
 * navegador, ou um proxy à frente do EDGE.
 */
@FeignClient(name = "edge-service", url = "${dash.edge.base-url:}", contextId = "edgeClient")
public interface EdgeClient {

    @GetMapping("/api/v1/edge/patients/{pacienteId}/liveness")
    DeviceStatusResponse getDeviceLiveness(@PathVariable("pacienteId") String pacienteId);
}
