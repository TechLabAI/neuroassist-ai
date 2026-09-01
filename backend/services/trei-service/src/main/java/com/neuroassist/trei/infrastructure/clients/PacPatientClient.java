package com.neuroassist.trei.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "pac-service", url = "${neuroassist.pac.url}")
public interface PacPatientClient {

    // TODO[SCAFFOLD]: add methods from techlab-ai-integration.md
}
