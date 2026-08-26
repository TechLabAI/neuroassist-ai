package com.neuroassist.e2e.dash;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.e2e.support.DevFixtures;
import com.neuroassist.e2e.support.E2EConfig;
import com.neuroassist.e2e.support.E2ETestBase;
import com.neuroassist.e2e.support.FixtureFactory;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DSH — painel da cuidadora (E2E-008).
 *
 * <p>O dash-service é um BFF: cada endpoint aqui é uma composição de ACS (escopo), PAC (catálogo),
 * EDGE (liveness) e HIST (histórico). É o serviço onde uma suposição errada sobre o caminho de um
 * produtor custa mais caro — e, como se vê em DSH-05, é exatamente o que aconteceu.
 *
 * <p>Este grupo dependia da fixture factory por um motivo simples: o operador de dev não é
 * responsável por nenhum paciente do seed, então o painel dele vem vazio. O paciente da suíte
 * existe justamente para dar ao painel algo que compor.
 */
class CaregiverPanelE2ETest extends E2ETestBase {

    private static FixtureFactory.PatientFixture paciente;
    private static FixtureFactory.DeviceFixture dispositivo;

    @BeforeAll
    static void montarCenario() {
        paciente = FixtureFactory.sharedPatient();
        dispositivo = FixtureFactory.sharedDevice();
        // Uma batida de ponto antes das asserções de liveness: sem ela o EDGE reporta 'offline' e o
        // cenário estaria medindo o relógio, não a composição.
        dispositivo
                .authed(E2EConfig.EDGE)
                .body(Map.of("agentVersion", "e2e-suite"))
                .post("/api/v1/edge/heartbeat")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("DSH-01 — o resumo lista o paciente pelo qual a cuidadora e responsavel (ACS)")
    void resumoListaPacienteDoEscopo() {
        Response resposta =
                authed(E2EConfig.DASH)
                        .get("/panel/summary")
                        .then()
                        .statusCode(200)
                        .extract()
                        .response();

        List<String> ids = resposta.path("patients.patientId");
        // `contains`, não `containsExactly`: o painel é do ambiente de dev compartilhado e outros
        // pacientes podem estar legitimamente vinculados à mesma cuidadora.
        assertThat(ids).contains(paciente.pacienteId().toString());
    }

    @Test
    @DisplayName("DSH-02 — o resumo traz o liveness do dispositivo, vindo do EDGE")
    void resumoTrazLivenessDoEdge() {
        Response resposta =
                authed(E2EConfig.DASH)
                        .get("/panel/summary")
                        .then()
                        .statusCode(200)
                        .extract()
                        .response();

        int indice =
                resposta.<java.util.List<String>>path("patients.patientId")
                        .indexOf(paciente.pacienteId().toString());
        assertThat(indice).as("o paciente da fixture precisa estar no resumo").isNotNegative();

        // 'online' prova a cadeia inteira: DASH → EDGE → pareamento → o heartbeat de @BeforeAll.
        // 'no-device' significaria que o pareamento sumiu; 'unknown' que o EDGE não respondeu.
        assertThat(resposta.<java.util.List<String>>path("patients.deviceStatus").get(indice))
                .isEqualTo("online");
    }

    @Test
    @DisplayName("DSH-03 — o status do dispositivo por paciente espelha o dispositivo pareado")
    void statusDoDispositivoPorPaciente() {
        Response resposta =
                authed(E2EConfig.DASH)
                        .get("/panel/patients/{id}/device", paciente.pacienteId())
                        .then()
                        .statusCode(200)
                        .extract()
                        .response();

        assertThat((String) resposta.path("deviceId")).isEqualTo(dispositivo.deviceId().toString());
        assertThat((String) resposta.path("status")).isEqualTo("online");
        // `degraded` é conceito do DASH e significa "não consegui falar com o EDGE" (C-34). Com o
        // EDGE no ar e respondendo, ele tem de ser falso — se virar true com status preenchido, a
        // separação entre "não há" e "não sei" voltou a ser confundida.
        assertThat((Boolean) resposta.path("degraded")).isFalse();
    }

    @Test
    @DisplayName("DSH-04 — o historico do paciente responde a partir do HIST")
    void historicoDoPacienteResponde() {
        Response resposta =
                authed(E2EConfig.DASH)
                        .queryParam("size", 5)
                        .get("/panel/patients/{id}/history", paciente.pacienteId())
                        .then()
                        .statusCode(200)
                        .extract()
                        .response();

        // Sem asserção de conteúdo: o histórico do painel é composto pelo HIST e o que ele devolve
        // depende de fatos assíncronos. O que este cenário prova é que a composição DASH→HIST
        // atravessa autenticação e paginação — um 503 aqui seria o sintoma de identidade de serviço
        // quebrada, que é o defeito que ele guarda.
        assertThat((List<?>) resposta.path("entries")).isNotNull();
        assertThat((Integer) resposta.path("page")).isZero();
    }

    @Test
    @DisplayName("DSH-05 — paciente fora do escopo da cuidadora e recusado com 403")
    void pacienteForaDoEscopoERecusado() {
        // O paciente do seed pertence a outra cuidadora. O operador desta suíte não é responsável
        // por ele, e o DASH valida escopo no ACS antes de compor qualquer coisa.
        Response resposta =
                authed(E2EConfig.DASH)
                        .get("/panel/patients/{id}/device", DevFixtures.PACIENTE)
                        .then()
                        .statusCode(403)
                        .extract()
                        .response();

        assertThat(resposta.asString()).contains("PATIENT_NOT_IN_SCOPE");
    }

    @Test
    @DisplayName(
            "DSH-06 — DEFEITO: o overview do paciente esta quebrado; o DASH chama um caminho que o"
                    + " PAC nao expoe")
    void overviewChamaCaminhoInexistenteNoPac() {
        // Armadilha nº1 do repositório, ao vivo: `PacClient` foi escrito a partir da suposição do
        // consumidor — `GET /api/v1/patients/{id}/commands` — enquanto o pac-service expõe
        // `GET /api/pac/v1/pacientes/{id}/catalogo-ativo`. O gate de módulo do DASH fica verde
        // porque o WireMock dele responde no caminho suposto; em runtime, o overview do painel
        // nunca funcionou.
        //
        // São três métodos com o mesmo prefixo errado em PacClient: getCommandCatalog,
        // getCalibrationState e o write-through de calibração.
        Response resposta =
                authed(E2EConfig.DASH)
                        .get("/panel/patients/{id}/overview", paciente.pacienteId())
                        .then()
                        .extract()
                        .response();

        assertThat(resposta.statusCode())
                .as("enquanto o caminho do PacClient estiver errado, o overview responde 404")
                .isEqualTo(404);
        assertThat(resposta.asString())
                .as(
                        "o 404 tem de vir do PAC pelo caminho errado — se vier de outro lugar, o"
                                + " cenario deixou de guardar o defeito que documenta")
                .contains("/api/v1/patients/")
                .contains("commands");
    }

    @Test
    @DisplayName(
            "DSH-07 — DEFEITO: o resumo nunca mostra evento ativo nem o nome do paciente (stub"
                    + " vivo)")
    void resumoTrazPlaceholderEmVezDeNomeEEventoAtivo() {
        // `PanelCompositionService.buildSummaryEntry` devolve `new PatientSummaryEntry(patientId,
        // patientId, false, ...)` — o nome é o próprio id e `hasActiveEvent` é a constante false,
        // com o comentário "serao resolvidos por adapters reais em P5". É stub vivo, não
        // scaffolding morto: o endpoint responde 200 e a cuidadora vê um painel plausível que
        // jamais acende para um alarme aberto.
        Response resposta =
                authed(E2EConfig.DASH)
                        .get("/panel/summary")
                        .then()
                        .statusCode(200)
                        .extract()
                        .response();

        int indice =
                resposta.<java.util.List<String>>path("patients.patientId")
                        .indexOf(paciente.pacienteId().toString());
        assertThat(indice).isNotNegative();

        assertThat(resposta.<java.util.List<String>>path("patients.name").get(indice))
                .as(
                        "enquanto o adapter de identidade nao existir, name e o proprio id; quando"
                                + " passar a ser '%s', o stub foi resolvido",
                        paciente.nome())
                .isEqualTo(paciente.pacienteId().toString());
        assertThat(resposta.<java.util.List<Boolean>>path("patients.hasActiveEvent").get(indice))
                .as("constante false no servico — nao reflete evento nenhum")
                .isFalse();
    }
}
