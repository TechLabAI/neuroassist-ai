package com.neuroassist.evnt.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "dash-service", url = "${evnt.dash.baseurl}")
public interface NotificationDispatchClient {

    // TODO: add methods from techlab-ai-integration.md
}
