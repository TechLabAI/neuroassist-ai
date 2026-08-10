package com.neuroassist.evnt.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@Profile("!poc")
@FeignClient(name = "hist-service", url = "${evnt.hist.baseurl:http://localhost:8087}")
public interface AuditTrailClient {

    // TODO: add methods from techlab-ai-integration.md
}
