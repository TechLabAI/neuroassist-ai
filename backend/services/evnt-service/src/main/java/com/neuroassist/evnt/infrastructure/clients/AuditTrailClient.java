package com.neuroassist.evnt.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "hist-service", url = "${evnt.hist.baseurl}")
public interface AuditTrailClient {

    // TODO: add methods from techlab-ai-integration.md
}
