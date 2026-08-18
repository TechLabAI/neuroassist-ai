package com.neuroassist.dash.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "pac-service", url = "${dash.pac.base-url}")
public interface PacClient {

    // TODO: add methods from techlab-ai-integration.md
}
