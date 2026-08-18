package com.neuroassist.dash.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "evnt-service", url = "${dash.evnt.base-url}")
public interface EvntClient {

    // TODO: add methods from techlab-ai-integration.md
}
