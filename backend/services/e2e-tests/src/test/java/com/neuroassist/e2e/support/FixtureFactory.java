package com.neuroassist.e2e.support;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monta, <b>pelas APIs públicas</b>, o cenário que os fluxos HB / NTF / AUD / DSH / LGPD exigem e
 * que o seed de dev não tem: um dispositivo com credencial própria, um paciente com comando
 * publicado, e o pareamento entre os dois.
 *
 * <h2>Isolamento, não limpeza (a decisão central)</h2>
 *
 * <p>A versão anterior da suíte não criava nada, e a justificativa registrada era boa: um teardown
 * em 4 serviços que falha pela metade degrada o ambiente de dev a cada rodada. Esta factory paga a
 * dívida sem reintroduzir o problema, escolhendo <b>dado próprio e reencontrável</b> em vez de dado
 * novo que precisa ser apagado:
 *
 * <ul>
 *   <li><b>Paciente</b> — criado com um {@code Idempotency-Key} <i>fixo</i> ({@link
 *       #CHAVE_IDEMPOTENCIA_PACIENTE}). Na primeira rodada o PAC cria; em toda rodada seguinte ele
 *       responde 200 com o mesmo recurso. Zero crescimento, zero teardown.
 *   <li><b>Comando</b> — procurado no catálogo ativo pelo rótulo {@link #ROTULO_COMANDO}; só é
 *       criado (draft → validate → publish) quando não existe. Mesmo efeito.
 *   <li><b>Dispositivo</b> — este <i>é</i> novo a cada rodada, porque o {@code clientSecret} só
 *       existe na resposta do enroll e não pode ser recuperado depois. É aposentado no encerramento
 *       da JVM; um dispositivo aposentado é inerte (não consegue mais token). Custo assumido: uma
 *       linha por rodada em {@code dispositivo_edge}, sempre rotulada com {@link #runId()}.
 * </ul>
 *
 * <p><b>Nada do seed é mutado.</b> {@code coord@neuroassist.dev}, o dispositivo e o paciente
 * semeados (ver {@link DevFixtures}) são só lidos. Em particular o pareamento — que é exclusivo por
 * paciente — nunca é aplicado sobre o paciente do seed, o que roubaria o dispositivo dos cenários
 * DEV-* existentes.
 *
 * <h2>Falha rápida e legível</h2>
 *
 * <p>Todo passo passa por {@link #passo}, que em status inesperado levanta {@link FixtureException}
 * dizendo qual passo, contra qual serviço, em qual URL e com qual corpo. Sem isso, um EDGE fora do
 * ar apareceria como "expected 200 but was 000" na primeira asserção de um teste que nem chegou a
 * exercitar o produto.
 */
public final class FixtureFactory {

    private static final Logger log = LoggerFactory.getLogger(FixtureFactory.class);

    /**
     * Chave de idempotência fixa do paciente compartilhado.
     *
     * <p>É deliberadamente estável entre rodadas — é ela que troca "criar e depois apagar" por
     * "criar uma vez e reencontrar". Mudar este literal cria um <i>novo</i> paciente; o {@code v1}
     * no fim existe para tornar essa decisão explícita quando o corpo da requisição mudar (o PAC
     * compara o hash do corpo, então chave igual com corpo diferente não é replay).
     */
    private static final String CHAVE_IDEMPOTENCIA_PACIENTE = "e2e-suite-shared-patient-v1";

    private static final String NOME_PACIENTE = "E2E Suite Shared Patient";
    private static final String ROTULO_COMANDO = "E2E Suite Comando";

    /** Prazo de propagação para leituras que atravessam serviço (catálogo, diretório). */
    private static final java.time.Duration PRAZO_PROPAGACAO = java.time.Duration.ofSeconds(20);

    private static final String RUN_ID = novoRunId();

    private static String operatorToken;
    private static PatientFixture pacienteCompartilhado;
    private static DeviceFixture dispositivoCompartilhado;
    private static UUID primitivoPrincipal;

    private FixtureFactory() {}

    // ------------------------------------------------------------------
    // Tipos
    // ------------------------------------------------------------------

    /**
     * Dispositivo de borda com credencial própria.
     *
     * <p>Os nomes espelham {@code EnrollResponse} do edge-service e {@code AutenticacaoResponse} do
     * acs-service. O {@code accessToken} é o token de <i>device</i> (authority {@code
     * edge.device}), que é uma identidade diferente da do operador — é justamente essa diferença
     * que os cenários de heartbeat e de ingestão de relatório exercitam.
     */
    public record DeviceFixture(
            UUID deviceId, String clientId, String clientSecret, String accessToken, String label) {

        /** Requisição autenticada como o dispositivo (não como o operador). */
        public RequestSpecification authed(String baseUri) {
            return given().baseUri(baseUri)
                    .contentType(ContentType.JSON)
                    .header("Authorization", "Bearer " + accessToken);
        }
    }

    /** Paciente da suíte, com o comando publicado que o relatório de gesto referencia. */
    public record PatientFixture(UUID pacienteId, String nome, UUID comandoId) {}

    // ------------------------------------------------------------------
    // Identidade da rodada
    // ------------------------------------------------------------------

    /**
     * Identificador único desta execução da JVM de teste.
     *
     * <p>Vai no rótulo do dispositivo e no nome do paciente descartável, para que qualquer linha
     * deixada para trás seja atribuível a uma rodada específica em vez de ser lixo anônimo.
     */
    public static String runId() {
        return RUN_ID;
    }

    private static String novoRunId() {
        return "e2e-"
                + Instant.now().toString().replaceAll("[:.]", "").substring(0, 15)
                + "-"
                + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x10000, 0xFFFFF));
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    /** Token do operador de dev, obtido uma vez por JVM. */
    public static synchronized String operatorToken() {
        if (operatorToken == null) {
            try {
                operatorToken = AuthClient.login(E2EConfig.LOGIN, E2EConfig.SENHA).accessToken();
            } catch (RuntimeException e) {
                throw new FixtureException(
                        ("passo=login-operador servico=acs url=%s/oauth2/token login=%s — o"
                                        + " backbone de autenticacao nao respondeu; sem token"
                                        + " nenhuma fixture pode ser montada")
                                .formatted(E2EConfig.ACS, E2EConfig.LOGIN),
                        e);
            }
        }
        return operatorToken;
    }

    /**
     * Paciente da suíte, com uma cuidadora responsável e um comando publicado.
     *
     * <p>A cuidadora é o próprio operador do login: o {@code sub} do token. É o que faz o paciente
     * aparecer em {@code /panel/summary} do dash-service e o que dá destino às notificações do
     * evnt-service — sem vínculo, o alarme é criado e não vai a lugar nenhum.
     *
     * <p>E2E-028: o PAC recusa 422 ao esvaziar a lista da última cuidadora. Por isso o vínculo
     * nunca é desfeito no teardown — e é também por isso que o paciente é <i>reutilizado</i> em vez
     * de recriado: um paciente novo por rodada ficaria para sempre no painel da cuidadora.
     */
    public static synchronized PatientFixture sharedPatient() {
        if (pacienteCompartilhado == null) {
            UUID pacienteId = criarOuReencontrarPaciente();
            UUID comandoId = comandoPublicado(pacienteId);
            pacienteCompartilhado = new PatientFixture(pacienteId, NOME_PACIENTE, comandoId);
            log.info(
                    "Fixture: paciente compartilhado {} com comando publicado {}",
                    pacienteId,
                    comandoId);
        }
        return pacienteCompartilhado;
    }

    /**
     * Dispositivo de borda registrado, matriculado, com token próprio e pareado ao {@link
     * #sharedPatient()}.
     *
     * <p>A cadeia é a real: o operador registra (EDGE), o agente troca o token de matrícula por uma
     * credencial (EDGE, anônimo), o ACS pergunta ao EDGE se a credencial vale e assina um token de
     * dispositivo. Três serviços, nenhum mock — é exatamente o caminho que o gate de módulo não
     * consegue exercitar.
     */
    public static synchronized DeviceFixture sharedDevice() {
        if (dispositivoCompartilhado == null) {
            // A atribuição vem depois do pareamento de propósito. Publicar a fixture antes de ela
            // estar completa faria a próxima classe de teste receber um dispositivo não pareado do
            // cache e falhar por um motivo que não é o dela — o erro apareceria três cenários
            // adiante do lugar onde nasceu.
            DeviceFixture novo = novoDispositivo("compartilhado");
            parear(novo.deviceId(), sharedPatient().pacienteId());
            dispositivoCompartilhado = novo;
            log.info(
                    "Fixture: dispositivo compartilhado {} pareado ao paciente {}",
                    novo.deviceId(),
                    sharedPatient().pacienteId());
        }
        return dispositivoCompartilhado;
    }

    /**
     * Paciente novo a cada chamada, para cenários cujo efeito é <b>terminal</b> — o apagamento LGPD
     * é o caso: uma vez anonimizado, o paciente não serve para provar a transição de novo.
     *
     * <p>Diferente do compartilhado, este cresce uma linha por rodada. É o preço de cobrir um fluxo
     * irreversível sem tocar em nada do seed; o nome carrega {@link #runId()} para que a origem de
     * cada linha seja evidente.
     */
    public static PatientFixture disposablePatient(String proposito) {
        String nome = "E2E %s %s".formatted(proposito, runId());
        Response resposta =
                passo(
                        "criar-paciente-descartavel",
                        "pac",
                        E2EConfig.PAC + "/api/pac/v1/pacientes",
                        () ->
                                operador(E2EConfig.PAC)
                                        .body(
                                                Map.of(
                                                        "nome",
                                                        nome,
                                                        "cuidadorResponsavelIds",
                                                        List.of(cuidadorOperador())))
                                        .post("/api/pac/v1/pacientes"),
                        200,
                        201);
        UUID id = UUID.fromString(resposta.path("id"));
        log.info("Fixture: paciente descartavel {} ({})", id, nome);
        return new PatientFixture(id, nome, null);
    }

    /**
     * Primeiro tipo de gesto ativo do GEST — o primitivo que o comando da suíte referencia.
     *
     * <p>Descoberto, não fixado: um id de primitivo escrito à mão aqui seria mais uma suposição do
     * consumidor sobre o produtor, que é a armadilha catalogada em plan-e2e-integration.md §11.
     */
    public static synchronized UUID primitivoPrincipal() {
        if (primitivoPrincipal == null) {
            Response resposta =
                    passo(
                            "listar-tipos-de-gesto",
                            "gest",
                            E2EConfig.GEST + "/gest-service/tipo-gesto",
                            () -> operador(E2EConfig.GEST).get("/gest-service/tipo-gesto"),
                            200);
            List<String> ids = resposta.path("id");
            if (ids == null || ids.isEmpty()) {
                throw new FixtureException(
                        ("passo=listar-tipos-de-gesto servico=gest"
                                        + " url=%s/gest-service/tipo-gesto — o tenant nao tem"
                                        + " nenhum tipo de gesto ativo; sem primitivo nao ha"
                                        + " comando publicavel")
                                .formatted(E2EConfig.GEST));
            }
            primitivoPrincipal = UUID.fromString(ids.get(0));
        }
        return primitivoPrincipal;
    }

    /** Id da cuidadora que autenticou — o {@code sub} do token do operador. */
    public static String cuidadorOperador() {
        return AuthClient.claim(operatorToken(), "sub");
    }

    // ------------------------------------------------------------------
    // Operações reutilizáveis
    // ------------------------------------------------------------------

    /** Registra + matricula um dispositivo novo e obtém o token de device. */
    public static DeviceFixture novoDispositivo(String proposito) {
        String label = "e2e-%s-%s".formatted(proposito, runId());

        Response registro =
                passo(
                        "registrar-dispositivo",
                        "edge",
                        E2EConfig.EDGE + "/api/v1/edge/devices",
                        () ->
                                operador(E2EConfig.EDGE)
                                        .body(Map.of("label", label))
                                        .post("/api/v1/edge/devices"),
                        201);
        UUID deviceId = UUID.fromString(registro.path("deviceId"));
        String enrollmentToken = registro.path("enrollmentToken");

        // O enroll é anônimo de propósito: o agente de borda ainda não tem credencial nenhuma,
        // e o que o autoriza é o token de matrícula de uso único. Mandar Bearer aqui mascararia
        // uma regressão que tornasse o endpoint autenticado.
        Response enroll =
                passo(
                        "matricular-dispositivo",
                        "edge",
                        E2EConfig.EDGE + "/api/v1/edge/devices/enroll",
                        () ->
                                given().baseUri(E2EConfig.EDGE)
                                        .contentType(ContentType.JSON)
                                        .body(
                                                Map.of(
                                                        "enrollmentToken",
                                                        enrollmentToken,
                                                        "agentVersion",
                                                        "e2e-suite"))
                                        .post("/api/v1/edge/devices/enroll"),
                        200);
        String clientId = enroll.path("clientId");
        String clientSecret = enroll.path("clientSecret");

        Response token =
                passo(
                        "emitir-token-de-dispositivo",
                        "acs",
                        E2EConfig.ACS + "/oauth2/token/device",
                        () ->
                                given().baseUri(E2EConfig.ACS)
                                        .contentType(ContentType.JSON)
                                        .body(
                                                Map.of(
                                                        "clientId", clientId,
                                                        "clientSecret", clientSecret))
                                        .post("/oauth2/token/device"),
                        200);

        return new DeviceFixture(
                deviceId, clientId, clientSecret, token.path("accessToken"), label);
    }

    /**
     * Pareia dispositivo↔paciente. O pareamento é exclusivo: um paciente, um dispositivo.
     *
     * <p>Como o paciente da suíte é reaproveitado entre rodadas e o dispositivo não, o titular do
     * pareamento é sempre o dispositivo da <i>rodada anterior</i> — que já foi aposentado, mas cujo
     * vínculo o EDGE mantém. O 409 {@code edge.pairing-conflict} é portanto o caso normal, não a
     * exceção: desfaz-se o vínculo antigo e tenta-se de novo, uma vez.
     *
     * <p>O desligamento só alcança dispositivos pareados <b>ao paciente da suíte</b>. O paciente do
     * seed nunca chega aqui, e é essa restrição que impede a factory de roubar o dispositivo dos
     * cenários DEV-*.
     */
    public static void parear(UUID deviceId, UUID pacienteId) {
        Response primeira =
                operador(E2EConfig.EDGE)
                        .body(Map.of("pacienteId", pacienteId.toString()))
                        .post("/api/v1/edge/devices/{id}/pairings", deviceId);
        if (primeira.statusCode() == 200 || primeira.statusCode() == 201) {
            return;
        }
        if (primeira.statusCode() == 409
                && String.valueOf(primeira.asString()).contains("pairing-conflict")) {
            desparearTitular(pacienteId);
            passo(
                    "parear-dispositivo-apos-liberar-o-titular",
                    "edge",
                    E2EConfig.EDGE + "/api/v1/edge/devices/" + deviceId + "/pairings",
                    () ->
                            operador(E2EConfig.EDGE)
                                    .body(Map.of("pacienteId", pacienteId.toString()))
                                    .post("/api/v1/edge/devices/{id}/pairings", deviceId),
                    200,
                    201);
            return;
        }
        throw new FixtureException(
                "passo=parear-dispositivo servico=edge url=%s/api/v1/edge/devices/%s/pairings —"
                                .formatted(E2EConfig.EDGE, deviceId)
                        + " esperava HTTP [200, 201], veio %d: %s"
                                .formatted(primeira.statusCode(), resumo(primeira)));
    }

    /** Desfaz o pareamento vigente do paciente, se houver — silencioso quando não há. */
    private static void desparearTitular(UUID pacienteId) {
        Response atual =
                operador(E2EConfig.EDGE).get("/api/v1/edge/patients/{p}/device", pacienteId);
        if (atual.statusCode() != 200) {
            return;
        }
        String titular = atual.path("deviceId");
        if (titular == null) {
            return;
        }
        log.info(
                "Fixture: liberando o pareamento do paciente {} — dispositivo {} de uma rodada"
                        + " anterior",
                pacienteId,
                titular);
        operador(E2EConfig.EDGE).delete("/api/v1/edge/devices/{id}/pairings/current", titular);
    }

    /**
     * Aposenta um dispositivo — o único teardown que a factory executa, e por isso o único que
     * precisa ser idempotente.
     *
     * <p>{@code 409 edge.device-retired} é sucesso: o estado desejado já vale. Qualquer outra falha
     * vira WARN e não interrompe nada — um teardown que explode no fim da suíte transformaria uma
     * rodada verde em vermelha sem que nenhum comportamento do produto tivesse mudado.
     */
    public static void aposentar(UUID deviceId) {
        try {
            Response r =
                    operador(E2EConfig.EDGE).post("/api/v1/edge/devices/{id}:retire", deviceId);
            if (r.statusCode() == 200 || ehJaAposentado(r)) {
                log.info("Teardown: dispositivo {} aposentado (HTTP {})", deviceId, r.statusCode());
            } else {
                log.warn(
                        "Teardown: nao consegui aposentar o dispositivo {} — HTTP {} {}",
                        deviceId,
                        r.statusCode(),
                        r.asString());
            }
        } catch (RuntimeException e) {
            log.warn(
                    "Teardown: nao consegui aposentar o dispositivo {}: {}",
                    deviceId,
                    e.toString());
        }
    }

    private static boolean ehJaAposentado(Response r) {
        return r.statusCode() == 409 && String.valueOf(r.asString()).contains("device-retired");
    }

    // ------------------------------------------------------------------
    // Internos
    // ------------------------------------------------------------------

    private static UUID criarOuReencontrarPaciente() {
        Response resposta =
                passo(
                        "criar-ou-reencontrar-paciente-compartilhado",
                        "pac",
                        E2EConfig.PAC + "/api/pac/v1/pacientes",
                        () ->
                                operador(E2EConfig.PAC)
                                        .header("Idempotency-Key", CHAVE_IDEMPOTENCIA_PACIENTE)
                                        .body(
                                                Map.of(
                                                        "nome",
                                                        NOME_PACIENTE,
                                                        "cuidadorResponsavelIds",
                                                        List.of(cuidadorOperador())))
                                        .post("/api/pac/v1/pacientes"),
                        200,
                        201);
        return UUID.fromString(resposta.path("id"));
    }

    /** Reencontra o comando da suíte no catálogo ativo; publica um novo só se não houver. */
    private static UUID comandoPublicado(UUID pacienteId) {
        UUID existente = comandoNoCatalogo(pacienteId);
        if (existente != null) {
            return existente;
        }

        Response draft =
                passo(
                        "criar-rascunho-de-comando",
                        "pac",
                        E2EConfig.PAC + "/api/pac/v1/pacientes/" + pacienteId + "/comandos",
                        () ->
                                operador(E2EConfig.PAC)
                                        .body(
                                                Map.of(
                                                        "rotulo",
                                                        ROTULO_COMANDO,
                                                        "necessidade",
                                                        "suite e2e",
                                                        "primitivoPrincipalId",
                                                        primitivoPrincipal().toString(),
                                                        "sensibilidade",
                                                        "media",
                                                        "tempoMinGestoMs",
                                                        300,
                                                        "tempoMaxGestoMs",
                                                        2000,
                                                        "urgencia",
                                                        "rotina"))
                                        .post("/api/pac/v1/pacientes/{p}/comandos", pacienteId),
                        201);
        UUID comandoId = UUID.fromString(draft.path("id"));

        // drafted → validated → published. Pular o :validate devolve 409
        // pac.comando.invalid-lifecycle-transition; o ciclo de vida não aceita atalho.
        passo(
                "validar-comando",
                "pac",
                E2EConfig.PAC + "/api/pac/v1/pacientes/" + pacienteId + "/comandos/" + comandoId,
                () ->
                        operador(E2EConfig.PAC)
                                .body(Map.of())
                                .post(
                                        "/api/pac/v1/pacientes/{p}/comandos/{c}:validate",
                                        pacienteId,
                                        comandoId),
                200);
        passo(
                "publicar-comando",
                "pac",
                E2EConfig.PAC + "/api/pac/v1/pacientes/" + pacienteId + "/comandos/" + comandoId,
                () ->
                        operador(E2EConfig.PAC)
                                .body(Map.of())
                                .post(
                                        "/api/pac/v1/pacientes/{p}/comandos/{c}:publish",
                                        pacienteId,
                                        comandoId),
                200);

        UUID publicado =
                Await.untilPresent(
                        "comando '%s' no catalogo ativo do paciente %s"
                                .formatted(ROTULO_COMANDO, pacienteId),
                        PRAZO_PROPAGACAO,
                        () -> comandoNoCatalogo(pacienteId));
        return publicado != null ? publicado : comandoId;
    }

    /** Devolve o id do comando da suíte no catálogo ativo, ou {@code null} se ainda não está lá. */
    private static UUID comandoNoCatalogo(UUID pacienteId) {
        Response resposta =
                passo(
                        "ler-catalogo-ativo",
                        "pac",
                        E2EConfig.PAC + "/api/pac/v1/pacientes/" + pacienteId + "/catalogo-ativo",
                        () ->
                                operador(E2EConfig.PAC)
                                        .get(
                                                "/api/pac/v1/pacientes/{p}/catalogo-ativo",
                                                pacienteId),
                        200);
        List<String> rotulos = resposta.path("comandos.rotulo");
        List<String> ids = resposta.path("comandos.id");
        if (rotulos == null) {
            return null;
        }
        int indice = rotulos.indexOf(ROTULO_COMANDO);
        return indice < 0 ? null : UUID.fromString(ids.get(indice));
    }

    private static RequestSpecification operador(String baseUri) {
        return given().baseUri(baseUri)
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + operatorToken())
                .header(E2EConfig.TENANT_HEADER, E2EConfig.TENANT);
    }

    /**
     * Executa um passo da montagem e falha alto quando o status não é um dos esperados.
     *
     * <p>A mensagem responde as quatro perguntas de um diagnóstico: qual passo, contra qual
     * serviço, em qual URL, e o que o serviço respondeu.
     */
    private static Response passo(
            String nome,
            String servico,
            String url,
            Supplier<Response> chamada,
            int... statusEsperados) {
        Response resposta;
        try {
            resposta = chamada.get();
        } catch (RuntimeException e) {
            throw new FixtureException(
                    "passo=%s servico=%s url=%s — a chamada nem completou (%s)"
                            .formatted(nome, servico, url, e.toString()),
                    e);
        }
        for (int esperado : statusEsperados) {
            if (resposta.statusCode() == esperado) {
                return resposta;
            }
        }
        throw new FixtureException(
                "passo=%s servico=%s url=%s — esperava HTTP %s, veio %d: %s"
                        .formatted(
                                nome,
                                servico,
                                url,
                                Arrays.toString(statusEsperados),
                                resposta.statusCode(),
                                resumo(resposta)));
    }

    private static String resumo(Response resposta) {
        String corpo = String.valueOf(resposta.asString());
        return corpo.length() > 600 ? corpo.substring(0, 600) + "…" : corpo;
    }

    // ------------------------------------------------------------------
    // Teardown
    // ------------------------------------------------------------------

    static {
        // Gancho de encerramento em vez de @AfterAll: o dispositivo é compartilhado por várias
        // classes de teste e nenhuma delas é dona do seu ciclo de vida. O gancho roda uma vez,
        // depois da última, e é best-effort por construção.
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    if (dispositivoCompartilhado != null) {
                                        aposentar(dispositivoCompartilhado.deviceId());
                                    }
                                },
                                "e2e-fixture-teardown"));
    }
}
