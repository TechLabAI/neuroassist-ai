package com.neuroassist.e2e.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.e2e.support.E2EConfig;
import com.neuroassist.e2e.support.E2ETestBase;
import com.neuroassist.e2e.support.FixtureFactory;
import io.restassured.response.Response;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FLOW-04 — apagamento de dados pessoais a pedido (LGPD, E2E-012).
 *
 * <h2>Por que estava fora, e o que mudou</h2>
 *
 * <p>O motivo registrado era "destrutivo e irreversível num ambiente de dev compartilhado" — e
 * continua verdadeiro. O que destrava o fluxo não é aceitar o risco, é <b>não correr o risco</b>: a
 * {@link FixtureFactory} cria um paciente descartável, exclusivo desta rodada, e o apagamento
 * acontece sobre ele. Nenhuma linha do seed é tocada.
 *
 * <p>Custo assumido e declarado: este é o único ponto da suíte que deixa uma linha nova por
 * execução. O paciente fica anonimizado logo em seguida (é o que o próprio cenário prova), e o nome
 * anterior carrega {@code runId()} para que a origem seja rastreável.
 *
 * <p>Espelhado de {@code PacienteController#erasePersonalData} e {@code PacienteResponse}.
 */
class LgpdErasureFlowE2ETest extends E2ETestBase {

    /** Literal que o pac-service grava no lugar do nome. Não é "apagado": é substituído. */
    private static final String NOME_APAGADO = "[dados apagados]";

    @Test
    @DisplayName("FLOW-04-01 — apagar os dados pessoais anonimiza o nome e preserva a identidade")
    void apagamentoAnonimizaSemDestruirAIdentidade() {
        FixtureFactory.PatientFixture descartavel = FixtureFactory.disposablePatient("lgpd");

        Response antes =
                authed(E2EConfig.PAC)
                        .get("/api/pac/v1/pacientes/{id}", descartavel.pacienteId())
                        .then()
                        .statusCode(200)
                        .extract()
                        .response();
        assertThat((String) antes.path("nome")).isEqualTo(descartavel.nome());

        Response apagamento =
                authed(E2EConfig.PAC)
                        .delete("/api/pac/v1/pacientes/{id}", descartavel.pacienteId())
                        .then()
                        .statusCode(200)
                        .extract()
                        .response();

        // DELETE é o verbo honesto para "faça os dados desta pessoa sumirem", mas não é um delete:
        // comando e vínculo referenciam a linha, e o hist-service mantém a cadeia de hash sobre
        // ela. O que some é o dado pessoal.
        assertThat((String) apagamento.path("id")).isEqualTo(descartavel.pacienteId().toString());
        assertThat((String) apagamento.path("nome")).isEqualTo(NOME_APAGADO);

        Response depois =
                authed(E2EConfig.PAC)
                        .get("/api/pac/v1/pacientes/{id}", descartavel.pacienteId())
                        .then()
                        .statusCode(200)
                        .extract()
                        .response();
        assertThat((String) depois.path("nome")).isEqualTo(NOME_APAGADO);
        assertThat((String) depois.path("nome")).doesNotContain(FixtureFactory.runId());
    }

    @Test
    @DisplayName("FLOW-04-02 — repetir o apagamento e idempotente, nao um conflito")
    void apagamentoRepetidoEIdempotente() {
        FixtureFactory.PatientFixture descartavel =
                FixtureFactory.disposablePatient("lgpd-repetido");

        authed(E2EConfig.PAC)
                .delete("/api/pac/v1/pacientes/{id}", descartavel.pacienteId())
                .then()
                .statusCode(200);

        // Um titular que reenvia o pedido não pode receber erro: o estado desejado já vale.
        Response segunda =
                authed(E2EConfig.PAC)
                        .delete("/api/pac/v1/pacientes/{id}", descartavel.pacienteId())
                        .then()
                        .statusCode(200)
                        .extract()
                        .response();
        assertThat((String) segunda.path("nome")).isEqualTo(NOME_APAGADO);
    }

    @Test
    @DisplayName(
            "FLOW-04-03 — DEFEITO: a resposta do apagamento declara zero cuidadoras, mas o vinculo"
                    + " continua la")
    void respostaDoApagamentoMenteSobreOsVinculos() {
        FixtureFactory.PatientFixture descartavel =
                FixtureFactory.disposablePatient("lgpd-vinculo");

        Response apagamento =
                authed(E2EConfig.PAC)
                        .delete("/api/pac/v1/pacientes/{id}", descartavel.pacienteId())
                        .then()
                        .statusCode(200)
                        .extract()
                        .response();

        // O javadoc do endpoint diz que a resposta 200 existe para "mostrar o que resta". Ela
        // mostra `cuidadorResponsavelIds: []` — e o GET seguinte, sobre a mesma linha, devolve o
        // vínculo intacto. Não há vazamento nem perda de dado: o vínculo *deve* sobreviver, como o
        // próprio javadoc diz. O errado é a resposta, que informa uma remoção que não houve.
        //
        // Quem lê o corpo (um front, um operador de LGPD) conclui que as cuidadoras foram
        // desvinculadas. Se este cenário ficar vermelho porque a lista deixou de vir vazia, o
        // defeito foi corrigido.
        assertThat((List<?>) apagamento.path("cuidadorResponsavelIds"))
                .as("a resposta do DELETE declara a lista vazia")
                .isEmpty();

        Response depois =
                authed(E2EConfig.PAC)
                        .get("/api/pac/v1/pacientes/{id}", descartavel.pacienteId())
                        .then()
                        .statusCode(200)
                        .extract()
                        .response();
        assertThat((List<?>) depois.path("cuidadorResponsavelIds"))
                .as("…e o estado persistido continua com a cuidadora vinculada")
                .isNotEmpty();
    }
}
