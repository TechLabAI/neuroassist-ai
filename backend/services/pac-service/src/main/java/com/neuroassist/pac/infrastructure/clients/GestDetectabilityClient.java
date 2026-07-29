package com.neuroassist.pac.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "gest-service", url = "${neuroassist.integration.gest.url}")
public interface GestDetectabilityClient {

    // TODO: add methods from techlab-ai-integration.md
}
