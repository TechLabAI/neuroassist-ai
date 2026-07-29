package com.neuroassist.pac.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "hist-service", url = "${neuroassist.integration.hist.url}")
public interface HistFactClient {

    // TODO: add methods from techlab-ai-integration.md
}
