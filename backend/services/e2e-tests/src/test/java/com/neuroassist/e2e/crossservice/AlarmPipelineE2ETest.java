package com.neuroassist.e2e.crossservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.e2e.support.E2EConfig;
import com.neuroassist.e2e.support.E2ETestBase;
import com.neuroassist.e2e.support.FixtureFactory;
import io.restassured.response.Response;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * NTF — o caminho completo do alarme (E2E-007): relatório de gesto → evento → despacho de
 * notificação → confirmação da cuidadora.
 *
 * <p>Uma chamada a {@code POST /api/v1/reports} acorda cinco serviços: EVNT recebe, GEST valida os
 * primitivos, PAC congela o rótulo e a urgência do comando, ACS resolve quem é responsável pelo
 * paciente e DASH recebe o despacho. É o cenário mais caro de montar e o que mais paga — nenhum
 * gate de módulo consegue exercitar essa cadeia sem substituir quatro dos cinco por mocks.
 *
 * <p>Determinismo: o {@code reportId} é único por cenário, e o {@code eventoId} devolvido na
 * resposta é o que amarra tudo o que vem depois. Nenhuma asserção fala de "o evento mais recente".
 */
class AlarmPipelineE2ETest extends E2ETestBase {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    private static FixtureFactory.DeviceFixture dispositivo;
    private static FixtureFactory.PatientFixture paciente;
    private static int versaoDoPerfil;

    @BeforeAll
    static void montarCenario() {
        paciente = FixtureFactory.sharedPatient();
        dispositivo = FixtureFactory.sharedDevice();
        // A versão vem do GEST, não de um literal: é ela que o evnt-service repassa ao GEST para
        // validar os primitivos, e congelá-la aqui quebraria a suíte na primeira republicação.
        versaoDoPerfil =
                authed(E2EConfig.GEST)
                        .get("/gest-service/detection-profile/devices/{id}", dispositivo.deviceId())
                        .then()
                        .statusCode(200)
                        .extract()
                        .path("version");
    }

    @Test
    @DisplayName("NTF-01 — um relatorio de gesto vira evento com destinatario resolvido pelo ACS")
    void relatorioViraEventoComDestinatario() {
        Response resposta = enviarRelatorio(novoReportId(), null);

        assertThat(resposta.statusCode()).isEqualTo(201);
        assertThat((String) resposta.path("status")).isEqualTo("created");
        assertThat(UUID.fromString(resposta.path("eventoId"))).isNotNull();

        // O tier vem da urgência que o PAC congelou no snapshot do comando: 'rotina' → discreet.
        // Se o comando da fixture passasse a ser 'emergencia', o esperado seria full_screen_alarm.
        assertThat((String) resposta.path("notification.tier")).isEqualTo("discreet");

        // O destinatário é resolvido no diretório do ACS a partir do vínculo cuidadora↔paciente.
        // Sem esse elo o alarme é criado e não vai a lugar nenhum — daí a asserção ser sobre a
        // identidade exata da cuidadora da fixture, e não sobre "algum destinatário".
        assertThat((String) resposta.path("notification.caregiverId"))
                .isEqualTo(FixtureFactory.cuidadorOperador());

        confirmar(UUID.fromString(resposta.path("eventoId")), "real");
    }

    @Test
    @DisplayName("NTF-02 — reenviar o mesmo reportId devolve o mesmo evento, sem criar outro")
    void relatorioDuplicadoEIdempotente() {
        String reportId = novoReportId();

        Response primeira = enviarRelatorio(reportId, null);
        assertThat(primeira.statusCode()).isEqualTo(201);
        UUID eventoId = UUID.fromString(primeira.path("eventoId"));

        Response segunda = enviarRelatorio(reportId, null);

        // 200 e não 201: o agente de borda que reenvia por falha de rede não pode gerar um segundo
        // alarme para o mesmo gesto.
        assertThat(segunda.statusCode()).isEqualTo(200);
        assertThat((String) segunda.path("status")).isEqualTo("duplicate");
        assertThat(UUID.fromString(segunda.path("eventoId"))).isEqualTo(eventoId);

        confirmar(eventoId, "real");
    }

    @Test
    @DisplayName("NTF-03 — relatorio com quadro biometrico bruto e recusado (LGPD)")
    void relatorioComPayloadBiometricoERecusado() {
        Response resposta = enviarRelatorio(novoReportId(), "quadro-bruto-nao-deve-trafegar");

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat((String) resposta.path("error.code")).isEqualTo("forbidden-biometric-payload");
    }

    @Test
    @DisplayName(
            "NTF-04 — o token do operador nao ingere relatorio; DEFEITO: a recusa sai como 500, nao"
                    + " 403")
    void ingestaoExigeIdentidadeDeDispositivo() {
        // Antes de C-17 este endpoint era anônimo e o tenant era o literal "poc". A metade boa do
        // cenário é que a ingestão continua fechada para quem não tem `edge.device`.
        Response resposta =
                authed(E2EConfig.EVNT)
                        .body(corpoDoRelatorio(novoReportId(), null))
                        .post("/api/v1/reports")
                        .then()
                        .extract()
                        .response();

        // A metade ruim: o GlobalExceptionHandler do evnt-service não trata AccessDeniedException,
        // então uma negativa de autorização vira `500 internal_error.unhandled` com o detalhe
        // "Access Denied". É a armadilha do "guarda que não guarda nada" invertida — o guarda
        // funciona, mas se anuncia como pane. Os outros seis serviços respondem 403 com código
        // estável (ver ISO-09); o consumidor não tem como distinguir "você não pode" de "eu
        // quebrei", e um retry automático em cima de um 5xx é o comportamento errado aqui.
        //
        // Quando o handler for corrigido, este cenário fica vermelho e deve voltar a exigir 403.
        assertThat(resposta.statusCode())
                .as("recusado — mas com o status errado enquanto o handler nao tratar AccessDenied")
                .isEqualTo(500);
        assertThat(resposta.asString()).contains("Access Denied");
        assertThat((String) resposta.path("error.code")).isEqualTo("internal_error.unhandled");
    }

    @Test
    @DisplayName("NTF-05 — a cuidadora confirma o evento que ela mesma recebeu")
    void cuidadoraConfirmaEvento() {
        UUID eventoId = UUID.fromString(enviarRelatorio(novoReportId(), null).path("eventoId"));

        authed(E2EConfig.EVNT)
                .body(Map.of("verdict", "real", "confirmedBy", FixtureFactory.cuidadorOperador()))
                .post("/api/v1/events/{id}/confirmation", eventoId)
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("NTF-06 — reconfirmar com o mesmo veredito e idempotente; divergir e 409 terminal")
    void vereditoETerminal() {
        UUID eventoId = UUID.fromString(enviarRelatorio(novoReportId(), null).path("eventoId"));

        confirmar(eventoId, "real");
        // Mesmo veredito reenviado: silêncio, não erro — a cuidadora que clica duas vezes não deve
        // ver um conflito.
        confirmar(eventoId, "real");

        Response divergente =
                authed(E2EConfig.EVNT)
                        .body(
                                Map.of(
                                        "verdict",
                                        "falso_positivo",
                                        "confirmedBy",
                                        FixtureFactory.cuidadorOperador()))
                        .post("/api/v1/events/{id}/confirmation", eventoId)
                        .then()
                        .statusCode(409)
                        .extract()
                        .response();

        assertThat((String) divergente.path("error.code")).isEqualTo("feedback-terminal");
    }

    @Test
    @DisplayName(
            "NTF-07 — DEFEITO: qualquer UUID pode ser gravado como confirmador; o ACS nao responde"
                    + " 404 para cuidadora inexistente")
    void confirmadorDesconhecidoNaoERecusado() {
        UUID eventoId = UUID.fromString(enviarRelatorio(novoReportId(), null).path("eventoId"));

        // EVNT-023 pôs uma guarda em EventConfirmationService: o confirmador tem de ser uma
        // cuidadora que o ACS conheça. A guarda pergunta ao diretório e trata `FeignException
        // .NotFound` como "não conheço" — o comentário do adapter diz, literalmente, "a caregiver
        // acs-service does not know answers 404".
        //
        // O ACS não responde 404. Responde 200 com a lista de pacientes vazia:
        //   GET /api/v1/acs/directory/caregivers/<uuid-inexistente>/patient-scope
        //   → 200 {"cuidadorId":"…","pacienteIds":[]}
        // Logo `isKnownCaregiver` devolve true para qualquer UUID bem formado e a guarda nunca
        // dispara. Armadilha nº1 do repositório em estado puro: o gate do EVNT fica verde porque o
        // mock do produtor responde 404 — passa exatamente porque a suposição está errada.
        //
        // Consequência: um id arbitrário entra como ator do fato `evento.confirmado`, que é
        // append-only e encadeado por hash no HIST. A atribuição de quem atendeu um alarme fica
        // falsificável.
        String confirmadorInexistente = "00000000-0000-0000-0000-0000000000aa";

        Response noAcs =
                authed(E2EConfig.ACS)
                        .get(
                                "/api/v1/acs/directory/caregivers/{id}/patient-scope",
                                confirmadorInexistente)
                        .then()
                        .extract()
                        .response();
        assertThat(noAcs.statusCode())
                .as("o diretorio do ACS responde 200 para uma cuidadora que nao existe")
                .isEqualTo(200);
        assertThat((List<?>) noAcs.path("pacienteIds")).isEmpty();

        Response confirmacao =
                authed(E2EConfig.EVNT)
                        .body(Map.of("verdict", "real", "confirmedBy", confirmadorInexistente))
                        .post("/api/v1/events/{id}/confirmation", eventoId)
                        .then()
                        .extract()
                        .response();

        // Quando o ACS passar a responder 404 (ou o adapter parar de confiar no status), isto vira
        // um erro `confirmador-unresolved` e o cenario fica vermelho — que e o sinal de correcao.
        assertThat(confirmacao.statusCode())
                .as("a guarda de confirmador nao dispara enquanto o ACS responder 200")
                .isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // Auxiliares — visíveis para AuditTrailE2ETest reaproveitar a mesma cadeia
    // ------------------------------------------------------------------

    static String novoReportId() {
        return "%s-r%03d".formatted(FixtureFactory.runId(), SEQUENCIA.incrementAndGet());
    }

    /**
     * Corpo de {@code ReportIngressUseCase.ReportIngressRequest}, campo a campo.
     *
     * <p>{@code deviceTenant} e {@code patientTenant} existem no record e <b>não</b> são enviados
     * de propósito: o controller sobrescreve o primeiro com o claim do token e faz o segundo cair
     * nele. Mandá-los daqui testaria a suposição do consumidor em vez do contrato real.
     */
    static Map<String, Object> corpoDoRelatorio(String reportId, String quadroBruto) {
        Map<String, Object> corpo = new java.util.LinkedHashMap<>();
        corpo.put("reportId", reportId);
        corpo.put("pacienteId", paciente.pacienteId().toString());
        corpo.put("commandRef", paciente.comandoId().toString());
        corpo.put(
                "primitives",
                List.of(
                        Map.of(
                                "tipoGestoId",
                                FixtureFactory.primitivoPrincipal().toString(),
                                "confidence",
                                0.93)));
        corpo.put("confidence", 0.93);
        corpo.put("profileVersion", versaoDoPerfil);
        corpo.put("recognizedAt", Instant.now().toString());
        corpo.put("deviceId", dispositivo.deviceId().toString());
        if (quadroBruto != null) {
            corpo.put("rawFramePayload", quadroBruto);
        }
        return corpo;
    }

    static Response enviarRelatorio(String reportId, String quadroBruto) {
        return dispositivo
                .authed(E2EConfig.EVNT)
                .body(corpoDoRelatorio(reportId, quadroBruto))
                .post("/api/v1/reports");
    }

    /**
     * Encerra o evento criado por um cenário.
     *
     * <p>Não é limpeza cosmética: {@code GET /api/v1/events/active} devolve eventos ainda não
     * avaliados, e deixar os desta suíte abertos empurraria ruído para o painel do ambiente de dev
     * a cada rodada. Falha de confirmação não derruba o cenário — o que estava sendo provado já foi
     * provado.
     */
    static void confirmar(UUID eventoId, String veredito) {
        try {
            authed(E2EConfig.EVNT)
                    .body(
                            Map.of(
                                    "verdict",
                                    veredito,
                                    "confirmedBy",
                                    FixtureFactory.cuidadorOperador()))
                    .post("/api/v1/events/{id}/confirmation", eventoId);
        } catch (RuntimeException ignorada) {
            // best-effort por construção
        }
    }
}
