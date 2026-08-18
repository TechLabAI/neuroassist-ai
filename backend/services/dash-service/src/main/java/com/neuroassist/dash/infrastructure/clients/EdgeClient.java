package com.neuroassist.dash.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "edge-service", url = "${dash.edge.base-url}")
public interface EdgeClient {

    // TODO: add methods from techlab-ai-integration.md
}
