package com.neuroassist.hist.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "acs-service", url = "${neuroassist.acs.url   # resolve caregiver->patient scope on caregiver-scoped reads}")
public interface AcsActorDirectoryClient {

    // TODO: add methods from techlab-ai-integration.md
}
