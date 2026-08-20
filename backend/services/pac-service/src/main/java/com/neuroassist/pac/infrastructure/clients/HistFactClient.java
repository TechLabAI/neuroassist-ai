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
 * comando.published}, {@code comando.recalibrated}, {@code comando.retired}). On failure, the
 * adapter falls back to the local outbox ({@link HistOutboxJpaRepository}).
 */
@FeignClient(
        name = "hist-service",
        url = "${neuroassist.integration.hist.url:http://hist-service:8089}")
public interface HistFactClient {

    /**
     * Records a single domain fact in hist-service.
     *
     * @param request the domain fact payload
     */
    @PostMapping("/hist-service/api/v1/facts")
    void recordFact(@RequestBody HistFactRequest request);
}
