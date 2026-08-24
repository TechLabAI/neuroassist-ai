package com.neuroassist.pac.bdd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;

/**
 * Per-scenario shared state for BDD step definitions (PAC-025, P9).
 *
 * <p>Singleton Spring bean — reset before each scenario via {@link BddHooks#beforeScenario()}.
 * Holds the HTTP response, current JWT, active headers, and test entity alias map.
 *
 * <p>Entity aliases allow feature files to use human-readable IDs like {@code "pac-001"}, {@code
 * "cmd-pub"} while the actual UUIDs are tracked here.
 *
 * <p>Not annotated with @Component — registered exclusively via {@link BddTestConfiguration}.
 * Spring applies @Value injection via AutowiredAnnotationBeanPostProcessor on the returned bean.
 */
public class ScenarioContext {

    /** Dynamic port of the running Spring Boot test server. */
    @Getter
    @Value("${local.server.port:8080}")
    private int localServerPort;

    /** HTTP response from the last {@code When} step. */
    @Getter @Setter private ResponseEntity<String> lastResponse;

    /** Signed JWT string for the current test scenario. */
    @Getter @Setter private String currentJwt;

    /** Value of the {@code empresaId} JWT claim for the current scenario. */
    @Getter @Setter private String currentEmpresaId;

    /**
     * When {@code false}, the {@code X-Empresa-Id} header is omitted from requests. Supports the
     * "X-Empresa-Id is absent" feature scenario.
     */
    @Getter @Setter private boolean sendEmpresaId = true;

    /** Map from human-readable alias (e.g., "pac-001") to actual UUID in the database. */
    private final Map<String, UUID> entityAliases = new HashMap<>();

    /** Stores the ETag of the last successfully created/fetched entity (for If-Match). */
    @Getter @Setter private String lastEtag;

    /** Resets all per-scenario state. Called by {@link BddHooks#beforeScenario()}. */
    public void reset() {
        lastResponse = null;
        currentJwt = null;
        currentEmpresaId = null;
        sendEmpresaId = true;
        entityAliases.clear();
        lastEtag = null;
    }

    /** Returns the base URL of the running test server, e.g. {@code http://localhost:12345}. */
    public String baseUrl() {
        return "http://localhost:" + localServerPort;
    }

    /**
     * Stores an entity alias → UUID mapping.
     *
     * @param alias human-readable alias used in feature files (e.g., "pac-001")
     * @param id actual UUID assigned by the service
     */
    public void putAlias(String alias, UUID id) {
        entityAliases.put(alias, id);
    }

    /**
     * Retrieves the UUID for a human-readable alias.
     *
     * @param alias feature-file alias (e.g., "pac-001")
     * @return UUID, or {@code null} if not registered
     */
    /**
     * Todos os aliases registrados neste cenario.
     *
     * <p>Existe para que {@code resolvePath} nao precise de uma lista fixa de nomes. Com a lista
     * fixa, um alias novo simplesmente nao era substituido na URL, e o teste falhava com
     * {@code 400 Failed to convert 'id' with value: 'pac-cob'} — uma mensagem que nao aponta para
     * a causa.
     */
    public java.util.Map<String, UUID> registeredAliases() {
        return java.util.Map.copyOf(entityAliases);
    }

    public UUID getAlias(String alias) {
        return entityAliases.get(alias);
    }

    /**
     * Returns the UUID string for an alias, or the alias itself if no UUID is registered (useful
     * for aliases that are already UUIDs or for aliases that haven't been set up yet).
     */
    public String resolveId(String alias) {
        UUID id = entityAliases.get(alias);
        return id != null ? id.toString() : alias;
    }
}
