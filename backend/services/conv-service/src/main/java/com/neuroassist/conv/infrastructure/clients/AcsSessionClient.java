package com.neuroassist.conv.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "acs-service", url = "${neuroassist.acs.url}")
public interface AcsSessionClient {

    // TODO[SCAFFOLD]: add methods from techlab-ai-integration.md
}
