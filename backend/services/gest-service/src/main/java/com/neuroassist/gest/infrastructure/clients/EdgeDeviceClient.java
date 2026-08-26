package com.neuroassist.gest.infrastructure.clients;

import java.time.Instant;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Cliente do EDGE para resolver o vínculo completo de um dispositivo — empresa <b>e</b> paciente
 * (E2E-034).
 *
 * <p>Separado do {@link EdgePairingClient} de propósito. Aquele fala com {@code
 * /devices/&#123;id&#125;/tenant}, cego a paciente por decisão de contrato; este fala com {@code
 * /devices/&#123;id&#125;}, que devolve os dois. Fundi-los faria o guarda de pareamento passar a
 * carregar dado de paciente sem ninguém ter decidido isso.
 *
 * <p>Autenticado com a <b>identidade de serviço</b> do GEST: o endpoint exige {@code edge.read} ou
 * {@code edge.internal}, e o token de serviço do EDGE — que é quem chama o GEST neste caminho —
 * carrega apenas {@code gest.detection-profile.read}.
 *
 * <p>RULE-06: {@code @FeignClient} só em {@code infrastructure.clients}.
 */
@FeignClient(
        name = "edge-device-client",
        url = "${edge.pairing.url:${EDGE_SERVICE_URL:http://edge-service:8087}}",
        configuration = ServiceIdentityFeignConfig.class)
public interface EdgeDeviceClient {

    @GetMapping("/api/v1/edge/devices/{deviceId}")
    EdgeDeviceView getDevice(@PathVariable("deviceId") UUID deviceId);

    /**
     * Espelha {@code DeviceResponse} do edge-service. <b>Public</b> record: um proxy dinâmico do
     * JDK não enxerga tipo package-private, e esse foi um dos 16 defeitos da Sessão 2.
     *
     * <p>Só os campos usados aqui são declarados; o resto do payload é ignorado pelo Jackson.
     */
    record EdgeDeviceView(
            UUID deviceId,
            String tenantRef,
            UUID pacienteId,
            String pairingState,
            String status,
            Instant ultimaSincronizacao) {}
}
