package com.neuroassist.e2e.crossservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.e2e.support.Await;
import com.neuroassist.e2e.support.E2EConfig;
import com.neuroassist.e2e.support.E2ETestBase;
import com.neuroassist.e2e.support.FixtureFactory;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AUD — trilha de auditoria (E2E-004).
 *
 * <h2>Por que este grupo estava fora, e o que mudou</h2>
 *
 * <p>A escrita do fato no hist-service é assíncrona e a resposta HTTP do evnt-service não devolve
 * nenhum identificador do fato. A conclusão registrada foi que só restaria {@code sleep} + "existe
 * algum fato recente" — um teste que passa por acidente sob qualquer tráfego concorrente, e o dev é
 * compartilhado.
 *
 * <p>A conclusão estava errada por um detalhe: <b>a correlação existe, só não está no envelope</b>.
 * {@code ReportIngressService} grava o fato {@code evento.criado} com {@code payload.eventoId} = o
 * id que a resposta HTTP acabou de devolver, e com {@code actor.caregiverId} = o id do dispositivo
 * que reportou. O primeiro é único no universo; o segundo é único desta rodada, porque o
 * dispositivo é criado pela {@link FixtureFactory}. O predicado do polling, portanto, identifica
 * <i>aquele</i> fato — não "um fato dos últimos N segundos".
 *
 * <p>Consequência prática: sob tráfego concorrente este cenário continua correto. Se ele ficar
 * vermelho, ou o fato não foi escrito, ou foi escrito com outra correlação.
 */
class AuditTrailE2ETest extends E2ETestBase {

    /**
     * Prazo do outbox EVNT→HIST. Generoso de propósito: o custo de esperar é menor que o de piscar.
     */
    private static final Duration PRAZO_OUTBOX = Duration.ofSeconds(30);

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    private static FixtureFactory.DeviceFixture dispositivo;
    private static FixtureFactory.PatientFixture paciente;
    private static int versaoDoPerfil;

    @BeforeAll
    static void montarCenario() {
        paciente = FixtureFactory.sharedPatient();
        dispositivo = FixtureFactory.sharedDevice();
        versaoDoPerfil =
                authed(E2EConfig.GEST)
                        .get("/gest-service/detection-profile/devices/{id}", dispositivo.deviceId())
                        .then()
                        .statusCode(200)
                        .extract()
                        .path("version");
    }

    @Test
    @DisplayName("AUD-01 — o fato evento.criado chega ao HIST correlacionado ao evento e ao device")
    void fatoDeEventoCriadoChegaCorrelacionado() {
        UUID eventoId = criarEvento();

        Map<String, Object> fato =
                aguardarFato("evento.criado", eventoId, "fato evento.criado do evento " + eventoId);

        // Duas correlações independentes: o evento que a resposta HTTP nomeou, e o dispositivo que
        // esta rodada criou. As duas juntas excluem qualquer fato produzido por tráfego alheio.
        assertThat(payload(fato).get("eventoId")).isEqualTo(eventoId.toString());
        assertThat(((Map<?, ?>) fato.get("actor")).get("caregiverId"))
                .isEqualTo(dispositivo.deviceId().toString());

        // O subject é o paciente, não o evento: o HIST organiza a trilha por sujeito (ADR-005).
        assertThat(((Map<?, ?>) fato.get("subject")).get("type")).isEqualTo("paciente");
        assertThat(((Map<?, ?>) fato.get("subject")).get("id"))
                .isEqualTo(paciente.pacienteId().toString());

        // O snapshot do comando veio do PAC pela mão do EVNT — outro serviço no caminho do fato.
        assertThat(payload(fato).get("comandoRef")).isEqualTo(paciente.comandoId().toString());

        confirmar(eventoId);
    }

    @Test
    @DisplayName("AUD-02 — o fato entra na cadeia de hash do sujeito, com sequencia e elo anterior")
    void fatoEntraNaCadeiaDeHash() {
        UUID eventoId = criarEvento();
        Map<String, Object> fato = aguardarFato("evento.criado", eventoId, "fato do " + eventoId);

        assertThat((String) fato.get("streamKey")).isEqualTo("paciente:" + paciente.pacienteId());
        assertThat(((Number) fato.get("sequenceNo")).longValue()).isPositive();
        assertThat((String) fato.get("recordHash")).isNotBlank();
        // prevHash só é nulo no primeiro elo do stream; o paciente da fixture já tem histórico
        // porque AUD-01 rodou antes ou porque rodadas anteriores gravaram nele.
        assertThat(fato.get("recordHash")).isNotEqualTo(fato.get("prevHash"));

        confirmar(eventoId);
    }

    @Test
    @DisplayName("AUD-03 — confirmar o evento grava o fato evento.confirmado, mesmo eventoId")
    void confirmacaoGravaSeuProprioFato() {
        UUID eventoId = criarEvento();
        // Espera o fato de criação antes de confirmar: os dois fatos vão para o mesmo stream, e
        // confirmar antes só embaralharia a ordem sem provar nada a mais.
        aguardarFato("evento.criado", eventoId, "fato de criacao do " + eventoId);

        authed(E2EConfig.EVNT)
                .body(Map.of("verdict", "real", "confirmedBy", FixtureFactory.cuidadorOperador()))
                .post("/api/v1/events/{id}/confirmation", eventoId)
                .then()
                .statusCode(200);

        Map<String, Object> fato =
                aguardarFato(
                        "evento.confirmado", eventoId, "fato evento.confirmado do " + eventoId);

        assertThat(payload(fato).get("feedback")).isEqualTo("REAL");
        // Autoria e autorização são coisas diferentes: a chamada é autenticada como evnt-service,
        // mas o ator do fato é a cuidadora.
        assertThat(((Map<?, ?>) fato.get("actor")).get("caregiverId"))
                .isEqualTo(FixtureFactory.cuidadorOperador());
    }

    @Test
    @DisplayName("AUD-04 — o mesmo fato lido por id devolve registro identico")
    void fatoLidoPorIdEIdentico() {
        UUID eventoId = criarEvento();
        Map<String, Object> daTimeline =
                aguardarFato("evento.criado", eventoId, "fato do " + eventoId);
        String registroId = (String) daTimeline.get("id");

        Response porId =
                authed(E2EConfig.HIST)
                        .get("/api/v1/hist/audit-facts/{id}", registroId)
                        .then()
                        .statusCode(200)
                        .extract()
                        .response();

        assertThat((String) porId.path("recordHash")).isEqualTo(daTimeline.get("recordHash"));
        assertThat((String) porId.path("factId")).isEqualTo(daTimeline.get("factId"));

        confirmar(eventoId);
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    /** Ingere um relatório novo e devolve o id do evento — a âncora de toda correlação daqui. */
    private static UUID criarEvento() {
        Map<String, Object> corpo = new java.util.LinkedHashMap<>();
        corpo.put(
                "reportId",
                "%s-a%03d".formatted(FixtureFactory.runId(), SEQUENCIA.incrementAndGet()));
        corpo.put("pacienteId", paciente.pacienteId().toString());
        corpo.put("commandRef", paciente.comandoId().toString());
        corpo.put(
                "primitives",
                List.of(
                        Map.of(
                                "tipoGestoId",
                                FixtureFactory.primitivoPrincipal().toString(),
                                "confidence",
                                0.91)));
        corpo.put("confidence", 0.91);
        corpo.put("profileVersion", versaoDoPerfil);
        corpo.put("recognizedAt", Instant.now().toString());
        corpo.put("deviceId", dispositivo.deviceId().toString());

        Response resposta =
                dispositivo
                        .authed(E2EConfig.EVNT)
                        .body(corpo)
                        .post("/api/v1/reports")
                        .then()
                        .statusCode(201)
                        .extract()
                        .response();
        return UUID.fromString(resposta.path("eventoId"));
    }

    /**
     * Poll sobre um predicado específico daquele fato: mesmo tipo, mesmo sujeito e {@code
     * payload.eventoId} igual ao evento que a resposta HTTP devolveu.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> aguardarFato(
            String factType, UUID eventoId, String descricao) {
        return Await.untilPresent(
                descricao,
                PRAZO_OUTBOX,
                () -> {
                    Response resposta =
                            authed(E2EConfig.HIST)
                                    .queryParam("subjectType", "paciente")
                                    .queryParam("subjectId", paciente.pacienteId().toString())
                                    .queryParam("factType", factType)
                                    .queryParam("size", 50)
                                    .get("/api/v1/hist/history");
                    if (resposta.statusCode() != 200) {
                        return null;
                    }
                    JsonPath json = resposta.jsonPath();
                    List<Map<String, Object>> itens = json.getList("items");
                    if (itens == null) {
                        return null;
                    }
                    return itens.stream()
                            .filter(
                                    item ->
                                            eventoId.toString()
                                                    .equals(payload(item).get("eventoId")))
                            .findFirst()
                            .orElse(null);
                });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(Map<String, Object> fato) {
        Object payload = fato.get("payload");
        return payload instanceof Map ? (Map<String, Object>) payload : Map.of();
    }

    private static void confirmar(UUID eventoId) {
        try {
            authed(E2EConfig.EVNT)
                    .body(
                            Map.of(
                                    "verdict",
                                    "real",
                                    "confirmedBy",
                                    FixtureFactory.cuidadorOperador()))
                    .post("/api/v1/events/{id}/confirmation", eventoId);
        } catch (RuntimeException ignorada) {
            // best-effort: encerrar o evento é higiene do ambiente, não a asserção do cenário
        }
    }
}
