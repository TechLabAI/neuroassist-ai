package com.neuroassist.gest.infrastructure.clients;

import com.neuroassist.gest.application.port.out.EdgeDevicePort;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter de {@link EdgeDevicePort}.
 *
 * <p>Nunca lança <b>para fora</b>: o chamador é o caminho de leitura do dispositivo, e uma exceção
 * aqui viraria um 5xx no heartbeat por causa de um dado que só refina a detecção. Cada desfecho
 * vira um estado explícito de {@link DeviceBinding} — e "não há paciente pareado" nunca se confunde
 * com "o EDGE não respondeu".
 *
 * <p>Mas a falha de transporte <b>sobe até o breaker</b> antes de virar estado: quem a converte é o
 * {@code fallbackMethod}, não um {@code catch}. Capturá-la no corpo do método devolveria um valor
 * normal, o resilience4j contaria sucesso, e o breaker nunca abriria.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EdgeDeviceAdapter implements EdgeDevicePort {

    private final EdgeDeviceClient edgeDeviceClient;

    @CircuitBreaker(name = "edgeDevice", fallbackMethod = "fallback")
    @Override
    public DeviceBinding resolveBinding(UUID deviceId) {
        try {
            EdgeDeviceClient.EdgeDeviceView view = edgeDeviceClient.getDevice(deviceId);
            if (view == null || view.pacienteId() == null) {
                log.debug("Dispositivo {} sem paciente pareado", deviceId);
                return DeviceBinding.unpaired(view != null ? view.tenantRef() : null);
            }
            return DeviceBinding.of(view.tenantRef(), view.pacienteId());
        } catch (FeignException.NotFound e) {
            // O EDGE respondeu, e a resposta é "não conheço esse dispositivo". Não é queda.
            log.debug("Dispositivo {} desconhecido no EDGE", deviceId);
            return DeviceBinding.unpaired(null);
        }
        // Qualquer outra FeignException PROPAGA de proposito: e o fallbackMethod que a converte em
        // unknown(). Capturar aqui devolveria um valor normal, o breaker registraria sucesso e
        // nunca abriria — um breaker decorativo, que e a armadilha que CLAUDE.md descreve.
    }

    /**
     * Um 4xx que nao seja 404 quase nunca e o EDGE caindo — e authority faltando, tenant errado, ou
     * contrato divergente. Continua degradando (o aparelho esta na casa do paciente), mas com um
     * log distinto e em ERROR: sem isso, um 403 de configuracao fica indistinguivel de uma queda,
     * que foi exatamente o defeito C-19.
     */
    @SuppressWarnings("unused") // invocado por nome pelo resilience4j
    private DeviceBinding fallback(UUID deviceId, Throwable cause) {
        if (cause instanceof FeignException fe && fe.status() >= 400 && fe.status() < 500) {
            log.error(
                    "EDGE recusou o dispositivo {} com HTTP {} — isto NAO e uma queda do EDGE."
                            + " Verifique a authority edge.read na identidade de servico do GEST.",
                    deviceId,
                    fe.status());
        } else {
            log.warn(
                    "EDGE indisponivel para o dispositivo {}: {}",
                    deviceId,
                    cause.getClass().getSimpleName());
        }
        return DeviceBinding.unknown();
    }
}
