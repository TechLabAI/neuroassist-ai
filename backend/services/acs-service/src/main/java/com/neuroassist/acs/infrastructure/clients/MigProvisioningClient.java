package com.neuroassist.acs.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "neuroassist-ai-migrations", url = "${neuroassist.mig.url:}")
public interface MigProvisioningClient {

    // TODO: add methods from techlab-ai-integration.md
}
