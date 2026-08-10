package com.neuroassist.evnt.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@Profile("!poc")
@FeignClient(name = "dash-service", url = "${evnt.dash.baseurl:http://localhost:8086}")
public interface NotificationDispatchClient {

    // TODO: add methods from techlab-ai-integration.md
}
