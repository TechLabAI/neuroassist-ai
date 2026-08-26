package com.neuroassist.evnt.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Command snapshot lookup against pac-service.
 *
 * <p>Was an empty stub with a default url on port 8081 (nothing listens there). The port it should
 * have served was implemented by {@code CommandLookupHardcodedAdapter}, which returned a fixed
 * {@code "Preciso de ajuda"} / {@code EMERGENCIA} snapshot from production code — so every alarm in
 * an integrated system would have carried the same text and the same urgency regardless of which
 * command the patient actually triggered.
 *
 * <p><b>The path requires the patient id.</b> pac-service exposes commands only under {@code
 * /api/pac/v1/pacientes/&#123;pacienteId&#125;/comandos/&#123;id&#125;} — there is no lookup by
 * command id alone. That is why {@code CommandLookupPort} takes both identifiers; the report
 * carries them.
 *
 * <p>Guarded by {@code pac.comando-lifecycle.read}, held through evnt-service's service identity.
 */
@FeignClient(name = "pac-command-client", url = "${evnt.pac.baseurl:http://pac-service:8082}")
public interface CommandLookupClient {

    @GetMapping("/api/pac/v1/pacientes/{pacienteId}/comandos/{comandoId}")
    ComandoResponse findCommand(
            @PathVariable("pacienteId") UUID pacienteId, @PathVariable("comandoId") UUID comandoId);

    /**
     * Subset of {@code com.neuroassist.pac.api.dto.ComandoAuthoringResponse} that the alarm needs:
     * the label shown to the caregiver and the urgency the notification tier is derived from.
     * pac-service returns many more fields; they are ignored.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ComandoResponse(
            UUID id,
            UUID pacienteId,
            String rotulo,
            String necessidade,
            String urgencia,
            String estado) {}
}
