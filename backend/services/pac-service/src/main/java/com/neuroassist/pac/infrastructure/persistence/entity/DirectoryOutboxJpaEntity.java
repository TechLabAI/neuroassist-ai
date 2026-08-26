package com.neuroassist.pac.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity for the {@code directory_outbox} table (E2E-015).
 *
 * <p>Written inside the caller's {@code @Transactional} boundary, together with the {@code
 * paciente_cuidador} row it propagates.
 *
 * <p>Schema: per-tenant. Column mapping aligned to {@code V0209__pac_directory_outbox.sql} — {@code
 * operation} and {@code status} are {@code varchar} there precisely because they are {@code String}
 * here (C-12).
 */
@Entity
@Table(name = "directory_outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectoryOutboxJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** {@code LINK} or {@code UNLINK}. */
    @Column(name = "operation", nullable = false)
    private String operation;

    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    @Column(name = "cuidador_id", nullable = false)
    private UUID cuidadorId;

    /** {@code pending} | {@code sent} | {@code failed}. */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "pending";

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    /** Why the last attempt failed — kept so a {@code failed} entry can be triaged. */
    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /** Populated when {@code status} transitions to {@code sent}. */
    @Column(name = "sent_at")
    private Instant sentAt;
}
