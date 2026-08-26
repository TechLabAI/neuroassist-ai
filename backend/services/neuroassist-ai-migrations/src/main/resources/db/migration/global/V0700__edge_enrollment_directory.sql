-- EDGE migration — global schema — faixa V0700..V0799 — owner: edge-service
-- =============================================================================
-- Migration: V0700__edge_enrollment_directory.sql
-- Service:   edge-service
-- Schema:    neuroassist_global
--
-- Índice global token_hash → tenant, para o enroll de dispositivo.
--
-- MOTIVO (E2E Sessão 3 / C-21): POST /api/v1/edge/devices/enroll é permitAll por
-- desenho — o enrollment token É a credencial (ADR-005), não há JWT. Sem JWT não há
-- claim de tenant, o TenantContextFilter não vincula nada e o search_path cai em
-- neuroassist_global. A consulta então falha:
--
--   ERROR: relation "dispositivo_enrollment" does not exist
--
-- A tabela existe, mas no schema do tenant. É um ovo-e-galinha: para achar o
-- enrollment é preciso saber o tenant, e o tenant só se descobre lendo o enrollment.
--
-- Esta tabela quebra o ciclo: vive no schema global (sempre alcançável, mesmo sem
-- tenant vinculado) e guarda apenas o ponteiro. O registro autoritativo do
-- enrollment continua em <tenant>.dispositivo_enrollment — aqui não há duplicação de
-- estado de negócio, só o índice necessário para rotear.
--
-- PRIVACIDADE: token_hash é SHA-256 do token (nunca o texto claro) e não há PII.
-- O que se expõe globalmente é a associação token→tenant, que é metadado
-- operacional.
-- =============================================================================

CREATE TABLE IF NOT EXISTS neuroassist_global.enrollment_directory (
    token_hash           varchar(200) NOT NULL,
    tenant_id            uuid         NOT NULL,
    dispositivo_edge_id  uuid         NOT NULL,
    expires_at           timestamptz  NOT NULL,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (token_hash)
);

-- Poda de tokens expirados (job operacional; o enroll também valida expiração).
CREATE INDEX IF NOT EXISTS ix_enrollment_directory_expires
    ON neuroassist_global.enrollment_directory (expires_at);

COMMENT ON TABLE neuroassist_global.enrollment_directory IS
    'Índice global token_hash → tenant para o enroll de dispositivo, que é anônimo por '
    'desenho (ADR-005). Ponteiro apenas; o registro autoritativo é <tenant>.dispositivo_enrollment.';

COMMENT ON COLUMN neuroassist_global.enrollment_directory.token_hash IS
    'SHA-256 do enrollment token — mesmo hash gravado em <tenant>.dispositivo_enrollment.';

COMMENT ON COLUMN neuroassist_global.enrollment_directory.expires_at IS
    'Cópia do vencimento, para permitir poda sem varrer os schemas de tenant.';
