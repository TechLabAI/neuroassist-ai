package com.neuroassist.evnt.infrastructure.clients;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "gest-service", url = "${evnt.gest.baseurl:http://localhost:8082}")
public interface GestValidationClient {

    @PostMapping("/api/v1/recognized-commands")
    ValidationResponse validate(@RequestBody ValidationRequest request);

    record PrimitiveRef(String tipoGestoId) {}

    record ValidationRequest(int profileVersion, List<PrimitiveRef> primitives) {}

    record ValidationResponse(boolean valid, String contractVersion) {}
}
