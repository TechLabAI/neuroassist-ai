-- V0003__hist_stream_head.sql
-- HIST module — mutable stream-tip cache for hash-chain append serialisation (ADR-006).
-- One row per (stream_key, tenant_id); updated on each successful append.

CREATE TABLE IF NOT EXISTS stream_head (
    stream_head_id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    stream_key              VARCHAR(256)NOT NULL,
    tenant_id               VARCHAR(64) NOT NULL,
    current_sequence_no     BIGINT      NOT NULL,
    current_hash            VARCHAR(64) NOT NULL,
    last_recorded_at        TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_stream_head PRIMARY KEY (stream_head_id),
    CONSTRAINT uq_stream_head_key_tenant UNIQUE (stream_key, tenant_id)
);

COMMENT ON TABLE stream_head IS 'Mutable cache of the latest (sequence_no, hash) per (stream_key, tenant_id) stream. Locked with PESSIMISTIC_WRITE during append to serialise concurrent writes (ADR-006).';
