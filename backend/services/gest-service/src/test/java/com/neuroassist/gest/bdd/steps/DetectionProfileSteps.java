package com.neuroassist.gest.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.neuroassist.gest.application.port.out.EdgeDevicePort;
import com.neuroassist.gest.application.port.out.EdgePairingPort;
import com.neuroassist.gest.application.port.out.PatientCatalogPort;
import com.neuroassist.gest.bdd.BddTestContext;
import com.neuroassist.gest.bdd.GestHttp;
import com.neuroassist.gest.bdd.ScenarioState;
import com.neuroassist.gest.domain.model.Sensibilidade;
import com.neuroassist.gest.shared.error.CodedException;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;

/**
 * Steps de versionamento e pull de perfil de detecção — {@code detection-profile.feature}.
 *
 * <p>Os perfis são criados <b>publicando de verdade</b>, e não com INSERT direto. Não é preferência
 * de estilo: {@code detection_profile.classification_params} passou a ter {@code
 * ck_profile_params_three_bands} (V0401), e a numeração monotônica de versão é justamente uma das
 * invariantes sob teste — montar a linha na mão testaria o INSERT do teste, não o serviço.
 */
@Slf4j
@RequiredArgsConstructor
public class DetectionProfileSteps {

    private static final String PROFILE_PATH = "/gest-service/detection-profile";

    /**
     * O endpoint por dispositivo recebe um UUID; "D-12" é o apelido que a feature usa. O mapa é
     * fixo para que o mesmo apelido resolva no mesmo id ao longo do cenário.
     */
    private static final UUID DEVICE_D12 = UUID.fromString("d12d12d1-2d12-4d12-8d12-d12d12d12d12");

    private final GestHttp http;
    private final ScenarioState state;
    private final BddTestContext db;
    private final EdgePairingPort edgePairingPort;
    private final EdgeDevicePort edgeDevicePort;
    private final PatientCatalogPort patientCatalogPort;

    // ── Given — primitivos ───────────────────────────────────────────────────

    @Given("primitive {string} is in state {string} with engineFeatureRef {string}")
    public void primitivoEmEstadoComRef(String nome, String estado, String ref) {
        db.insertTipoGesto(nome, estado, ref, SharedSteps.SCHEMA);
    }

    @And("primitive {string} is in state {string}")
    public void primitivoEmEstado(String nome, String estado) {
        // engine_feature_ref só é exigido em 'supported' (ck_tipo_gesto_supported_needs_ref).
        String ref = "supported".equals(estado) ? "mp." + nome + ".v1" : null;
        db.insertTipoGesto(nome, estado, ref, SharedSteps.SCHEMA);
    }

    @Given("no primitives are in state {string} in tenant {string}")
    public void nenhumPrimitivoNoEstado(String estado, String tenant) {
        // O gancho de SharedSteps já esvaziou o schema; um primitivo em outro estado torna o
        // cenário mais honesto — prova que o 422 vem da AUSÊNCIA de 'supported', e não da
        // ausência de qualquer primitivo.
        db.insertTipoGesto("so-registrado", "registered", null, SharedSteps.SCHEMA);
        assertThat(db.countRows(SharedSteps.SCHEMA, "tipo_gesto")).isEqualTo(1);
    }

    @And("primitive {string} is in state \"supported\" and added after the profile was published")
    public void primitivoSupportedAdicionadoDepoisDoPublish(String nome) {
        assertThat(db.profileVersions(SharedSteps.SCHEMA))
                .as("o perfil tem de existir ANTES deste primitivo, senão o cenário não prova nada")
                .isNotEmpty();
        db.insertTipoGesto(nome, "supported", "mp." + nome + ".v1", SharedSteps.SCHEMA);
    }

    // ── Given — perfis ───────────────────────────────────────────────────────

    @Given("a profile version {int} already exists for tenant {string}")
    public void perfilVersaoJaExiste(int versao, String tenant) {
        garantirPrimitivoSuportado("3f1c-aaa");
        publicarAte(versao);
    }

    @Given("a profile version {int} already exists for tenant {string} with primitives [{string}]")
    public void perfilVersaoJaExisteComPrimitivos(int versao, String tenant, String nome) {
        garantirPrimitivoSuportado(nome);
        publicarAte(versao);
        assertThat(db.profilePrimitiveNames(versao, SharedSteps.SCHEMA)).containsExactly(nome);
    }

    @Given("profile version {int} is active for tenant {string}")
    public void perfilVersaoEstaAtivo(int versao, String tenant) {
        garantirPrimitivoSuportado("3f1c-aaa");
        publicarAte(versao);
        assertThat(db.profileActiveFlag(versao, SharedSteps.SCHEMA)).isTrue();
    }

    @And("profile version {int} is active for tenant {string} with primitive {string}")
    public void perfilVersaoAtivoComPrimitivo(int versao, String tenant, String nome) {
        garantirPrimitivoSuportado(nome);
        publicarAte(versao);
    }

    @Given("no detection profile exists for tenant {string}")
    public void nenhumPerfilExiste(String tenant) {
        assertThat(db.profileVersions(SharedSteps.SCHEMA)).isEmpty();
    }

    // ── Given — dispositivo ──────────────────────────────────────────────────

    @Given("device {string} is paired to tenant {string}")
    public void dispositivoPareadoAoTenant(String device, String tenant) {
        String tenantId = db.mapTenantName(tenant);
        when(edgePairingPort.checkPairingAndGetTenant(any(UUID.class))).thenReturn(tenantId);
        when(edgeDevicePort.resolveBinding(any(UUID.class)))
                .thenReturn(EdgeDevicePort.DeviceBinding.unpaired(tenantId));
    }

    /**
     * O guarda de pareamento é do edge-service, e é ele que decide. O contrato da porta diz que a
     * recusa chega como {@code CodedException("device-not-paired")} — o mock reproduz esse
     * contrato, não uma suposição sobre ele.
     */
    @Given("device {string} is not paired to the caller's tenant")
    public void dispositivoNaoPareado(String device) {
        when(edgePairingPort.checkPairingAndGetTenant(any(UUID.class)))
                .thenThrow(
                        new CodedException(
                                "device-not-paired",
                                "Dispositivo não pareado à empresa do chamador"));
    }

    @And(
            "the paired patient has calibrated {string} to sensibilidade {string} in catalog version {int}")
    public void pacientePareadoCalibrouGesto(String nome, String faixa, int catalogoVersao) {
        UUID pacienteId = UUID.randomUUID();
        UUID tipoGestoId = db.tipoGestoIdByNome(nome, SharedSteps.SCHEMA);
        assertThat(tipoGestoId)
                .as("primitivo '%s' tem de existir antes da calibração", nome)
                .isNotNull();

        when(edgeDevicePort.resolveBinding(any(UUID.class)))
                .thenReturn(EdgeDevicePort.DeviceBinding.of(SharedSteps.TENANT, pacienteId));
        when(patientCatalogPort.bandsFor(pacienteId))
                .thenReturn(
                        PatientCatalogPort.PatientBands.of(
                                catalogoVersao, Map.of(tipoGestoId, Sensibilidade.valueOf(faixa))));
    }

    // ── When ─────────────────────────────────────────────────────────────────

    @When("the caller publishes a detection profile with default classification params")
    public void publicarComParametrosPadrao() {
        http.post(PROFILE_PATH, "{}");
    }

    @When("the caller publishes a detection profile")
    public void publicar() {
        http.post(PROFILE_PATH, "{}");
    }

    @When("the caller publishes a new detection profile")
    public void publicarNovo() {
        http.post(PROFILE_PATH, "{}");
    }

    @When("the caller gets the active detection profile")
    public void obterPerfilAtivo() {
        http.get(PROFILE_PATH + "/active");
    }

    @When("the caller gets profile by version {int}")
    public void obterPerfilPorVersao(int versao) {
        http.get(PROFILE_PATH + "/" + versao);
    }

    @When("the edge device {string} gets its detection profile without If-None-Match")
    public void bordaObtemPerfilSemIfNoneMatch(String device) {
        http.get(PROFILE_PATH + "/devices/" + DEVICE_D12);
    }

    @When("the edge device {string} gets its detection profile with If-None-Match {string}")
    public void bordaObtemPerfilComIfNoneMatch(String device, String etag) {
        http.getIfNoneMatch(PROFILE_PATH + "/devices/" + DEVICE_D12, "\"" + etag + "\"");
    }

    // ── Then — corpo ─────────────────────────────────────────────────────────

    @And("the response body contains version {int}")
    public void corpoContemVersao(int versao) {
        assertThat(http.lastJson().get("version").asLong()).isEqualTo(versao);
    }

    @And("the returned version is {int}")
    public void versaoRetornadaE(int versao) {
        corpoContemVersao(versao);
    }

    @And("the response body has version {int}")
    public void corpoTemVersao(int versao) {
        corpoContemVersao(versao);
    }

    @And("the profile primitives list contains {string} with engineFeatureRef {string}")
    public void listaDePrimitivosContem(String nome, String ref) {
        JsonNode primitivo = primitivoDoCorpo(nome);
        assertThat(primitivo).as("primitivo '%s' ausente da resposta", nome).isNotNull();
        assertThat(primitivo.get("engineFeatureRef").asText()).isEqualTo(ref);
    }

    @And("the profile primitives list does NOT contain {string}")
    public void listaDePrimitivosNaoContem(String nome) {
        assertThat(primitivoDoCorpo(nome))
                .as("invariante #2: o perfil só carrega primitivos 'supported'")
                .isNull();
    }

    @And("the profile has active true")
    public void perfilTemActiveTrue() {
        assertThat(http.lastJson().get("active").asBoolean()).isTrue();
    }

    @And("the new profile has active true")
    public void novoPerfilTemActiveTrue() {
        perfilTemActiveTrue();
    }

    @And("the primitive {string} has sensibilidade {string} with origem {string}")
    public void primitivoTemSensibilidadeEOrigem(String nome, String faixa, String origem) {
        JsonNode primitivo = primitivoDoCorpo(nome);
        assertThat(primitivo).as("primitivo '%s' ausente da resposta", nome).isNotNull();
        assertThat(primitivo.get("sensibilidade").asText()).isEqualTo(faixa);
        assertThat(primitivo.get("origem").asText()).isEqualTo(origem);
    }

    @And("the response body reports catalogoVersao {int} and catalogoDegradado {word}")
    public void corpoReportaCatalogo(int versao, String degradado) {
        JsonNode corpo = http.lastJson();
        assertThat(corpo.get("catalogoVersao").asInt()).isEqualTo(versao);
        assertThat(corpo.get("catalogoDegradado").asBoolean())
                .isEqualTo(Boolean.parseBoolean(degradado));
        assertThat(corpo.get("pacienteId").isNull())
                .as("a resposta por dispositivo tem de dizer de qual paciente é a projeção")
                .isFalse();
    }

    @And("the response body is empty")
    public void corpoVazio() {
        String corpo = state.lastResponse.getBody();
        assertThat(corpo == null || corpo.isBlank())
                .as("um 304 não pode carregar corpo — o ponto do 304 é economizar banda")
                .isTrue();
    }

    // ── Then — cabeçalho ─────────────────────────────────────────────────────

    @And("the response ETag is {string}")
    public void etagDaRespostaE(String esperado) {
        assertThat(state.lastResponse.getHeaders().getETag()).isEqualTo("\"" + esperado + "\"");
    }

    // ── Then — banco ─────────────────────────────────────────────────────────

    @And("no version is ever reused")
    public void nenhumaVersaoEReutilizada() {
        List<Long> versoes = db.profileVersions(SharedSteps.SCHEMA);
        assertThat(versoes).doesNotHaveDuplicates().isSorted();
        // Monotônica de verdade: a sequência tem de ser 1..N sem buraco nem repetição.
        List<Long> esperadas = new ArrayList<>();
        for (long v = 1; v <= versoes.size(); v++) {
            esperadas.add(v);
        }
        assertThat(versoes).isEqualTo(esperadas);
    }

    @And("profile version {int} has active false")
    public void perfilVersaoTemActiveFalse(int versao) {
        assertThat(db.profileActiveFlag(versao, SharedSteps.SCHEMA)).isFalse();
    }

    @And("there is exactly one active profile for tenant {string}")
    public void existeExatamenteUmPerfilAtivo(String tenant) {
        assertThat(db.countActiveProfiles(SharedSteps.SCHEMA)).isEqualTo(1);
    }

    @Then("the primitives list of profile version {int} still contains only [{string}]")
    public void listaDoPerfilAindaContemApenas(int versao, String nome) {
        assertThat(db.profilePrimitiveNames(versao, SharedSteps.SCHEMA)).containsExactly(nome);
    }

    @And("the primitives list of profile version {int} contains {string}")
    public void listaDoPerfilContem(int versao, String nome) {
        assertThat(db.profilePrimitiveNames(versao, SharedSteps.SCHEMA)).contains(nome);
    }

    @And("the primitives list matches exactly what was recorded at publish time")
    public void listaBateComOQueFoiGravadoNoPublish() {
        long versao = http.lastJson().get("version").asLong();
        List<String> naResposta = new ArrayList<>();
        http.lastJson().get("primitives").forEach(p -> naResposta.add(p.get("nome").asText()));
        assertThat(naResposta)
                .containsExactlyInAnyOrderElementsOf(
                        db.profilePrimitiveNames(versao, SharedSteps.SCHEMA));
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    /** Publica repetidamente até o perfil ativo ser exatamente a versão pedida. */
    private void publicarAte(int versaoAlvo) {
        long jaExistentes = db.profileVersions(SharedSteps.SCHEMA).size();
        for (long v = jaExistentes; v < versaoAlvo; v++) {
            ResponseEntity<String> response = http.post(PROFILE_PATH, "{}");
            assertThat(response.getStatusCode().value())
                    .as("setup: publish falhou — %s", response.getBody())
                    .isEqualTo(200);
        }
        assertThat(db.profileVersions(SharedSteps.SCHEMA)).hasSize(versaoAlvo);
        // O setup usa o mesmo estado que o cenário vai avaliar; zerar a resposta evita que um
        // Then leia por engano a resposta de uma publicação de montagem.
        state.lastResponse = null;
    }

    private void garantirPrimitivoSuportado(String nome) {
        if (db.tipoGestoIdByNome(nome, SharedSteps.SCHEMA) == null) {
            db.insertTipoGesto(nome, "supported", "mp." + nome + ".v1", SharedSteps.SCHEMA);
        }
    }

    private JsonNode primitivoDoCorpo(String nome) {
        JsonNode primitivos = http.lastJson().get("primitives");
        assertThat(primitivos)
                .as("a resposta do perfil tem de trazer os primitivos (E2E-033)")
                .isNotNull();
        for (JsonNode p : primitivos) {
            if (nome.equals(p.get("nome").asText())) {
                return p;
            }
        }
        return null;
    }
}
