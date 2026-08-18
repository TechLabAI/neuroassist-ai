package com.neuroassist.edge.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * EDGE aggregate root — device identity, exclusive pairing, and liveness inputs. Zero Spring/JPA
 * imports (RULE-01, RULE-02). {@code pairingState} FSM: registered → paired ↔ unpaired → retired
 * (terminal). {@code ultimaSincronizacao} is server-stamped at heartbeat accept (never the client
 * clock). {@code status} (online/offline) is derived at read time — never stored (ADR-006). {@code
 * rowVersion} maps to the JPA {@code @Version} column added in P3.
 */
public record DispositivoEdge(
        UUID id,
        String tenantId,
        String label,
        UUID pacienteId,
        PairingState pairingState,
        String versao,
        Instant ultimaSincronizacao,
        Long runningProfileVersion,
        long rowVersion,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy) {

    /** Factory for a newly registered device — sets mandatory defaults. */
    public static DispositivoEdge newDevice(
            UUID id, String tenantId, String label, UUID pacienteHint, String actor) {
        Instant now = Instant.now();
        // pacienteHint is advisory only — does NOT set pacienteId (not paired yet)
        return new DispositivoEdge(
                id,
                tenantId,
                label,
                null,
                PairingState.registered,
                null,
                null,
                null,
                0L,
                now,
                now,
                actor,
                actor);
    }

    /** Transition state: move to paired + set patient. */
    public DispositivoEdge withPaired(UUID pacienteId, String actor) {
        return new DispositivoEdge(
                id,
                tenantId,
                label,
                pacienteId,
                PairingState.paired,
                versao,
                ultimaSincronizacao,
                runningProfileVersion,
                rowVersion,
                createdAt,
                Instant.now(),
                createdBy,
                actor);
    }

    /** Transition state: move to unpaired + clear patient. */
    public DispositivoEdge withUnpaired(String actor) {
        return new DispositivoEdge(
                id,
                tenantId,
                label,
                null,
                PairingState.unpaired,
                versao,
                ultimaSincronizacao,
                runningProfileVersion,
                rowVersion,
                createdAt,
                Instant.now(),
                createdBy,
                actor);
    }

    /** Update heartbeat fields — server clock only. */
    public DispositivoEdge withHeartbeat(
            String agentVersion, Long runningProfileVer, Instant serverNow) {
        return new DispositivoEdge(
                id,
                tenantId,
                label,
                pacienteId,
                pairingState,
                agentVersion,
                serverNow,
                runningProfileVer,
                rowVersion,
                createdAt,
                serverNow,
                createdBy,
                "system");
    }

    /**
     * Record initial agentVersion at enrollment — does NOT stamp ultimaSincronizacao. api-contract
     * §2.2: "record agentVersion as initial versao (not yet a heartbeat)".
     */
    public DispositivoEdge withInitialAgentVersion(String agentVersion, String actor) {
        Instant now = Instant.now();
        return new DispositivoEdge(
                id,
                tenantId,
                label,
                pacienteId,
                pairingState,
                agentVersion,
                ultimaSincronizacao,
                runningProfileVersion,
                rowVersion,
                createdAt,
                now,
                createdBy,
                actor);
    }

    /** Move to retired state. */
    public DispositivoEdge withRetired(String actor) {
        return new DispositivoEdge(
                id,
                tenantId,
                label,
                null,
                PairingState.retired,
                versao,
                ultimaSincronizacao,
                runningProfileVersion,
                rowVersion,
                createdAt,
                Instant.now(),
                createdBy,
                actor);
    }
}
