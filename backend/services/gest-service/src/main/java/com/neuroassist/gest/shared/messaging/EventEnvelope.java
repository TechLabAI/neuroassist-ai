package com.neuroassist.gest.shared.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Canonical JMS event envelope (events.arch.json §envelope.payload_fields, D-065). Producers wrap
 * their {@code data} payload in one of these; the Jackson MessageConverter (ArtemisConfig)
 * serialises to JSON.
 *
 * <p>{@code data} is intentionally {@code Map<String, Object>} for Phase 2 Fase 1 — a typed
 * payload-per-event upgrade is future work.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope(
        UUID eventId, // required — Idempotency key — consumers MUST be idempotent on this id.
        String eventType, // required — Mirrors the destination name (e.g.
        // 'bosq.cadastro.cliente.criado').
        String eventVersion, // required — Semver-like, normally 'v1'.
        OffsetDateTime
                producedAt, // required — Same as the header; duplicated in body for archival.
        Map<String, Object>
                producer, // required — Identifies the emitting service instance and its version.
        String tenantId, // optional — Same as header. Null for global events.
        UUID correlationId, // required — Same as header.
        UUID causationId, // optional — eventId that triggered this event (when applicable).
        Map<String, Object>
                actor, // optional — Who caused the event. Optional for system-triggered events.
        Map<String, Object> data // required — Event-specific payload — see per-event definition.
        ) {}
