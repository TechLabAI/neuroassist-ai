package com.neuroassist.evnt.bdd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Per-scenario shared state (cucumber-glue scope). All step definition classes within the same
 * scenario inject this singleton via {@code @Autowired} and share data through it.
 *
 * <p>P8 (EVNT-022/023/024) — eliminates the need for static fields or ThreadLocal hacks in step
 * classes.
 */
@Component
@Scope("cucumber-glue")
@Getter
@Setter
public class ScenarioContext {

    // HTTP response from the last API call
    private ResponseEntity<String> lastResponse;

    // Scenario identity
    private String tenantId = "empresa_demo";
    private String pacienteId = BddIds.PACIENTE;
    private String commandRef = "cmd-emergency";
    private String urgencia = "emergencia";
    private String reportId;
    private boolean rawFramePayload = false;
    private String deviceTokenTenant = "empresa_demo";

    /**
     * Map of scenario-level alias → UUID for eventos pre-created in a Given step (e.g. "evt-1" →
     * actual UUID in DB). Used to resolve assertions like "Evento 'evt-1' has feedback 'real'".
     */
    private final Map<String, UUID> eventoAliases = new HashMap<>();

    public void registerEvento(String alias, UUID id) {
        eventoAliases.put(alias, id);
    }

    public UUID getEventoId(String alias) {
        return eventoAliases.get(alias);
    }
}
