package com.neuroassist.e2e.crossservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

import com.neuroassist.e2e.support.DevFixtures;
import com.neuroassist.e2e.support.E2EConfig;
import com.neuroassist.e2e.support.E2ETestBase;
import io.restassured.response.Response;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DEV — {@code GET /gest-service/detection-profile/devices/{deviceId}}.
 *
 * <p>É o fluxo cross-service mais denso do sistema: o gest-service checa o pareamento no
 * edge-service, lê o catálogo ativo do paciente no pac-service, e projeta a faixa calibrada de cada
 * gesto sobre o perfil publicado da empresa. Três serviços, uma resposta.
 *
 * <p>Todo nome de campo abaixo foi copiado de {@code DetectionProfileResponse} e {@code
 * DetectionProfilePrimitiveResponse} — não de um exemplo de payload nem de memória.
 */
class DetectionProfileProjectionE2ETest extends E2ETestBase {

    private static final String PATH =
            "/gest-service/detection-profile/devices/" + DevFixtures.DEVICE;

    @Test
    @DisplayName("DEV-01 — o perfil do dispositivo vem projetado com o paciente pareado")
    void perfilDoDispositivoTrazOPacientePareado() {
        authed(E2EConfig.GEST)
                .get(PATH)
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("version", greaterThan(0))
                .body("active", equalTo(true))
                .body("contractVersion", notNullValue())
                // pacienteId so e preenchido na resposta POR DISPOSITIVO — e o que distingue esta
                // rota da /active, que responde o perfil da empresa sem paciente nenhum.
                .body("pacienteId", equalTo(DevFixtures.PACIENTE))
                .body("catalogoVersao", notNullValue())
                // catalogoDegradado=true significaria que o PAC nao respondeu. Se virar true aqui,
                // o teste esta medindo uma queda do PAC, nao a projecao.
                .body("catalogoDegradado", equalTo(false))
                .body("publishedAt", notNullValue());
    }

    @Test
    @DisplayName("DEV-02 — a tabela de limiares traz as tres faixas com os campos do contrato")
    void tabelaDeLimiaresTemAsTresFaixas() {
        Response response =
                authed(E2EConfig.GEST).get(PATH).then().statusCode(200).extract().response();

        for (String faixa : List.of("baixa", "media", "alta")) {
            java.util.Map<String, Object> banda = response.path("thresholds." + faixa);
            assertThat(banda).as("faixa %s ausente em thresholds", faixa).isNotNull();
            // Um subconjunto dos campos de DetectionThresholds: o suficiente para detectar que a
            // faixa veio vazia ou com outro shape, sem congelar valores que a clinica pode
            // calibrar.
            assertThat((Number) response.path("thresholds." + faixa + ".earClose")).isNotNull();
            assertThat((Number) response.path("thresholds." + faixa + ".earOpen")).isNotNull();
            assertThat((Number) response.path("thresholds." + faixa + ".blinkMinFrames"))
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("DEV-03 — cada primitivo diz com que faixa detecta-lo e de onde a faixa veio")
    void primitivosDeclaramFaixaEOrigem() {
        Response response =
                authed(E2EConfig.GEST).get(PATH).then().statusCode(200).extract().response();

        List<String> origens = response.path("primitives.origem");
        List<String> sensibilidades = response.path("primitives.sensibilidade");
        List<String> engineRefs = response.path("primitives.engineFeatureRef");

        assertThat(origens).as("o perfil projetado nao trouxe primitivo nenhum").isNotEmpty();
        // Os tres estados de origem sao o contrato: sem eles, "a cuidadora escolheu media" e
        // "ninguem calibrou" seriam indistinguiveis.
        assertThat(origens)
                .allMatch(o -> List.of("PACIENTE", "PERFIL_EMPRESA", "INDISPONIVEL").contains(o));
        assertThat(sensibilidades).allMatch(s -> List.of("baixa", "media", "alta").contains(s));
        assertThat(engineRefs).allSatisfy(ref -> assertThat(ref).isNotBlank());
        // Pelo menos um gesto deste paciente esta calibrado — e o que torna o cenario uma prova de
        // projecao, e nao apenas uma leitura do perfil da empresa.
        assertThat(origens).contains("PACIENTE");
    }

    @Test
    @DisplayName("DEV-04 — o ETag combina versao do perfil e versao do catalogo, e habilita 304")
    void etagCombinaAsDuasVersoesEHabilita304() {
        Response first =
                authed(E2EConfig.GEST).get(PATH).then().statusCode(200).extract().response();

        String etagHeader = first.getHeader("ETag");
        assertThat(etagHeader)
                .as("ETag ausente — o agente de borda perde o cache condicional")
                .isNotNull();

        int version = first.path("version");
        int catalogoVersao = first.path("catalogoVersao");
        // Formato "<perfil>-<catalogo>": so `version` faria a recalibracao de um paciente NAO
        // invalidar o cache do dispositivo dele.
        assertThat(etagHeader).isEqualTo("\"%d-%d\"".formatted(version, catalogoVersao));
        assertThat(first.path("etag").toString()).isEqualTo(etagHeader);

        authed(E2EConfig.GEST).header("If-None-Match", etagHeader).get(PATH).then().statusCode(304);
    }
}
