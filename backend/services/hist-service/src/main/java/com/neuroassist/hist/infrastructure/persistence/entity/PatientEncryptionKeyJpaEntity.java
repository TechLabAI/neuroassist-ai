package com.neuroassist.hist.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * JPA entity — per-patient AES-256 key for LGPD crypto-shredding (P10, HIST-024).
 *
 * <p>Erasure: {@code keyCiphertext} and {@code keyIv} are set to {@code null} and {@code erasedAt}
 * is stamped. The row is retained as an audit trail of LGPD compliance.
 */
@Entity
@Table(name = "patient_encryption_key")
@IdClass(PatientEncryptionKeyId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientEncryptionKeyJpaEntity {

    @Id
    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "key_ciphertext")
    private byte[] keyCiphertext;

    @Column(name = "key_iv")
    private byte[] keyIv;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "erased_at")
    private Instant erasedAt;
}
