package com.neuroassist.e2e.edge;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.e2e.support.E2EConfig;
import com.neuroassist.e2e.support.E2ETestBase;
import com.neuroassist.e2e.support.FixtureFactory;
import io.restassured.response.Response;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HB — heartbeat de dispositivo (E2E-005).
 *
 * <p>Este grupo estava fora da suíte por uma razão só: exigia um <b>token de dispositivo</b>, que
 * não existe no seed. A cadeia que a {@link FixtureFactory} monta — registrar no EDGE, matricular
 * anonimamente, pedir ao ACS um token que o ACS só assina depois de perguntar ao EDGE se a
 * credencial vale — atravessa dois serviços e nenhum gate de módulo consegue exercitá-la.
 *
 * <p>Espelhado de {@code HeartbeatController} e {@code HeartbeatResponse} do edge-service, campo a
 * campo. Ao mexer numa asserção aqui, abra os dois antes.
 */
class HeartbeatE2ETest extends E2ETestBase {

    private static FixtureFactory.DeviceFixture dispositivo;

    @BeforeAll
    static void montarDispositivo() {
        dispositivo = FixtureFactory.sharedDevice();
    }

    @Test
    @DisplayName("HB-01 — o dispositivo bate ponto com o proprio token e o EDGE responde online")
    void heartbeatComTokenDeDispositivo() {
        Response resposta =
                dispositivo
                        .authed(E2EConfig.EDGE)
                        .body(Map.of("agentVersion", "e2e-suite", "runningProfileVersion", 1))
                        .post("/api/v1/edge/heartbeat")
                        .then()
                        .statusCode(200)
                        .extract()
                        .response();

        // O deviceId vem do token, nunca do corpo (ADR-005, invariante #2). O corpo enviado acima
        // sequer o declara — se o EDGE passasse a lê-lo de lá, esta asserção viria nula.
        assertThat(UUID.fromString(resposta.path("deviceId")))
                .as("o EDGE deve identificar o dispositivo pelo sub do token")
                .isEqualTo(dispositivo.deviceId());
        assertThat((String) resposta.path("status")).isEqualTo("online");
        assertThat((Integer) resposta.path("nextHeartbeatSeconds")).isPositive();
        assertThat((Integer) resposta.path("stalenessThresholdSeconds")).isPositive();
    }

    @Test
    @DisplayName("HB-02 — cada batida avanca ultimaSincronizacao")
    void heartbeatAvancaUltimaSincronizacao() {
        Instant primeira = Instant.parse(baterPonto().path("ultimaSincronizacao"));
        Instant segunda = Instant.parse(baterPonto().path("ultimaSincronizacao"));

        // Sem valor absoluto: o que prova liveness é o avanço, e ele é a única coisa estável entre
        // execuções.
        assertThat(segunda).isAfter(primeira);
    }

    @Test
    @DisplayName("HB-03 — corpo declarando outro dispositivo e recusado com edge.identity-mismatch")
    void heartbeatComIdentidadeForjadaNoCorpo() {
        Response resposta =
                dispositivo
                        .authed(E2EConfig.EDGE)
                        .body(
                                Map.of(
                                        "deviceId",
                                        "00000000-0000-0000-0000-0000000000ff",
                                        "agentVersion",
                                        "e2e-suite"))
                        .post("/api/v1/edge/heartbeat")
                        .then()
                        .statusCode(403)
                        .extract()
                        .response();

        assertThat((String) resposta.path("error.code")).isEqualTo("edge.identity-mismatch");
    }

    @Test
    @DisplayName("HB-04 — heartbeat anonimo e recusado com 401")
    void heartbeatAnonimo() {
        anonymous(E2EConfig.EDGE)
                .body(Map.of("agentVersion", "e2e-suite"))
                .post("/api/v1/edge/heartbeat")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("HB-05 — o token do operador nao serve para bater ponto (falta edge.device)")
    void heartbeatComTokenDeOperador() {
        // Um humano coordenador tem edge.manage e edge.read, e mesmo assim não pode se passar por
        // um dispositivo. As duas identidades são separadas de propósito.
        authed(E2EConfig.EDGE)
                .body(Map.of("agentVersion", "e2e-suite"))
                .post("/api/v1/edge/heartbeat")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName(
            "HB-06 — DEFEITO: o ponteiro de perfil vem degradado embora o GEST responda 200 para"
                    + " este mesmo dispositivo")
    void ponteiroDePerfilDegradadoComGestNoAr() {
        // A suíte primeiro prova, por HTTP, que o GEST publica um perfil para este dispositivo.
        Integer versaoNoGest =
                authed(E2EConfig.GEST)
                        .get("/gest-service/detection-profile/devices/{id}", dispositivo.deviceId())
                        .then()
                        .statusCode(200)
                        .extract()
                        .path("version");
        assertThat(versaoNoGest).isNotNull();

        Response heartbeat = baterPonto();

        // …e o EDGE, no mesmo instante e sobre o mesmo dispositivo, diz que não conseguiu falar com
        // o GEST. `degraded=true` significa exatamente "o GEST não respondeu" (DetectionProfilePort
        // §degraded) — e ele respondeu, três linhas acima.
        //
        // Causa: edge-service resolve `neuroassist.gest.url` a partir da variável GEST_URL, que o
        // docker-compose.dev.yml não define no bloco do edge-service (define GEST_SERVICE_URL para
        // outros serviços). O default vira `lb://gest-service`, e um @FeignClient com `url` fixa
        // não passa pelo load balancer — toda chamada falha e o breaker abre. Consequência: o
        // profileDrift construído em E2E-018/033 está morto em runtime para todos os dispositivos.
        //
        // Este cenário documenta a realidade. Quando a variável for corrigida ele fica vermelho —
        // que é o sinal certo de que o defeito acabou.
        assertThat((Boolean) heartbeat.path("profilePointerDegraded"))
                .as(
                        "enquanto GEST_URL nao estiver no ambiente do edge-service, o ponteiro"
                                + " degrada sempre; se isto virar false, o defeito foi corrigido e"
                                + " o cenario deve ser reescrito para exigir a versao %s",
                        versaoNoGest)
                .isTrue();
        assertThat((Object) heartbeat.path("currentProfileVersion")).isNull();
    }

    private static Response baterPonto() {
        return dispositivo
                .authed(E2EConfig.EDGE)
                .body(Map.of("agentVersion", "e2e-suite", "runningProfileVersion", 1))
                .post("/api/v1/edge/heartbeat")
                .then()
                .statusCode(200)
                .extract()
                .response();
    }
}
