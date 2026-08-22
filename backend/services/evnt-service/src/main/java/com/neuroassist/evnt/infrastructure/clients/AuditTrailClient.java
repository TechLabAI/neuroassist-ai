package com.neuroassist.evnt.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Audit-fact writer into hist-service.
 *
 * <p>Was an empty stub with a default url on port 8087 — that is edge-service. The port it should
 * have served was implemented by {@code AuditTrailNoOpAdapter}, a literal no-op, so AUD-06 and
 * AUD-07 were absent features: nothing evnt-service did ever reached the audit trail.
 *
 * <p>Mirrors {@code com.neuroassist.hist.api.controller.RecordAuditFactController} and {@code
 * RecordAuditFactRequest} verbatim. Two contract details that are easy to get wrong and that
 * pac-service already paid for (defects C-5 and C-6):
 *
 * <ul>
 *   <li><b>Idempotency is the {@code factId} in the body</b>, not an {@code Idempotency-Key}
 *       header. A replay returns 200 with the original record instead of 201.
 *   <li><b>{@code factType} is a controlled vocabulary</b> configured in {@code
 *       hist.fact-types.allowed}; anything outside it is a 422. {@code evento.criado} had to be
 *       added there for this client to work at all.
 * </ul>
 *
 * <p>hist-service takes the tenant exclusively from the {@code tenant} claim of the JWT and answers
 * 403 if the optional {@code empresaId} field contradicts it — so this client omits that field and
 * lets the service-identity token, which is minted per tenant, be the single source.
 *
 * <p>Guarded by {@code SCOPE_hist:write}; the ACS-side authority is written {@code hist:write} and
 * hist-service applies the {@code SCOPE_} prefix itself. Deliberately not granted to any caregiver
 * role — audit facts are written by services, not by people.
 */
@FeignClient(name = "hist-audit-client", url = "${evnt.hist.baseurl:http://hist-service:8089}")
public interface AuditTrailClient {

    @PostMapping("/api/v1/hist/audit-facts")
    RecordAuditFactResponse recordFact(@RequestBody RecordAuditFactRequest request);

    /**
     * Mirrors {@code RecordAuditFactRequest.SubjectRefDto} — {@code type} is one of {@code paciente
     * | comando | cuidador | dispositivo}.
     */
    record SubjectRefDto(String type, UUID id) {}

    /** Mirrors {@code RecordAuditFactRequest.ActorRefDto}. */
    record ActorRefDto(UUID caregiverId, String displayNameSnapshot) {}

    /**
     * Mirrors {@code RecordAuditFactRequest}. {@code payload} must not carry biometric keys ({@code
     * landmarks}, {@code image}, {@code frame}, {@code video}, {@code embedding}) — 422.
     */
    record RecordAuditFactRequest(
            UUID factId,
            String factType,
            SubjectRefDto subject,
            ActorRefDto actor,
            Map<String, Object> payload,
            Instant occurredAt) {}

    /** Subset of {@code RecordAuditFactResponse} — the hash-chain position of the stored fact. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RecordAuditFactResponse(
            UUID id, UUID factId, String factType, long sequenceNo, String recordHash) {}
}
