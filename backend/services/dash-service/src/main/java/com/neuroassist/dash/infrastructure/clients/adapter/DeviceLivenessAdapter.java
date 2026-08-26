package com.neuroassist.dash.infrastructure.clients.adapter;

import com.neuroassist.dash.application.port.out.DeviceLivenessPort;
import com.neuroassist.dash.domain.model.DeviceStatus;
import com.neuroassist.dash.infrastructure.clients.EdgeClient;
import com.neuroassist.dash.infrastructure.clients.dto.DeviceStatusResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter outbound: implementa {@link DeviceLivenessPort} delegando ao {@link EdgeClient}.
 *
 * <p>EDGE é dependência <b>soft</b> (ADR-004): o painel abre mesmo sem ela.
 *
 * <p><b>Três estados, não dois</b> (E2E-018). A versão anterior tinha um {@code catch (Exception)}
 * que transformava qualquer falha em {@code degraded=true, status="unknown"} — inclusive o 404 que
 * o EDGE responde quando o paciente simplesmente <i>não tem dispositivo</i>. "Não há dispositivo" e
 * "não consegui falar com o EDGE" viravam a mesma resposta, e a cuidadora não tinha como saber se
 * estava vendo ausência de equipamento ou ausência de informação.
 *
 * <p>É o mesmo defeito que o EDGE pagou no ponteiro de perfil (C-19/HB-04) e corrigiu do mesmo
 * jeito: separar "o produtor respondeu que não há" de "o produtor não respondeu".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeviceLivenessAdapter implements DeviceLivenessPort {

    /** O EDGE não respondeu. Não sabemos o estado do dispositivo. */
    private static final String STATUS_UNKNOWN = "unknown";

    /** O EDGE respondeu que este paciente não tem dispositivo pareado. Informação, não falha. */
    private static final String STATUS_NO_DEVICE = "no-device";

    private final EdgeClient edgeClient;

    @Override
    @CircuitBreaker(name = "edge-service", fallbackMethod = "edgeUnreachable")
    public DeviceStatus getDeviceStatus(String patientId) {
        log.debug("Consultando EDGE: liveness do dispositivo do paciente [{}]", patientId);
        try {
            DeviceStatusResponse response = edgeClient.getDeviceLiveness(patientId);
            // `degraded` é um conceito DESTE serviço — significa "não consegui falar com o EDGE",
            // e não algo que o EDGE reporte. O status vem íntegro do produtor (C-34).
            return new DeviceStatus(response.deviceId(), response.status(), false);
        } catch (feign.FeignException.NotFound ex) {
            // O EDGE respondeu, e a resposta é "não há dispositivo". degraded=false de propósito:
            // não há nada degradado aqui, há um paciente sem equipamento pareado.
            log.debug("EDGE: paciente [{}] não tem dispositivo pareado", patientId);
            return new DeviceStatus(null, STATUS_NO_DEVICE, false);
        }
    }

    /**
     * Chamado quando o EDGE está inalcançável ou com o breaker aberto.
     *
     * <p>Não é chamado em 4xx: {@code ignoreExceptions} na configuração deixa a família {@code
     * FeignClientException} passar intacta, para que um 403 nunca vire "degradado".
     */
    @SuppressWarnings("unused") // referenciado por nome em @CircuitBreaker
    private DeviceStatus edgeUnreachable(String patientId, Throwable cause) {
        log.warn(
                "EDGE indisponivel para paciente [{}] — status desconhecido: {}",
                patientId,
                cause.getMessage());
        return new DeviceStatus(null, STATUS_UNKNOWN, true);
    }
}
