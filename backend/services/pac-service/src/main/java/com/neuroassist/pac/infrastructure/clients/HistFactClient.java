package com.neuroassist.pac.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * INT-PAC-004 — Feign client for hist-service domain-fact recording.
 *
 * <p>RULE-06: {@code @FeignClient} declared only in {@code infrastructure.clients}.
 *
 * <p>Used by {@link HistFactAdapter} to attempt direct delivery of domain facts ({@code
 * comando.publicado}, {@code comando.recalibrado}, {@code comando.aposentado}). On failure, the
 * adapter falls back to the local outbox ({@link HistOutboxJpaRepository}).
 *
 * <p>The path and body follow hist-service's ingestion surface {@code POST
 * /api/v1/hist/audit-facts} (HIST-005) verbatim — see {@link HistFactRequest}.
 *
 * <p><b>Service identity.</b> Unlike the other PAC Feign clients, this one does NOT travel on the
 * caregiver's token: {@link HistFeignConfig} swaps the {@code Authorization} header for
 * pac-service's own service-identity token ({@code hist:write}), so the {@code CUIDADORA} role no
 * longer needs that authority. The caregiver remains the recorded <i>actor</i> inside the fact
 * body.
 */
@FeignClient(
        name = "hist-service",
        url = "${neuroassist.integration.hist.url:http://hist-service:8089}",
        configuration = HistFeignConfig.class)
public interface HistFactClient {

    /**
     * Records a single domain fact in hist-service.
     *
     * @param request the domain fact payload
     */
    @PostMapping("/api/v1/hist/audit-facts")
    void recordFact(@RequestBody HistFactRequest request);
}
