package com.neuroassist.gest.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.neuroassist.gest.application.port.out.EdgeDevicePort;
import com.neuroassist.gest.application.port.out.EdgePairingPort;
import com.neuroassist.gest.application.port.out.PatientCatalogPort;
import com.neuroassist.gest.bdd.BddTestContext;
import com.neuroassist.gest.bdd.ScenarioState;
import com.neuroassist.gest.bdd.TestJwtFactory;
import com.neuroassist.gest.infrastructure.security.TenantContextHolder;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

/**
 * Steps comuns a duas ou mais features, e os ganchos de ciclo de vida do cenário.
 *
 * <p><b>Por que existe um gancho que liga o tenant na thread de teste.</b> A suíte inteira estava
 * 54/54 vermelha com {@code relation "tipo_gesto_transition" does not exist}. A causa não era o
 * caminho HTTP: era o próprio setup. Os steps que falam com o banco direto (limpeza, montagem de
 * cenário, leitura de auditoria) rodam na thread do teste, onde o {@code TenantContextFilter} nunca
 * passou — ele só roda na thread do Tomcat, dentro da requisição. Sem tenant ligado, o {@code
 * TenantIdentifierResolver} devolve o fallback {@code neuroassist_global}, onde as tabelas do GEST
 * não existem, e o primeiro {@code deleteAll()} do {@code @Before} derrubava o cenário antes do
 * primeiro {@code When}.
 *
 * <p>Ligar aqui é seguro justamente porque as duas threads são distintas: o filtro continua dono do
 * contexto dentro da requisição e o limpa no {@code finally}; este gancho é dono do contexto da
 * thread de teste. Nenhum dos dois enxerga o {@code ThreadLocal} do outro.
 */
@Slf4j
@RequiredArgsConstructor
public class SharedSteps {

    /**
     * Tenant primário dos cenários; o schema {@code t_emp_test} é criado pelas migrations reais.
     */
    public static final String TENANT = "emp_test";

    /** Segundo tenant, usado só para provar isolamento. */
    public static final String TENANT_OTHER = "emp_other";

    public static final String SCHEMA = "t_" + TENANT;
    public static final String SCHEMA_OTHER = "t_" + TENANT_OTHER;

    private final ScenarioState state;
    private final BddTestContext db;
    private final EdgePairingPort edgePairingPort;
    private final EdgeDevicePort edgeDevicePort;
    private final PatientCatalogPort patientCatalogPort;

    // ── Ciclo de vida ────────────────────────────────────────────────────────

    /**
     * Roda antes de qualquer outro gancho ({@code order = 0}): liga o tenant, zera os dois schemas
     * e devolve os mocks ao estado neutro. Sem a ordem explícita, a limpeza de dados poderia rodar
     * antes do tenant estar ligado — que é exatamente o defeito original.
     */
    @Before(order = 0)
    public void prepararCenario() {
        TenantContextHolder.set(TENANT);
        // BddTestContext.currentSchema() deriva do tenant corrente; sem isto os helpers JDBC
        // montavam "t_null.tipo_gesto" e o cenario falhava por SQL invalido, nao pelo que testa.
        db.setCurrentTenant(TENANT);
        db.cleanSchema(SCHEMA);
        db.cleanSchema(SCHEMA_OTHER);

        reset(edgePairingPort, edgeDevicePort, patientCatalogPort);
        // Padrões neutros: dispositivo pareado à empresa de teste, sem paciente, catálogo vazio.
        // Cada um é sobrescrito pelos cenários que se importam com o contrário.
        when(edgePairingPort.checkPairingAndGetTenant(any(UUID.class))).thenReturn(TENANT);
        when(edgeDevicePort.resolveBinding(any(UUID.class)))
                .thenReturn(EdgeDevicePort.DeviceBinding.unpaired(TENANT));
        when(patientCatalogPort.bandsFor(any(UUID.class)))
                .thenReturn(PatientCatalogPort.PatientBands.empty());

        state.currentToken = TestJwtFactory.buildManageToken(TENANT);
        state.lastResponse = null;
        state.previousResponse = null;
        state.lastEntityId = null;
    }

    @After
    public void encerrarCenario() {
        TenantContextHolder.clear();
    }

    // ── Background ───────────────────────────────────────────────────────────

    /**
     * Não é decorativo: o schema do tenant tem de existir de verdade, senão o {@code
     * TenantContextFilter} responde 503 a toda requisição do cenário e os {@code Then} de status
     * falhariam por um motivo que não é o testado.
     */
    @Given("the tenant {string} is active")
    public void oTenantEstaAtivo(String nomeNaFeature) {
        String tenant = db.mapTenantName(nomeNaFeature);
        assertThat(tenant).isEqualTo(TENANT);
        assertThat(db.schemaExists(db.schemaFor(tenant)))
                .as("schema %s tem de existir — as migrations reais o criam", db.schemaFor(tenant))
                .isTrue();
    }

    @And("the caller is authenticated")
    public void oChamadorEstaAutenticado() {
        state.currentToken = TestJwtFactory.buildManageToken(TENANT);
    }

    @And("the caller is authenticated with role {string}")
    public void oChamadorEstaAutenticadoComPapel(String papel) {
        state.currentToken = tokenParaPapel(papel);
    }

    @And("the caller has role {string}")
    public void oChamadorTemPapel(String papel) {
        state.currentToken = tokenParaPapel(papel);
    }

    /**
     * COORDENADOR_CLINICA vs. CUIDADORA — os dois conjuntos de authorities que os usuários em
     * memória do HTTP Basic representavam, agora como claims do token. A diferença entre eles é o
     * que os cenários de 403 medem.
     */
    private String tokenParaPapel(String papel) {
        return switch (papel) {
            case "gest.manage" -> TestJwtFactory.buildManageToken(TENANT);
            case "gest.read" -> TestJwtFactory.buildReadToken(TENANT);
            default ->
                    throw new IllegalArgumentException(
                            "Papel desconhecido na feature: '"
                                    + papel
                                    + "' — acrescente o mapeamento em vez de deixar o step passar");
        };
    }

    // ── Asserções de resposta ────────────────────────────────────────────────

    /**
     * Um único step de status, parametrizado.
     *
     * <p>Os steps literais anteriores ("the response status is 201", …) começavam por {@code if
     * (lastResponse == null) return;} — um cenário cujo {@code When} não fizesse requisição nenhuma
     * passava no {@code Then}. Aqui a ausência de resposta é falha, porque é exatamente o sintoma
     * de um step de ação que não age.
     */
    @Then("the response status is {int}")
    public void oStatusDaRespostaE(int esperado) {
        assertThat(state.lastResponse)
                .as("nenhuma requisição foi feita pelo step anterior")
                .isNotNull();
        assertThat(state.lastResponse.getStatusCode())
                .as("corpo da resposta: %s", state.lastResponse.getBody())
                .isEqualTo(HttpStatus.valueOf(esperado));
    }

    /** O {@code type} do ProblemDetail (RFC 7807) — o código estável do erro, não o texto dele. */
    @And("the error type is {string}")
    public void oTipoDoErroE(String tipo) {
        assertThat(state.lastResponse).isNotNull();
        assertThat(state.lastResponse.getBody())
                .as("o corpo deveria ser um ProblemDetail com type terminando em '%s'", tipo)
                .isNotNull()
                .contains(tipo);
    }
}
