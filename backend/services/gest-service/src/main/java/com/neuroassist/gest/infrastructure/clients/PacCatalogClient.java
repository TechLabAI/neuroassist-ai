package com.neuroassist.gest.infrastructure.clients;

import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Cliente do catálogo ativo do PAC — de onde vem a sensibilidade calibrada para cada gesto do
 * paciente (E2E-034).
 *
 * <p>O javadoc do DTO do PAC já dizia a intenção — <i>"GEST polls this endpoint with If-None-Match
 * to detect changes"</i> — mas o GEST nunca teve um cliente do PAC. Este é o primeiro.
 *
 * <p>Caminho e forma copiados do {@code CatalogoAtivoController} e do {@code CatalogoAtivoResponse}
 * do produtor, não da suposição do consumidor: escrever o cliente pelo que o consumidor imagina, e
 * depois stubar o WireMock com a mesma suposição, faz o teste passar exatamente quando a suposição
 * está errada (Sessão 2, 10 dos 16 defeitos).
 *
 * <p>Autenticado com a identidade de serviço do GEST — exige {@code pac.catalogo-ativo.read}.
 */
@FeignClient(
        name = "pac-catalog-client",
        url = "${neuroassist.pac.url:${PAC_SERVICE_URL:http://pac-service:8082}}",
        configuration = ServiceIdentityFeignConfig.class)
public interface PacCatalogClient {

    /**
     * @param empresaId o PAC exige {@code X-Empresa-Id} e responde 400 {@code missing-tenant} sem
     *     ele, mesmo com um {@code tenant_id} válido no token. O interceptor global do GEST manda
     *     {@code X-Tenant-Id} — outro nome para o mesmo dado. É o shim de tenant catalogado em
     *     E2E-021 (3 claims + 2 headers); aqui só se fala a língua do produtor, sem tentar unificar
     *     os dois nomes, que é trabalho de outra issue.
     */
    @GetMapping("/api/pac/v1/pacientes/{pacienteId}/catalogo-ativo")
    CatalogoAtivoView getCatalogoAtivo(
            @PathVariable("pacienteId") UUID pacienteId,
            @RequestHeader("X-Empresa-Id") String empresaId);

    /** Espelha {@code CatalogoAtivoResponse}. Só os campos que a projeção usa. */
    record CatalogoAtivoView(UUID pacienteId, int catalogoVersao, List<ComandoView> comandos) {}

    /**
     * Espelha {@code CatalogoAtivoResponse.ComandoSummary}.
     *
     * <p>{@code primitivoPrincipalId} é o {@code tipoGestoId} do GEST — é por ele que a junção
     * acontece. {@code sensibilidade} chega como texto ({@code baixa|media|alta}) e é convertida no
     * adapter: uma faixa desconhecida não pode virar exceção no caminho de leitura do dispositivo.
     */
    record ComandoView(
            UUID id, UUID primitivoPrincipalId, String sensibilidade, int calibracaoVersao) {}
}
