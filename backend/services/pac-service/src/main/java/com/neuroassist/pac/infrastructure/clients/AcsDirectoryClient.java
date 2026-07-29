package com.neuroassist.pac.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "acs-service", url = "${neuroassist.integration.acs.url}")
public interface AcsDirectoryClient {

    // TODO: add methods from techlab-ai-integration.md
}
