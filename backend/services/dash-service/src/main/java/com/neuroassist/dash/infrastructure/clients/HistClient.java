package com.neuroassist.dash.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "hist-service", url = "${dash.hist.base-url}")
public interface HistClient {

    // TODO: add methods from techlab-ai-integration.md
}
