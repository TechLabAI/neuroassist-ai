-- V0002__hist_idempotency.sql
-- HIST module — idempotency registry for audit fact ingestion (ADR-005).
-- Prevents duplicate appends for the same producer-minted factId within a tenant.

CREATE TABLE IF NOT EXISTS audit_idempotency (
    idempotency_id      UUID        NOT NULL DEFAULT gen_random_uuid(),
    fact_id             UUID        NOT NULL,
    tenant_id           VARCHAR(64) NOT NULL,
    audit_record_id     UUID        NOT NULL,
    recorded_at         TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_audit_idempotency PRIMARY KEY (idempotency_id),
    CONSTRAINT uq_idempotency_fact_tenant UNIQUE (fact_id, tenant_id),
    CONSTRAINT fk_idempotency_audit_record
        FOREIGN KEY (audit_record_id) REFERENCES audit_record(audit_record_id)
);

COMMENT ON TABLE audit_idempotency IS 'Idempotency registry — keyed by (fact_id, tenant_id). Written once per successful append; never updated or deleted (ADR-005).';
