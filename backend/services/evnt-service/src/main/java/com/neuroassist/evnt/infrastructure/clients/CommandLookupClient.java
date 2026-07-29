package com.neuroassist.evnt.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "pac-service", url = "${evnt.pac.baseurl}")
public interface CommandLookupClient {

    // TODO: add methods from techlab-ai-integration.md
}
