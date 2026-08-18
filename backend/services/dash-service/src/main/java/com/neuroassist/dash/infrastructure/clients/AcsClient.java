package com.neuroassist.dash.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "acs-service", url = "${dash.acs.base-url}")
public interface AcsClient {

    // TODO: add methods from techlab-ai-integration.md
}
