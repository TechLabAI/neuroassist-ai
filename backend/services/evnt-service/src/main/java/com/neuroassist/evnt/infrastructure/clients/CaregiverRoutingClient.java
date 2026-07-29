package com.neuroassist.evnt.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "acs-service", url = "${evnt.acs.baseurl}")
public interface CaregiverRoutingClient {

    // TODO: add methods from techlab-ai-integration.md
}
