-- V0206__pac_idempotency_key.sql
-- PAC-023 (P8) — Idempotency-Key dedup table (SPEC-05 §3.3)
--
-- Stores deduplication entries for POST /pacientes and POST .../comandos.
-- Stored in per-empresa (tenant) schema: schema routing via search_path provides
-- tenant isolation — making the composite key = {resource_type}:{uuid4-key} unique
-- per tenant without a shared global table. The body_hash allows collision detection
-- (same key + different body → 409 idempotency-mismatch).
--
-- TTL: rows older than 24 hours are eligible for lazy purge or a scheduled cleanup job.

CREATE TABLE IF NOT EXISTS idempotency_key (
    key         TEXT        NOT NULL,
    resource_id UUID        NOT NULL,
    body_hash   TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_idempotency_key PRIMARY KEY (key)
);

-- Index for TTL cleanup queries (DELETE WHERE created_at < now() - INTERVAL '24h')
CREATE INDEX IF NOT EXISTS idx_idempotency_key_created_at
    ON idempotency_key (created_at);

COMMENT ON TABLE idempotency_key IS
    'Idempotency deduplication window (24h) for PAC POST endpoints (SPEC-05 §3). '
    'PK = {resource_type}:{uuid4-idempotency-key}; tenant isolation via per-schema routing.';
COMMENT ON COLUMN idempotency_key.key IS
    'Composite PK: {resource_type}:{caller-supplied UUID4 Idempotency-Key header value}.';
COMMENT ON COLUMN idempotency_key.resource_id IS
    'UUID of the created resource (Paciente.id or Comando.id).';
COMMENT ON COLUMN idempotency_key.body_hash IS
    'SHA-256 of canonical request body fields; mismatch → 409 idempotency-mismatch.';
COMMENT ON COLUMN idempotency_key.created_at IS
    'Creation timestamp; rows older than 24h are eligible for purge.';
