package com.neuroassist.evnt.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "gest-service", url = "${evnt.gest.baseurl}")
public interface GestValidationClient {

    // TODO: add methods from techlab-ai-integration.md
}
