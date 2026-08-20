package com.neuroassist.hist.infrastructure.hash;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.neuroassist.hist.application.port.out.HashChainPort;
import com.neuroassist.hist.domain.model.AuditRecord;
import com.neuroassist.hist.domain.model.HashChain;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter — SHA-256 hash-chain computation (HIST-007, ADR-006).
 *
 * <p>Canonical form: sorted-key JSON of all {@link AuditRecord} fields except {@code recordHash}
 * (which is the value being computed), with nested map keys also sorted via Jackson's {@code
 * ORDER_MAP_ENTRIES_BY_KEYS} feature.
 *
 * <p>Pre-image: {@code UTF-8(prevHash_hex) || UTF-8(canonical_json)}.
 *
 * <p>No JCS / RFC-8785 library required: Jackson {@link ObjectMapper} with sorted keys produces
 * deterministic output for the v1 vocabulary payload structure.
 */
@Slf4j
@Component
public class Sha256HashChainAdapter implements HashChainPort {

    /**
     * Dedicated ObjectMapper configured for canonical, deterministic JSON output. Isolated from the
     * application-wide ObjectMapper to avoid serialization side effects.
     */
    private static final ObjectMapper CANONICAL_MAPPER =
            new ObjectMapper()
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    @Override
    public String compute(String prevHash, AuditRecord record) {
        try {
            String canonical = canonicalize(record);
            String preimage = prevHash + canonical;
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(preimage.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JVM spec; this should never happen
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    @Override
    public String genesis() {
        return HashChain.GENESIS_PREV_HASH;
    }

    /**
     * Builds a deterministic sorted-key JSON string from the audit record, excluding {@code
     * recordHash} (the field being computed).
     *
     * <p>Package-private for unit-test access.
     */
    String canonicalize(AuditRecord r) {
        // TreeMap guarantees lexicographic key order at the top level.
        // CANONICAL_MAPPER's ORDER_MAP_ENTRIES_BY_KEYS handles nested map levels
        // (e.g. payload keys), making the full output deterministic.
        TreeMap<String, Object> map = new TreeMap<>();
        map.put(
                "correctsRecordId",
                r.correctsRecordId() != null ? r.correctsRecordId().toString() : null);
        map.put("factId", r.factId() != null ? r.factId().toString() : null);
        map.put("factType", r.factType());
        map.put("id", r.id() != null ? r.id().toString() : null);
        // Truncate to microseconds before stringifying — PostgreSQL stores TIMESTAMP with
        // microsecond precision; Instant.now() on some JVMs (Windows) may return sub-microsecond
        // nanoseconds that would produce a different toString() representation after a DB
        // round-trip,
        // causing spurious hash mismatches during chain verification (ADR-006).
        map.put(
                "occurredAt",
                r.occurredAt() != null
                        ? r.occurredAt().truncatedTo(ChronoUnit.MICROS).toString()
                        : null);
        // payload included for integrity; never logged in cleartext (LGPD).
        // Normalize null and empty-map to null so that a DB round-trip (JsonMapConverter stores
        // null as "{}") produces the same canonical form as the original null at ingestion time.
        map.put("payload", (r.payload() == null || r.payload().isEmpty()) ? null : r.payload());
        map.put("prevHash", r.prevHash());
        map.put(
                "recordedAt",
                r.recordedAt() != null
                        ? r.recordedAt().truncatedTo(ChronoUnit.MICROS).toString()
                        : null);
        map.put("sequenceNo", r.sequenceNo());
        map.put("streamKey", r.streamKey());
        map.put("tenantId", r.tenantId());

        // subject — nested sorted map
        if (r.subject() != null) {
            TreeMap<String, Object> subject = new TreeMap<>();
            subject.put("id", r.subject().id() != null ? r.subject().id().toString() : null);
            subject.put("type", r.subject().type() != null ? r.subject().type().name() : null);
            map.put("subject", subject);
        } else {
            map.put("subject", null);
        }

        // actor — nested sorted map
        if (r.actor() != null) {
            TreeMap<String, Object> actor = new TreeMap<>();
            actor.put(
                    "caregiverId",
                    r.actor().caregiverId() != null ? r.actor().caregiverId().toString() : null);
            actor.put("displayNameSnapshot", r.actor().displayNameSnapshot());
            map.put("actor", actor);
        } else {
            map.put("actor", null);
        }

        try {
            return CANONICAL_MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to canonicalize AuditRecord for hash computation", e);
        }
    }
}
