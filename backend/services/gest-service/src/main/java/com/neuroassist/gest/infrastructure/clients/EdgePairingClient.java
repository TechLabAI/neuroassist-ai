package com.neuroassist.gest.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "", url = "${}")
public interface EdgePairingClient {

    // TODO: add methods from techlab-ai-integration.md
}
