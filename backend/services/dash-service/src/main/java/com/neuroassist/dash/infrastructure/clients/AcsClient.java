package com.neuroassist.dash.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Cliente Feign para o directory do acs-service — quais pacientes um cuidador enxerga. RULE-06:
 * FeignClient apenas em infrastructure.clients.
 *
 * <p><b>Path e forma da resposta estavam ambos errados</b> (achado ao validar E2E-018 contra o
 * stack real). Este cliente pedia {@code GET /api/v1/caregivers/{id}/patients} devolvendo {@code
 * List<String>}; o acs-service expõe {@code GET
 * /api/v1/acs/directory/caregivers/{id}/patient-scope} devolvendo {@code
 * PatientScopeResponse(cuidadorId, pacienteIds)}. O painel respondia **404** com {@code "No static
 * resource api/v1/caregivers/..."} — ou seja, {@code GET /panel/summary} nunca funcionou contra um
 * ACS de verdade.
 *
 * <p>É a 17ª ocorrência do padrão catalogado em {@code plan-e2e-integration.md} §11: o consumidor
 * inventa o contrato do produtor, e o mock do teste concorda com a invenção. Aqui o mock do BDD
 * stubava o path inventado, então o gate do DASH ficou verde o tempo todo.
 *
 * <p>Escrito agora a partir de {@code DirectoryController} e {@code
 * DirectoryResponse.PatientScopeResponse}, lidos no produtor.
 */
@FeignClient(name = "acs-service", url = "${dash.acs.base-url:}", contextId = "acsClient")
public interface AcsClient {

    @GetMapping("/api/v1/acs/directory/caregivers/{caregiverId}/patient-scope")
    PatientScopeResponse getPatientScope(@PathVariable("caregiverId") String caregiverId);

    /**
     * Espelha {@code DirectoryResponse.PatientScopeResponse}.
     *
     * <p>Os ids vêm como {@code String}, e não {@code UUID} como no produtor: no fio um UUID é uma
     * string JSON, e o DASH trata identificador de paciente como opaco de ponta a ponta ({@code
     * IdentityDirectoryPort} devolve {@code List<String>}). Declarar {@code UUID} aqui só
     * adicionaria uma conversão — e um modo de falha — num serviço que nunca interpreta o valor.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PatientScopeResponse(String cuidadorId, List<String> pacienteIds) {}
}
