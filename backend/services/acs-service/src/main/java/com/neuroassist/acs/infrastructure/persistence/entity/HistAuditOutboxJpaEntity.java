package com.neuroassist.acs.infrastructure.persistence.entity;

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
 * Entidade JPA de {@code neuroassist_global.acs_hist_outbox} (E2E-031).
 *
 * <p><b>Schema qualificado explicitamente</b>, como {@code tenant} e {@code signing_key}: esta
 * tabela é global de propósito. O acs-service grava fatos de várias empresas e o dreno roda sem
 * tenant vinculado — depender do {@code search_path} aqui traria de volta exatamente a armadilha
 * que os outbox do PAC e do EVNT precisam contornar.
 *
 * <p>Mapeamento alinhado a {@code V0103__acs_hist_outbox.sql} — {@code status} é {@code varchar} lá
 * precisamente porque é {@code String} aqui (defeito C-12).
 */
@Entity
@Table(schema = "neuroassist_global", name = "acs_hist_outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistAuditOutboxJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Chave de idempotência do fato — cunhada uma vez, reenviada em toda retentativa. */
    @Column(name = "fact_id", nullable = false, updatable = false)
    private UUID factId;

    @Column(name = "fact_type", nullable = false)
    private String factType;

    /** Empresa a que o fato pertence; escopa o token de serviço na reentrega. */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** {@code HistAuditClient.RecordAuditFactRequest} serializado. */
    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    /** {@code pending} | {@code sent} | {@code failed}. */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "pending";

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    /** Por que a última tentativa falhou — mantido para que uma entrada `failed` seja triável. */
    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;
}
