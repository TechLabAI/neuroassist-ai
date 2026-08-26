package com.neuroassist.dash.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Cliente Feign do pac-service. RULE-06: {@code @FeignClient} apenas em infrastructure.clients.
 *
 * <p><b>Os três caminhos anteriores não existiam.</b> Este cliente pedia {@code
 * /api/v1/patients/&#123;id&#125;/commands}, {@code /api/v1/patients/&#123;id&#125;/calibration} e
 * {@code POST /api/v1/patients/&#123;id&#125;/commands/&#123;cmd&#125;/calibrate}. O PAC serve
 * {@code /api/pac/v1/pacientes/...}. Verificado contra o PAC rodando: os dois primeiros respondem
 * <b>404 {@code "No static resource api/v1/patients/…"}</b> — ou seja, {@code GET
 * /panel/patients/&#123;id&#125;/overview} nunca funcionou uma única vez.
 *
 * <p>É a 19ª ocorrência do padrão catalogado em {@code plan-e2e-integration.md} §11: o consumidor
 * inventa o contrato do produtor e o mock do teste concorda com a invenção — e então o teste passa
 * <i>exatamente quando</i> a suposição está errada. Os stubs WireMock do BDD stubavam os caminhos
 * inventados, e o gate do DASH ficou verde o tempo todo.
 *
 * <p>Reescrito a partir de {@code PacienteController}, {@code CatalogoAtivoController}, {@code
 * ComandoLifecycleController} e dos records de resposta correspondentes, lidos no produtor e
 * conferidos com {@code curl} contra o serviço real.
 *
 * <p><b>Tenant.</b> O PAC exige o header de tenant e responde 400 {@code missing-tenant} sem ele. O
 * canônico é {@code X-Tenant-Id} (E2E-021) e o {@code feignTenantIdInterceptor} global do DASH já o
 * envia em toda chamada — por isso nenhum {@code @RequestHeader} aparece aqui.
 *
 * <p><b>Identidade.</b> Estas chamadas propagam o JWT do cuidador (o interceptor de identidade de
 * serviço do DASH aplica-se só ao evnt-service). As authorities exigidas — {@code
 * pac.paciente.read} e {@code pac.comando-lifecycle.recalibrar} — já estão nos papéis CUIDADORA e
 * COORDENADOR_CLINICA do {@code RoleAuthorityPolicy}; nada de novo precisou ser emitido.
 */
@FeignClient(name = "pac-service", url = "${dash.pac.base-url:}", contextId = "pacClient")
public interface PacClient {

    /**
     * Todos os pacientes do tenant — {@code GET /api/pac/v1/pacientes}, authority {@code
     * pac.paciente.read}.
     *
     * <p><b>Uma chamada, não N.</b> O painel precisa do nome de cada um dos pacientes da cuidadora.
     * Perguntar um a um seria um N+1 sobre HTTP no caminho mais quente do sistema; o PAC já expõe a
     * lista do tenant inteiro, e o DASH filtra pelo escopo que o ACS devolveu.
     */
    @GetMapping("/api/pac/v1/pacientes")
    List<PacienteView> listarPacientes();

    /** Um paciente — {@code GET /api/pac/v1/pacientes/&#123;id&#125;}. */
    @GetMapping("/api/pac/v1/pacientes/{pacienteId}")
    PacienteView buscarPaciente(@PathVariable("pacienteId") String pacienteId);

    /**
     * Catálogo ativo do paciente — o que o PAC tem de comandos publicados.
     *
     * <p>O endpoint carrega ETag sobre {@code catalogoVersao} e aceita {@code If-None-Match}. O
     * DASH não usa o GET condicional: cada chamada nasce de uma requisição de painel diferente e
     * não há onde guardar o ETag num BFF sem estado (ADR-004). Fica registrado aqui para que a
     * ausência seja uma decisão e não um esquecimento.
     */
    @GetMapping("/api/pac/v1/pacientes/{pacienteId}/catalogo-ativo")
    CatalogoAtivoView getCatalogoAtivo(@PathVariable("pacienteId") String pacienteId);

    /**
     * Recalibração in-place de um comando publicado — {@code POST
     * .../comandos/&#123;id&#125;:recalibrate}, authority {@code pac.comando-lifecycle.recalibrar}.
     *
     * <p><b>É este o "calibrate" que existe.</b> Não há no PAC endpoint de <i>estado de
     * calibração</i> nem {@code .../calibrate}: o que existe é esta transição de ciclo de vida, que
     * incrementa {@code calibracaoVersao}. Os campos também são outros — ver {@link
     * RecalibrateBody}.
     */
    @PostMapping("/api/pac/v1/pacientes/{pacienteId}/comandos/{comandoId}:recalibrate")
    ComandoLifecycleView recalibrar(
            @PathVariable("pacienteId") String pacienteId,
            @PathVariable("comandoId") String comandoId,
            @RequestBody RecalibrateBody body);

    // ---------------------------------------------------------------------
    // Espelhos dos DTOs do produtor. `public record` obrigatório: proxies JDK
    // não acessam membros package-private (CLAUDE.md).
    // ---------------------------------------------------------------------

    /**
     * Espelha {@code PacienteResponse}. Só os campos que o painel usa.
     *
     * <p>{@code id} vem como {@code String} e não {@code UUID}: no fio um UUID é uma string JSON, e
     * o DASH trata identificador de paciente como opaco de ponta a ponta — mesma razão registrada
     * no {@code AcsClient}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PacienteView(String id, String nome) {}

    /** Espelha {@code CatalogoAtivoResponse}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CatalogoAtivoView(String pacienteId, int catalogoVersao, List<ComandoView> comandos) {}

    /**
     * Espelha {@code CatalogoAtivoResponse.ComandoSummary}.
     *
     * <p>{@code sensibilidade} e {@code urgencia} são enums no PAC e chegam como texto; ficam
     * {@code String} aqui de propósito — um valor novo do lado do produtor não pode virar exceção
     * de desserialização no caminho de leitura do painel.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ComandoView(
            String id,
            String rotulo,
            String necessidade,
            String sensibilidade,
            String urgencia,
            int calibracaoVersao) {}

    /**
     * Espelha {@code RecalibrateRequest} campo por campo.
     *
     * <p><b>Nenhum campo é inventado, e três do DASH sumiram.</b> O request antigo do painel era
     * {@code (sensitivity, threshold, extra)}: o PAC não tem {@code threshold}, não tem mapa {@code
     * extra}, e a sensibilidade é um enum ({@code baixa|media|alta}), não texto livre. Todos os
     * campos aqui são opcionais no produtor — {@code null} significa "mantém o valor atual".
     */
    record RecalibrateBody(
            String urgencia,
            String sensibilidade,
            Integer tempoMinGestoMs,
            Integer tempoMaxGestoMs) {}

    /** Espelha {@code ComandoLifecycleResponse}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ComandoLifecycleView(
            String id,
            String pacienteId,
            String rotulo,
            String sensibilidade,
            String urgencia,
            String estado,
            int calibracaoVersao,
            List<String> warnings) {}
}
