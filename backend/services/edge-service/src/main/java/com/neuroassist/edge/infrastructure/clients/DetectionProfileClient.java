package com.neuroassist.edge.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "gest-service", url = "${neuroassist.gest.url}")
public interface DetectionProfileClient {

    // TODO: add methods from techlab-ai-integration.md
}
