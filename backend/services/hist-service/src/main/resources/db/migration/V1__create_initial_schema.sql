-- =============================================================================
-- Migration: V1__create_initial_schema.sql
-- Service:   hist-service
-- Generated: 2026-08-16 by be-migration-generator.py
-- Source:    contracts/data-model.md (extraído de 5 blocos ```sql```)
--
-- ATENÇÃO: este arquivo é GERADO. Para alterar DDL:
--   1. Edite contracts/data-model.md (blocos ```sql```).
--   2. Re-rode: python scripts/be-migration-generator.py <service> --spec-dir <path> --force
-- =============================================================================

-- schema: global
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- schema: empresa
CREATE TABLE audit_record (
    id                    uuid         NOT NULL DEFAULT uuid_generate_v7(),
    fact_id               uuid         NOT NULL,
    fact_type             varchar(80)  NOT NULL,
    subject_type          varchar(20)  NOT NULL,
    subject_id            uuid         NOT NULL,
    actor_caregiver_id    uuid         NOT NULL,
    actor_label_snapshot  varchar(200) NOT NULL,
    payload               jsonb        NOT NULL,
    occurred_at           timestamptz  NOT NULL,
    recorded_at           timestamptz  NOT NULL DEFAULT now(),
    stream_key            varchar(60)  NOT NULL,
    sequence_no           bigint       NOT NULL,
    prev_hash             char(64)     NOT NULL,
    record_hash           char(64)     NOT NULL,
    corrects_record_id    uuid         NULL,
    CONSTRAINT pk_audit_record PRIMARY KEY (id),
    CONSTRAINT uq_audit_record_fact_id UNIQUE (fact_id),
    CONSTRAINT uq_audit_record_stream_seq UNIQUE (stream_key, sequence_no),
    CONSTRAINT uq_audit_record_hash UNIQUE (record_hash),
    CONSTRAINT ck_audit_record_subject_type
        CHECK (subject_type IN ('paciente','comando','cuidador','dispositivo')),
    CONSTRAINT ck_audit_record_sequence_no CHECK (sequence_no >= 0),
    CONSTRAINT fk_audit_record_corrects
        FOREIGN KEY (corrects_record_id) REFERENCES audit_record (id)
);

CREATE INDEX ix_audit_record_timeline
    ON audit_record (subject_type, subject_id, recorded_at DESC);
CREATE INDEX ix_audit_record_fact_type      ON audit_record (fact_type);
CREATE INDEX ix_audit_record_actor          ON audit_record (actor_caregiver_id);
CREATE INDEX ix_audit_record_recorded_at    ON audit_record (recorded_at DESC);
CREATE INDEX ix_audit_record_stream         ON audit_record (stream_key, sequence_no);

COMMENT ON TABLE audit_record IS
    'HIST immutable append-only audit ledger (ADR-004). Write-once; no UPDATE/DELETE. Hash-chained per (stream_key) (ADR-006). payload carries no biometric/image data (LGPD).';
COMMENT ON COLUMN audit_record.stream_key IS 'subject_type:subject_id — hash-chain scope within the tenant';
COMMENT ON COLUMN audit_record.record_hash IS 'sha256(prev_hash || canonical(record)) — tamper evidence';
COMMENT ON COLUMN audit_record.corrects_record_id IS 'Set only on corrective records; points to the prior record (ADR-004)';

-- schema: empresa
CREATE TABLE audit_idempotency (
    fact_id        uuid        NOT NULL,
    record_id      uuid        NOT NULL,
    first_seen_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_audit_idempotency PRIMARY KEY (fact_id),
    CONSTRAINT fk_audit_idempotency_record
        FOREIGN KEY (record_id) REFERENCES audit_record (id)
);

COMMENT ON TABLE audit_idempotency IS
    'fact_id -> record_id idempotency ledger (ADR-005). One fact => one record; duplicates return the original.';

-- schema: empresa
CREATE TABLE stream_head (
    stream_key         varchar(60) NOT NULL,
    last_sequence_no   bigint      NOT NULL,
    last_record_hash   char(64)    NOT NULL,
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_stream_head PRIMARY KEY (stream_key)
);

COMMENT ON TABLE stream_head IS
    'Derived tip cache per (stream_key): O(1) append tip lookup (ADR-006). Rebuildable from audit_record; not a source of truth.';

-- schema: empresa
-- Defense-in-depth immutability backstop (ADR-004): the service role may INSERT and SELECT the ledger,
-- but never UPDATE or DELETE it. Applied per tenant schema by MIG. Role name is resolved by MIG at apply
-- time (placeholder ${hist_service_role}).
REVOKE UPDATE, DELETE ON audit_record       FROM PUBLIC;
REVOKE UPDATE, DELETE ON audit_idempotency   FROM PUBLIC;
GRANT  INSERT, SELECT ON audit_record        TO "${hist_service_role}";
GRANT  INSERT, SELECT ON audit_idempotency    TO "${hist_service_role}";
GRANT  INSERT, SELECT, UPDATE ON stream_head  TO "${hist_service_role}";
