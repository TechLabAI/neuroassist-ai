package com.neuroassist.e2e.crossservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.e2e.support.Await;
import com.neuroassist.e2e.support.DevFixtures;
import com.neuroassist.e2e.support.E2EConfig;
import com.neuroassist.e2e.support.E2ETestBase;
import io.restassured.response.Response;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flow — recalibrar um comando no PAC invalida o cache do dispositivo no GEST.
 *
 * <p>É o cenário que justifica o formato de ETag {@code "<perfil>-<catalogo>"}. Se o ETag fosse só
 * a versão do perfil, a cuidadora recalibraria a sensibilidade, o agente de borda receberia 304 e
 * continuaria detectando com a faixa antiga — sem erro em lugar nenhum. Nenhum gate de módulo pega
 * isso: o PAC não conhece o ETag do GEST e o GEST testa o PAC contra um mock.
 *
 * <p><b>Este teste escreve no ambiente.</b> Ele devolve a sensibilidade ao valor original no fim,
 * mas {@code calibracaoVersao} e {@code catalogoVersao} são monótonos e sobem duas vezes por
 * execução — por isso nenhuma asserção fixa um número absoluto: só a mudança relativa é verificada.
 */
class RecalibrationEtagE2ETest extends E2ETestBase {

    private static final Logger log = LoggerFactory.getLogger(RecalibrationEtagE2ETest.class);

    private static final String DEVICE_PROFILE =
            "/gest-service/detection-profile/devices/" + DevFixtures.DEVICE;
    private static final String CATALOGO =
            "/api/pac/v1/pacientes/" + DevFixtures.PACIENTE + "/catalogo-ativo";

    @Test
    @DisplayName("FLOW-01 — recalibrar no PAC muda o ETag do perfil do dispositivo no GEST")
    void recalibrarNoPacInvalidaOCacheDoDispositivo() {
        Response catalogo =
                authed(E2EConfig.PAC).get(CATALOGO).then().statusCode(200).extract().response();

        // Campos copiados de CatalogoAtivoResponse / ComandoSummary.
        String comandoId = catalogo.path("comandos[0].id");
        String sensibilidadeOriginal = catalogo.path("comandos[0].sensibilidade");
        assertThat(comandoId)
                .as("o paciente de fixture nao tem comando publicado — ver DevFixtures")
                .isNotNull();

        String etagAntes = etagDoDispositivo();
        String novaSensibilidade = "alta".equals(sensibilidadeOriginal) ? "media" : "alta";
        log.info(
                "Recalibrando comando {} de {} para {}",
                comandoId,
                sensibilidadeOriginal,
                novaSensibilidade);

        try {
            Response recalibrado = recalibrar(comandoId, novaSensibilidade);
            // Campos de ComandoLifecycleResponse — o PAC confirma a mudanca na propria resposta.
            assertThat(recalibrado.path("sensibilidade").toString()).isEqualTo(novaSensibilidade);
            assertThat(recalibrado.path("estado").toString()).isEqualTo("published");
            assertThat((Integer) recalibrado.path("calibracaoVersao")).isPositive();

            String etagDepois =
                    Await.untilChanged(
                            "ETag do perfil do dispositivo",
                            etagAntes,
                            Duration.ofSeconds(15),
                            this::etagDoDispositivo);

            // O ETag antigo deixa de valer: o agente que ainda o apresentar recebe 200 com o
            // perfil novo, e nao um 304 que o deixaria com a faixa velha.
            authed(E2EConfig.GEST)
                    .header("If-None-Match", etagAntes)
                    .get(DEVICE_PROFILE)
                    .then()
                    .statusCode(200);

            // E a faixa recalibrada aparece de fato projetada no primitivo.
            Response perfil =
                    authed(E2EConfig.GEST)
                            .get(DEVICE_PROFILE)
                            .then()
                            .statusCode(200)
                            .extract()
                            .response();
            assertThat(perfil.<java.util.List<String>>path("primitives.sensibilidade"))
                    .as("a recalibracao nao chegou ao primitivo projetado")
                    .contains(novaSensibilidade);
            assertThat(perfil.getHeader("ETag")).isEqualTo(etagDepois);
        } finally {
            // Restaura o valor de negocio. As versoes nao voltam — e isso e correto: elas contam
            // quantas vezes o comando foi recalibrado, nao o valor atual.
            recalibrar(comandoId, sensibilidadeOriginal);
        }
    }

    @Test
    @DisplayName("FLOW-02 — a versao do catalogo do PAC e a mesma que o GEST projeta no ETag")
    void versaoDoCatalogoDoPacEAMesmaQueOGestProjeta() {
        int catalogoVersaoNoPac = authed(E2EConfig.PAC).get(CATALOGO).path("catalogoVersao");
        Response perfil =
                authed(E2EConfig.GEST)
                        .get(DEVICE_PROFILE)
                        .then()
                        .statusCode(200)
                        .extract()
                        .response();

        // Um desalinhamento aqui significa que o GEST esta servindo uma projecao velha e nao sabe.
        assertThat((Integer) perfil.path("catalogoVersao")).isEqualTo(catalogoVersaoNoPac);
        assertThat(perfil.getHeader("ETag"))
                .isEqualTo(
                        "\"%d-%d\""
                                .formatted((Integer) perfil.path("version"), catalogoVersaoNoPac));
    }

    private Response recalibrar(String comandoId, String sensibilidade) {
        return authed(E2EConfig.PAC)
                .body(
                        """
                      {"sensibilidade":"%s"}"""
                                .formatted(sensibilidade))
                .post(
                        "/api/pac/v1/pacientes/%s/comandos/%s:recalibrate"
                                .formatted(DevFixtures.PACIENTE, comandoId))
                .then()
                .statusCode(200)
                .extract()
                .response();
    }

    private String etagDoDispositivo() {
        return authed(E2EConfig.GEST).get(DEVICE_PROFILE).getHeader("ETag");
    }
}
