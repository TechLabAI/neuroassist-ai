package com.neuroassist.gest.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Profile;

/**
 * Feign client for the EDGE pairing service.
 * Disabled in poc profile — replaced by EdgePairingPortStub.
 */
@Profile("!poc")
@FeignClient(name = "edge-pairing-client", url = "${edge.pairing.url:http://localhost:8090}")
public interface EdgePairingClient {

    // TODO: add methods from techlab-ai-integration.md
}
