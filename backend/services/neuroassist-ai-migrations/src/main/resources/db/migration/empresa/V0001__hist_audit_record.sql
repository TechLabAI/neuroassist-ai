-- V0001__hist_audit_record.sql
-- HIST module — write-once audit record ledger (ADR-004, ADR-006)
-- Applied per empresa schema (tenant isolation).

-- Extension required by uuid_generate_v7 / uuid_generate_v4 fallbacks.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS audit_record (
    audit_record_id     UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(64) NOT NULL,
    fact_id             UUID        NOT NULL,
    fact_type           VARCHAR(128)NOT NULL,

    -- Subject ref (denormalised; never joined)
    subject_type        VARCHAR(64) NOT NULL,
    subject_id          UUID        NOT NULL,

    -- Actor ref snapshot at ingestion time (denormalised; never re-fetched from ACS)
    caregiver_id        UUID        NOT NULL,
    actor_display_name  VARCHAR(256),

    -- Structured payload (validated by LGPD guard at ingest; no biometric keys)
    payload             TEXT        NOT NULL DEFAULT '{}',

    -- Timestamps
    occurred_at         TIMESTAMPTZ NOT NULL,
    recorded_at         TIMESTAMPTZ NOT NULL,

    -- Hash chain fields (ADR-006)
    stream_key          VARCHAR(256)NOT NULL,
    sequence_no         BIGINT      NOT NULL,
    prev_hash           VARCHAR(64) NOT NULL,
    record_hash         VARCHAR(64) NOT NULL,

    -- Optional correction linkage
    corrects_record_id  UUID,

    CONSTRAINT pk_audit_record PRIMARY KEY (audit_record_id),
    CONSTRAINT uq_audit_record_fact_tenant UNIQUE (fact_id, tenant_id)
);

COMMENT ON TABLE audit_record IS 'Immutable audit event ledger — write-once (ADR-004). Hash-chained per (tenant, stream_key) stream (ADR-006).';
COMMENT ON COLUMN audit_record.record_hash IS 'SHA-256 of prevHash || canonical(record) — ADR-006 chain integrity anchor.';
