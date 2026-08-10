package com.neuroassist.evnt.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@Profile("!poc")
@FeignClient(name = "pac-service", url = "${evnt.pac.baseurl:http://localhost:8081}")
public interface CommandLookupClient {

    // TODO: add methods from techlab-ai-integration.md
}
