package com.neuroassist.dash.infrastructure.clients;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client para o ACS (Cuidadores e Acesso). Resolve a lista de pacientes vinculados a um
 * cuidador. RULE-06: FeignClient apenas em infrastructure.clients.
 */
@FeignClient(name = "acs-service", url = "${dash.acs.base-url:}", contextId = "acsClient")
public interface AcsClient {

    @GetMapping("/api/v1/caregivers/{caregiverId}/patients")
    List<String> getPatientsForCaregiver(@PathVariable("caregiverId") String caregiverId);
}
