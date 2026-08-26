package com.neuroassist.evnt.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Directory lookups against acs-service.
 *
 * <p>This interface was an empty stub ({@code // TODO: add methods from techlab-ai-integration.md})
 * whose default url pointed at port 8084 — gest-service. The port that should have used it was
 * served instead by {@code CaregiverRoutingHardcodedAdapter}, which returned the literal string
 * {@code "cuidadora-jamilly-fixo"} from production code. DIR-02 was therefore not a failing
 * integration but an absent feature.
 *
 * <p>Mirrors {@code com.neuroassist.acs.api.controller.DirectoryController} verbatim. Both
 * endpoints are guarded by {@code acs.directory.read}, which evnt-service holds through its service
 * identity.
 */
@FeignClient(name = "acs-directory-client", url = "${evnt.acs.baseurl:http://acs-service:8088}")
public interface CaregiverRoutingClient {

    /**
     * Caregivers responsible for a patient — the routing question asked when a report becomes an
     * Evento. acs-service serves this with {@code Cache-Control: max-age=60}.
     */
    @GetMapping("/api/v1/acs/directory/patients/{id}/caregivers")
    PatientCaregiversResponse findCaregiversForPatient(@PathVariable("id") UUID pacienteId);

    /**
     * Patients a caregiver is responsible for — used to check that whoever confirms an Evento is a
     * caregiver acs-service actually knows (EVNT-023).
     */
    @GetMapping("/api/v1/acs/directory/caregivers/{id}/patient-scope")
    PatientScopeResponse findPatientScope(@PathVariable("id") UUID cuidadorId);

    /** Mirrors {@code DirectoryResponse.ResponsibleCaregiver}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResponsibleCaregiver(UUID cuidadorId, String papel, List<String> authorities) {}

    /** Mirrors {@code PatientCaregiversResponse}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PatientCaregiversResponse(
            UUID pacienteId, List<ResponsibleCaregiver> responsibleCaregivers) {}

    /** Mirrors {@code PatientScopeResponse}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PatientScopeResponse(UUID cuidadorId, List<UUID> pacienteIds) {}
}
