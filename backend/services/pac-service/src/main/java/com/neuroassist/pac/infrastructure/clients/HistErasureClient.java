package com.neuroassist.pac.infrastructure.clients;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign client for hist-service's LGPD erasure surface (Flow-04 / E2E-012).
 *
 * <p>Mirrors {@code com.neuroassist.hist.api.controller.PatientDataErasureController} verbatim. Two
 * details that differ from every other hist-service call and are easy to get wrong:
 *
 * <ul>
 *   <li>the tenant travels in an <b>explicit {@code X-Tenant-Id} header</b>, not in the JWT claim —
 *       this is the only hist-service endpoint that requires it;
 *   <li>it is guarded by {@code SCOPE_hist:erase}, an authority distinct from {@code hist:write}.
 * </ul>
 *
 * <p><b>Service identity.</b> Uses {@link HistFeignConfig}, so the call authenticates as
 * pac-service. Erasure is the service acting on its own account while executing a request; the
 * requesting caregiver is authorised at pac-service's own boundary.
 */
@FeignClient(
        name = "hist-erasure-client",
        url = "${neuroassist.integration.hist.url:http://hist-service:8089}",
        configuration = HistFeignConfig.class)
public interface HistErasureClient {

    @DeleteMapping("/api/v1/hist/patients/{subjectId}/personal-data")
    void erasePersonalData(
            @PathVariable("subjectId") UUID subjectId,
            @RequestHeader("X-Tenant-Id") String tenantId);
}
