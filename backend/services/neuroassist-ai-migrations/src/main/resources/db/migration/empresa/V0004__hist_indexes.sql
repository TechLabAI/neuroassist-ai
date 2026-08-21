-- V0004__hist_indexes.sql
-- HIST module — composite indexes to support read queries from HistoryQueryService and
-- ChainVerificationService (HIST-008). All indexes are CONCURRENT-safe on creation.

-- Primary read path: history query by subject (stream_key) within a tenant, ordered by sequence
CREATE INDEX IF NOT EXISTS idx_audit_record_stream_tenant_seq
    ON audit_record (stream_key, tenant_id, sequence_no ASC);

-- Secondary read path: history query with date range filter
CREATE INDEX IF NOT EXISTS idx_audit_record_tenant_occurred
    ON audit_record (tenant_id, occurred_at DESC);

-- Fact type filter (used by HistoryQueryService.findFiltered)
CREATE INDEX IF NOT EXISTS idx_audit_record_tenant_facttype
    ON audit_record (tenant_id, fact_type);

-- Subject lookup (subjectType + subjectId filter)
CREATE INDEX IF NOT EXISTS idx_audit_record_subject
    ON audit_record (tenant_id, subject_type, subject_id);

-- Actor lookup (caregiver scope)
CREATE INDEX IF NOT EXISTS idx_audit_record_actor
    ON audit_record (tenant_id, caregiver_id);

-- Idempotency lookup by factId is already covered by the UNIQUE constraint (uq_idempotency_fact_tenant).

-- Stream head lookup is covered by the UNIQUE constraint (uq_stream_head_key_tenant).
