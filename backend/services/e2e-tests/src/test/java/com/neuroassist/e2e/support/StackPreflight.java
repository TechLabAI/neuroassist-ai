package com.neuroassist.e2e.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifica, uma única vez por JVM, que os 7 serviços respondem {@code /actuator/health}.
 *
 * <p>Por que isto existe: sem ele, a primeira asserção de cada teste falharia com "Connection
 * refused" — uma mensagem que não diz nem qual serviço caiu nem o que fazer. Aqui o diagnóstico sai
 * completo e uma vez só, listando exatamente quais portas não responderam e o comando para levantar
 * o stack.
 *
 * <p>Comportamento default é <b>skip</b> (via {@link Assumptions#abort}), não falha: quem roda
 * `./gradlew build` sem Docker não deve ver vermelho por um ambiente que nunca prometeu ter. Mas o
 * skip é barulhento — banner em nível ERROR — e o CI inverte a política com {@code
 * -De2e.requireStack=true}.
 */
public final class StackPreflight {

    private static final Logger log = LoggerFactory.getLogger(StackPreflight.class);

    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    /** Resultado calculado no primeiro uso e reaproveitado — o health check não precisa repetir. */
    private static volatile String failureReport;

    private static volatile boolean checked;

    private StackPreflight() {}

    /** Chamado no {@code @BeforeAll} de {@link E2ETestBase}. */
    public static synchronized void ensureStackIsUp() {
        if (!checked) {
            failureReport = probeAll();
            checked = true;
        }
        if (failureReport == null) {
            return;
        }
        log.error("\n{}", failureReport);
        if (E2EConfig.REQUIRE_STACK) {
            throw new IllegalStateException(
                    "Stack de dev indisponivel e -De2e.requireStack=true. Veja o relatorio acima.");
        }
        Assumptions.abort("Stack de dev indisponivel — suite E2E pulada. Veja o log ERROR acima.");
    }

    /**
     * @return {@code null} se tudo está no ar; senão, o relatório pronto para log.
     */
    private static String probeAll() {
        Map<String, String> targets = new LinkedHashMap<>();
        targets.put("acs-service", E2EConfig.ACS);
        targets.put("pac-service", E2EConfig.PAC);
        targets.put("gest-service", E2EConfig.GEST);
        targets.put("edge-service", E2EConfig.EDGE);
        targets.put("hist-service", E2EConfig.HIST);
        targets.put("evnt-service", E2EConfig.EVNT);
        targets.put("dash-service", E2EConfig.DASH);

        List<String> down = new ArrayList<>();
        targets.forEach(
                (name, base) -> {
                    String verdict = probe(base + "/actuator/health");
                    if (verdict != null) {
                        down.add("  - %-14s %s  ->  %s".formatted(name, base, verdict));
                    }
                });
        if (down.isEmpty()) {
            log.info("Preflight OK — os 7 servicos responderam /actuator/health.");
            return null;
        }
        return """
               =========================================================================
                SUITE E2E NAO PODE RODAR — o stack de dev nao esta respondendo.
               =========================================================================
                Servicos fora do ar (%d de %d):
               %s

                Para levantar:
                  docker compose -f docker/docker-compose.dev.yml up -d

                Para apontar para outro ambiente:
                  ./gradlew :services:e2e-tests:e2eTest -De2e.acs.url=https://... (etc.)

                Para transformar este skip em FALHA (recomendado no CI):
                  -De2e.requireStack=true
               =========================================================================\
               """
                .formatted(down.size(), targets.size(), String.join("\n", down));
    }

    /**
     * @return {@code null} quando saudável; senão a razão legível da reprovação.
     */
    private static String probe(String healthUrl) {
        try {
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(healthUrl))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build();
            HttpResponse<String> response =
                    HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return "HTTP " + response.statusCode();
            }
            // O actuator responde 200 com status DOWN em algumas configuracoes de grupo;
            // por isso o corpo tambem e inspecionado, e nao so o codigo.
            if (!response.body().contains("\"status\":\"UP\"")) {
                return "health != UP: " + response.body();
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrompido";
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }
}
