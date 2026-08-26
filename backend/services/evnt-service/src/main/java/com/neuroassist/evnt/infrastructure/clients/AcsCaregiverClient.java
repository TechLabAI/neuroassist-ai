package com.neuroassist.evnt.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Cadastro de cuidadores do tenant, no acs-service — a origem do grau 3 da escada (ADR-011 §1).
 *
 * <p>A coordenadora da clínica responde pelo tenant, não por um paciente, então ela não aparece em
 * {@code /directory/patients/&#123;id&#125;/caregivers} a menos que também esteja vinculada àquele
 * paciente. Este endpoint já existia no acs-service; nenhum endpoint novo foi criado.
 *
 * <p><b>Espelha {@code com.neuroassist.acs.api.controller.CaregiverController#listByTenant} e
 * {@code com.neuroassist.acs.api.dto.CaregiverResponse} campo por campo</b> — caminho, nome do
 * parâmetro de query e nomes dos campos conferidos no produtor, não supostos. Escrever um cliente a
 * partir da suposição do consumidor é a armadilha nº 1 deste repositório, e um stub de teste
 * escrito da mesma suposição faz o teste passar exatamente quando a suposição está errada.
 *
 * <p><b>Autoridade exigida pelo produtor: {@code acs.cuidador.read}</b> — e o cliente de serviço
 * {@code evnt-service} <b>não a possui hoje</b> (ele tem {@code acs.directory.read}). Enquanto isso
 * não for decidido, esta chamada responde 403, o adapter degrada para lista vazia, e o grau 3 só é
 * povoado por uma coordenadora que também esteja vinculada ao paciente. Registrado como lacuna
 * aberta na E2E-020.
 *
 * <p>{@code public record} de propósito: um record package-private não é acessível ao proxy
 * dinâmico do JDK que implementa o cliente.
 */
@FeignClient(name = "acs-caregiver-client", url = "${evnt.acs.baseurl:http://acs-service:8088}")
public interface AcsCaregiverClient {

    /** {@code GET /api/v1/acs/caregivers?tenantId=} — exige {@code acs.cuidador.read}. */
    @GetMapping("/api/v1/acs/caregivers")
    List<CaregiverResponse> listByTenant(@RequestParam("tenantId") UUID tenantId);

    /** Espelha {@code CaregiverResponse}. Sem credencial nem senha — o produtor não os expõe. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CaregiverResponse(
            UUID id,
            UUID tenant,
            String nome,
            String login,
            String papel,
            String status,
            Instant criadoEm) {

        /** {@code CuidadorStatus.ACTIVE} — inativa não recebe alarme. */
        public static final String ACTIVE = "ACTIVE";
    }
}
