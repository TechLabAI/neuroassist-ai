package com.neuroassist.hist.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.hist.application.port.out.AuditLedgerPort;
import com.neuroassist.hist.domain.model.ActorRef;
import com.neuroassist.hist.domain.model.AuditRecord;
import com.neuroassist.hist.domain.model.SubjectRef;
import com.neuroassist.hist.domain.model.SubjectType;
import com.neuroassist.hist.infrastructure.crypto.AesGcmPayloadCrypto;
import com.neuroassist.hist.infrastructure.persistence.entity.AuditRecordJpaEntity;
import com.neuroassist.hist.infrastructure.persistence.entity.PatientEncryptionKeyId;
import com.neuroassist.hist.infrastructure.persistence.entity.PatientEncryptionKeyJpaEntity;
import com.neuroassist.hist.infrastructure.persistence.repository.PatientEncryptionKeyJpaRepository;
import com.neuroassist.hist.infrastructure.security.TenantContextHolder;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter — write-once insert and read of {@link AuditRecord} (ADR-004, P10).
 *
 * <p>Payload is AES-256-GCM encrypted per patient before persistence (P10 / HIST-024). On read, the
 * patient key is fetched and decrypted; if the key was erased (LGPD), the returned record has
 * {@code payload = null} and {@code erased = true}.
 *
 * <p>Uses {@link EntityManager#persist} exclusively — never {@code save()} or {@code merge()} — to
 * enforce the write-once invariant at the persistence boundary (ADR-004).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLedgerAdapter implements AuditLedgerPort {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EntityManager entityManager;
    private final PatientEncryptionKeyJpaRepository patientKeyRepository;
    private final AesGcmPayloadCrypto crypto;

    @Override
    public AuditRecord insert(AuditRecord record, String tenantId) {
        TenantContextHolder.require();
        AuditRecordJpaEntity entity = toEntity(record);
        entityManager.persist(entity);
        log.debug(
                "AuditRecord persisted: id={} streamKey={} seq={} tenant={}",
                record.id(),
                record.streamKey(),
                record.sequenceNo(),
                tenantId);
        return record;
    }

    // ---- mapper: domain → JPA entity (encrypt payload) ----

    private AuditRecordJpaEntity toEntity(AuditRecord r) {
        SubjectRef subject = r.subject();
        ActorRef actor = r.actor();

        String encryptedPayload = encryptPayload(r);

        return AuditRecordJpaEntity.builder()
                .id(r.id())
                .tenantId(r.tenantId())
                .factId(r.factId())
                .factType(r.factType())
                .subjectType(subject != null ? subject.type().name() : null)
                .subjectId(subject != null ? subject.id() : null)
                .caregiverId(actor != null ? actor.caregiverId() : null)
                .actorDisplayName(actor != null ? actor.displayNameSnapshot() : null)
                .payload(encryptedPayload)
                // Truncate to microseconds before persisting: PostgreSQL TIMESTAMPTZ rounds
                // sub-microsecond nanoseconds, which would differ from the truncation in
                // Sha256HashChainAdapter.canonicalize(), causing spurious hash mismatches
                // (ADR-006).
                .occurredAt(truncateMicros(r.occurredAt()))
                .recordedAt(truncateMicros(r.recordedAt()))
                .streamKey(r.streamKey())
                .sequenceNo(r.sequenceNo())
                .prevHash(r.prevHash())
                .recordHash(r.recordHash())
                .correctsRecordId(r.correctsRecordId())
                .scopePacienteId(r.scopePacienteId())
                .build();
    }

    /**
     * Encrypts the payload map with the per-patient key. Returns null when payload is null/empty.
     */
    private String encryptPayload(AuditRecord r) {
        if (r.payload() == null || r.payload().isEmpty()) {
            return null;
        }
        if (r.subject() == null) {
            return null;
        }
        try {
            String json = MAPPER.writeValueAsString(r.payload());
            byte[] rawKey = findOrCreateRawPatientKey(cryptoKeySubject(r), r.tenantId());
            return crypto.encryptPayload(json, rawKey);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize payload for encryption", e);
        }
    }

    /**
     * The identity whose key encrypts this record's payload.
     *
     * <p>Crypto-shredding only means something if erasing a <i>patient</i> reaches every payload
     * about that patient. Keying by {@code subject.id} did not: a {@code comando.publicado} fact
     * has the command as its subject, so it was encrypted under a key nobody would ever erase — and
     * its payload carries {@code pacienteId}. Erasing the patient left it perfectly readable.
     *
     * <p>So the key follows {@code scopePacienteId} when the record has one, and falls back to the
     * subject otherwise (facts with no patient scope, such as {@code acesso.*}).
     */
    private UUID cryptoKeySubject(AuditRecord r) {
        return r.scopePacienteId() != null ? r.scopePacienteId() : r.subject().id();
    }

    /**
     * Finds the per-patient AES key (creating it on first use). Throws if the key is already erased
     * — new records must not be inserted for an erased patient.
     */
    private byte[] findOrCreateRawPatientKey(UUID subjectId, String tenantId) {
        PatientEncryptionKeyId id = new PatientEncryptionKeyId(subjectId, tenantId);
        PatientEncryptionKeyJpaEntity entity =
                patientKeyRepository
                        .findById(id)
                        .orElseGet(
                                () -> {
                                    byte[] rawKey = crypto.generatePatientKey();
                                    byte[][] wrapped = crypto.wrapPatientKey(rawKey);
                                    return patientKeyRepository.save(
                                            PatientEncryptionKeyJpaEntity.builder()
                                                    .subjectId(subjectId)
                                                    .tenantId(tenantId)
                                                    .keyCiphertext(wrapped[0])
                                                    .keyIv(wrapped[1])
                                                    .createdAt(Instant.now())
                                                    .build());
                                });
        if (entity.getErasedAt() != null || entity.getKeyCiphertext() == null) {
            throw new IllegalStateException(
                    "Patient key erased — cannot insert new records for erased patient: "
                            + subjectId);
        }
        return crypto.unwrapPatientKey(entity.getKeyCiphertext(), entity.getKeyIv());
    }

    // ---- mapper: JPA entity → domain (decrypt payload) ----

    /** Converts a JPA entity back to the domain record (used by read paths in other adapters). */
    public AuditRecord toDomain(AuditRecordJpaEntity e) {
        SubjectRef subject =
                e.getSubjectType() != null && e.getSubjectId() != null
                        ? new SubjectRef(SubjectType.valueOf(e.getSubjectType()), e.getSubjectId())
                        : null;
        ActorRef actor =
                e.getCaregiverId() != null
                        ? new ActorRef(e.getCaregiverId(), e.getActorDisplayName())
                        : null;

        Map<String, Object> payload = null;
        boolean erased = false;

        if (e.getPayload() != null && e.getSubjectId() != null) {
            // Records written before the key followed scopePacienteId are still encrypted under
            // their subject's key. Look for the scope key first, then fall back — otherwise this
            // change would make every existing command fact read as "erased", which is data loss
            // dressed up as compliance.
            PatientEncryptionKeyJpaEntity keyEntity = null;
            if (e.getScopePacienteId() != null) {
                keyEntity =
                        patientKeyRepository
                                .findById(
                                        new PatientEncryptionKeyId(
                                                e.getScopePacienteId(), e.getTenantId()))
                                .orElse(null);
            }
            boolean scopeKeyErased =
                    keyEntity != null
                            && (keyEntity.getErasedAt() != null
                                    || keyEntity.getKeyCiphertext() == null);
            if (keyEntity == null && !scopeKeyErased) {
                keyEntity =
                        patientKeyRepository
                                .findById(
                                        new PatientEncryptionKeyId(
                                                e.getSubjectId(), e.getTenantId()))
                                .orElse(null);
            }

            if (keyEntity == null
                    || keyEntity.getErasedAt() != null
                    || keyEntity.getKeyCiphertext() == null) {
                erased = true;
            } else {
                try {
                    byte[] rawKey =
                            crypto.unwrapPatientKey(
                                    keyEntity.getKeyCiphertext(), keyEntity.getKeyIv());
                    String json = crypto.decryptPayload(e.getPayload(), rawKey);
                    payload = MAPPER.readValue(json, MAP_TYPE);
                } catch (Exception ex) {
                    log.warn(
                            "Failed to decrypt payload for record={} — treating as erased: {}",
                            e.getId(),
                            ex.getMessage());
                    erased = true;
                }
            }
        }

        return new AuditRecord(
                e.getId(),
                e.getTenantId(),
                e.getFactId(),
                e.getFactType(),
                subject,
                actor,
                payload,
                e.getOccurredAt(),
                e.getRecordedAt(),
                e.getStreamKey(),
                e.getSequenceNo(),
                e.getPrevHash(),
                e.getRecordHash(),
                e.getCorrectsRecordId(),
                erased,
                e.getScopePacienteId());
    }

    private static Instant truncateMicros(Instant instant) {
        return instant != null ? instant.truncatedTo(ChronoUnit.MICROS) : null;
    }
}
