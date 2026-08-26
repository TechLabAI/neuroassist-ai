package com.neuroassist.edge.infrastructure.clients;

import com.neuroassist.edge.application.port.out.GetFullDetectionProfilePort;
import com.neuroassist.edge.infrastructure.security.ServiceTokenUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Feign-based implementation of {@link GetFullDetectionProfilePort} (EDGE-022, P1).
 *
 * <p>Fetches the complete detection profile for the browser station via {@link
 * DetectionProfileClient} (service identity — GEST requires {@code gest.detection-profile.read}).
 * The device token never reaches GEST; only the EDGE service identity does (principle of least
 * privilege, closes C-19).
 *
 * <p><b>NOT {@code @Primary}.</b> {@link DetectionProfileFeignAdapter} already carries
 * {@code @Primary} for {@link com.neuroassist.edge.application.port.out.DetectionProfilePort}. This
 * adapter implements a <em>different</em> port ({@link GetFullDetectionProfilePort}); there is
 * exactly one bean per port, so no {@code @Primary} is needed or correct here.
 *
 * <p><b>Circuit breaker {@code edge-device-profile}</b> is separate from the heartbeat breaker
 * {@code gest-detection-profile}. A degraded station (profile fetch failing) must not degrade the
 * heartbeat circuit, and vice-versa.
 *
 * <p><b>What this adapter considers a failure (opens the breaker):</b>
 *
 * <ul>
 *   <li>Connection timeout / 5xx from GEST — genuine unavailability;
 * </ul>
 *
 * <p><b>What is ignored (does not open the breaker):</b>
 *
 * <ul>
 *   <li>{@code FeignException.NotFound} (404) — GEST answered "no profile for this device". Not a
 *       GEST failure; mapped to {@link GetFullDetectionProfilePort.ProfileResult#degraded()} so the
 *       service can surface an explicit error;
 *   <li>{@code FeignException.Forbidden} (403) — authorisation issue (should not occur with a
 *       service token; if it does, it is a configuration defect, not an outage). Converting a 403
 *       to "service degraded" is the exact shape of C-19 — it makes an auth bug look like an outage
 *       and triggers the circuit breaker against a healthy GEST.
 * </ul>
 *
 * <p><b>ServiceTokenUnavailableException is rethrown,</b> never swallowed: a missing service
 * identity is a configuration defect, not a transient outage. Swallowing it would produce the same
 * silent degradation that caused C-19.
 *
 * <p>RULE-05: this class does not import from {@code api.*}. RULE-06: {@code @FeignClient} only in
 * {@code infrastructure.clients} — satisfied; this class only <em>uses</em> the client.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceProfileFeignAdapter implements GetFullDetectionProfilePort {

    private final DetectionProfileClient detectionProfileClient;

    @Override
    @CircuitBreaker(name = "edge-device-profile", fallbackMethod = "fallback")
    public ProfileResult getProfileForDevice(UUID deviceId, String ifNoneMatch) {
        ResponseEntity<GestDetectionProfileView> response;
        try {
            response = detectionProfileClient.getProfileForDeviceConditional(deviceId, ifNoneMatch);
        } catch (FeignException e) {
            return interpretarFalha(deviceId, e);
        }

        HttpStatus status = HttpStatus.valueOf(response.getStatusCode().value());

        if (status == HttpStatus.OK && response.getBody() != null) {
            GestDetectionProfileView view = response.getBody();
            log.debug(
                    "GEST profile v={} for deviceId='{}', pacienteId='{}'",
                    view.version(),
                    deviceId,
                    view.pacienteId());
            return ProfileResult.ok(mapToProfileData(view));
        }

        log.warn(
                "GEST: unexpected status {} for deviceId='{}'. Returning degraded.",
                status,
                deviceId);
        return ProfileResult.degraded();
    }

    /**
     * Traduz a exceção do Feign para um {@link ProfileResult}.
     *
     * <p><b>O 304 chega AQUI, e não como {@code ResponseEntity}.</b> O decodificador de erro padrão
     * do Feign é invocado para tudo que não seja 2xx — 3xx incluído. A primeira versão deste
     * adaptador testava {@code response.getStatusCode() == NOT_MODIFIED} no retorno: código morto,
     * porque a chamada lança antes. A exceção caía no fallback do circuit breaker e virava 502 —
     * uma revalidação de cache bem-sucedida indistinguível de GEST fora do ar, que é exatamente o
     * cenário BDD que reprovava.
     *
     * <p>Não existe {@code FeignException.NotModified} nesta versão do Feign (conferido no jar),
     * por isso a discriminação é por {@link FeignException#status()}.
     */
    private ProfileResult interpretarFalha(UUID deviceId, FeignException e) {
        if (e.status() == HttpStatus.NOT_MODIFIED.value()) {
            log.debug("GEST returned 304 for deviceId='{}' — ETag matched, no update.", deviceId);
            return ProfileResult.notModified();
        }

        if (e.status() == HttpStatus.NOT_FOUND.value()) {
            log.warn(
                    "GEST returned 404 for deviceId='{}' — no profile for this device in GEST."
                            + " Returning degraded so the service surfaces an explicit error.",
                    deviceId);
            return ProfileResult.degraded();
        }

        if (e.status() == HttpStatus.FORBIDDEN.value()) {
            // 403 = defeito de configuração da identidade de serviço — relançado para ficar visível
            // no log e NÃO contar como indisponibilidade contra o circuit breaker.
            log.error(
                    "GEST returned 403 for deviceId='{}' — service identity may lack"
                            + " gest.detection-profile.read. This is a configuration defect.",
                    deviceId);
            throw e;
        }

        throw e;
    }

    /**
     * Fallback invoked by Resilience4j when the circuit opens or a non-ignored exception escapes.
     *
     * <p>{@link ServiceTokenUnavailableException} is rethrown because it indicates a configuration
     * defect (no service identity) — not a transient GEST outage. Swallowing it would silently
     * return a degraded result and mask the misconfiguration.
     */
    @SuppressWarnings("unused")
    ProfileResult fallback(UUID deviceId, String ifNoneMatch, Throwable cause) {
        if (cause instanceof ServiceTokenUnavailableException stue) {
            log.error(
                    "Service identity unavailable for GEST profile call (deviceId='{}'): {}",
                    deviceId,
                    stue.getMessage());
            throw stue;
        }
        log.warn(
                "GEST unreachable — profile request degraded for deviceId='{}': {}",
                deviceId,
                cause.getMessage());
        return ProfileResult.degraded();
    }

    // -------------------------------------------------------------------------
    // Mapping: GestDetectionProfileView → port data records
    // GestDetectionProfileView stays exclusively in infrastructure.clients
    // -------------------------------------------------------------------------

    private static ProfileData mapToProfileData(GestDetectionProfileView view) {
        return new ProfileData(
                view.pacienteId(),
                view.version(),
                view.catalogoDegradado(),
                view.etag(),
                mapThresholds(view.thresholds()),
                mapPrimitives(view.primitives()));
    }

    private static ThresholdTableData mapThresholds(
            GestDetectionProfileView.GestDetectionThresholdTableView src) {
        if (src == null) {
            return null;
        }
        return new ThresholdTableData(
                mapBand(src.baixa()), mapBand(src.media()), mapBand(src.alta()));
    }

    private static BandData mapBand(GestDetectionProfileView.GestDetectionBandView src) {
        if (src == null) {
            return null;
        }
        return new BandData(
                src.earClose(),
                src.earOpen(),
                src.earOtherMax(),
                src.blinkMinFrames(),
                src.doubleBlinkWindowMs(),
                src.briRaiseOn(),
                src.briRaiseOff(),
                src.briOtherMax(),
                src.browMinFrames());
    }

    private static List<PrimitiveData> mapPrimitives(
            List<GestDetectionProfileView.GestProfilePrimitiveView> src) {
        if (src == null) {
            return Collections.emptyList();
        }
        return src.stream()
                .map(
                        p ->
                                new PrimitiveData(
                                        p.tipoGestoId(),
                                        p.nome(),
                                        p.engineFeatureRef(),
                                        p.sensibilidade(),
                                        p.origem()))
                .toList();
    }
}
