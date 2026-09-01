package com.neuroassist.conv.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "hist-service", url = "${neuroassist.hist.url}")
public interface HistAuditClient {

    // TODO[SCAFFOLD]: add methods from techlab-ai-integration.md
}
