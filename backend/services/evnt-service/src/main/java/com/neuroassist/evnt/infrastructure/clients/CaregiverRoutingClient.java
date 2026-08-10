package com.neuroassist.evnt.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@Profile("!poc")
@FeignClient(name = "acs-service", url = "${evnt.acs.baseurl:http://localhost:8084}")
public interface CaregiverRoutingClient {

    // TODO: add methods from techlab-ai-integration.md
}
