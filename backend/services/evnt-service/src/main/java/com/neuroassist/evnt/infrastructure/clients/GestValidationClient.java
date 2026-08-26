package com.neuroassist.evnt.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Validates the gesture primitives carried by a report against gest-service.
 *
 * <p><b>This client was wrong in three ways at once</b>, and none was visible to the evnt-service
 * gates because {@code GestValidationAdapter} was only ever exercised against a mock of this very
 * interface:
 *
 * <ul>
 *   <li>the path was {@code POST /api/v1/recognized-commands}, which gest-service does not expose —
 *       its controllers all live under {@code /gest-service/**};
 *   <li>the default url pointed at port 8082, which is pac-service;
 *   <li>the request and response shapes were invented rather than mirrored.
 * </ul>
 *
 * <p>It sits on the fail-closed leg of {@code ReportIngressService}, so in an integrated system no
 * gesture report could ever become an Evento.
 *
 * <p><b>Why per-primitive lookup and not {@code POST /gest-service/eligibility}.</b> The
 * eligibility endpoint takes an {@code urgencyLevel} and answers whether the <i>tenant's</i>
 * primitive catalogue is adequate for it — it structurally cannot answer "is this primitive, the
 * one this report claims to have recognised, known?". pac-service hit the same wall and resolved it
 * the same way (defect C-7 of the E2E plan): {@code GET /gest-service/tipo-gesto/&#123;id&#125;},
 * where a 404 means the primitive is unknown to GEST. This client mirrors that precedent rather
 * than inventing a fourth contract.
 *
 * <p>Requires the {@code gest.tipo-gesto.read} authority, which evnt-service holds through its
 * service identity.
 */
@FeignClient(name = "gest-validation-client", url = "${evnt.gest.baseurl:http://gest-service:8084}")
public interface GestValidationClient {

    /**
     * Returns the primitive, or throws {@code FeignException.NotFound} when gest-service does not
     * know it.
     */
    @GetMapping("/gest-service/tipo-gesto/{id}")
    TipoGestoResponse findPrimitive(@PathVariable("id") UUID id);

    /**
     * Mirrors {@code com.neuroassist.gest.api.dto.TipoGestoResponse}. {@code public} because JDK
     * dynamic proxies cannot reach package-private types; unknown fields are ignored so that a
     * gest-service which grows the response does not break this consumer.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TipoGestoResponse(
            UUID id, String nome, String detectabilityState, String engineFeatureRef) {}
}
