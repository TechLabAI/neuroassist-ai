-- V0005__hist_role_grants.sql
-- HIST module — minimal privilege grants for the hist-service database role.
-- SELECT + INSERT only on audit_record and audit_idempotency (ADR-004 enforcement at DB level).
-- SELECT + INSERT + UPDATE on stream_head (mutable tip cache — ADR-006).

-- Grant privileges for the hist-service role (configurable via Flyway placeholder)
DO $$
BEGIN
    -- audit_record: INSERT only (no UPDATE, no DELETE — ADR-004)
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${hist_service_role}') THEN
        GRANT SELECT, INSERT ON audit_record     TO "${hist_service_role}";
        GRANT SELECT, INSERT ON audit_idempotency TO "${hist_service_role}";
        GRANT SELECT, INSERT, UPDATE ON stream_head TO "${hist_service_role}";
    END IF;
END;
$$;
