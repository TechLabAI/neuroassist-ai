package com.neuroassist.pac.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "edge-service", url = "${neuroassist.integration.edge.url}")
public interface EdgeDeviceClient {

    // TODO: add methods from techlab-ai-integration.md
}
