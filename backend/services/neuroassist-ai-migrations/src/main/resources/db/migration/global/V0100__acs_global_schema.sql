-- ACS migration — global schema — faixa V0100..V0199 — owner: acs-service
-- =============================================================================
-- Migration: V0100__acs_global_schema.sql
-- Service:   acs-service
-- Schema:    neuroassist_global (tabelas globais do ACS)
-- Based on:  acs-service/contracts/data-model.md
--
-- Cria as tabelas globais que existem no schema neuroassist_global:
--   1. tenant       — registro de tenant (clinica/paciente_home/telehealth)
--   2. signing_key  — chaves RSA para assinatura JWT RS256
--
-- Pré-requisito: V0001__init_schema.sql (cria neuroassist_global schema)
-- NUNCA hardcode schema diferente de neuroassist_global aqui.
-- private_key_enc NUNCA deve aparecer em API responses (@JsonIgnore enforçado em code).
-- =============================================================================

-- Extensão para geração de UUID (se não criada pelo V0001)
CREATE EXTENSION IF NOT EXISTS pgcrypto SCHEMA neuroassist_global;

-- ────────────────────────────────────────────────────────────────────────────
-- Tabela: neuroassist_global.tenant
-- Aggregate root — identidade de tenant, status de provisionamento.
-- Invariante: sem DELETE físico — apenas transições de status.
-- schema_nome é preenchido após provisionamento (MIG-service saga D-203).
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS neuroassist_global.tenant (
    tenant_id                 uuid          NOT NULL DEFAULT gen_random_uuid(),
    nome                      varchar(200)  NOT NULL,
    tipo                      varchar(50)   NOT NULL,
    status                    varchar(50)   NOT NULL DEFAULT 'PROVISIONING',
    schema_nome               varchar(80)   NULL,
    criado_em                 timestamptz   NOT NULL DEFAULT now(),
    criado_por_usuario_id     uuid          NULL,
    atualizado_em             timestamptz   NULL,
    atualizado_por_usuario_id uuid          NULL,
    PRIMARY KEY (tenant_id),
    CONSTRAINT ck_tenant_tipo   CHECK (tipo   IN ('CLINICA','PAC_HOME','TELEHEALTH')),
    CONSTRAINT ck_tenant_status CHECK (status IN ('PROVISIONING','ACTIVE','SUSPENDED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_tenant_schema_nome
    ON neuroassist_global.tenant (schema_nome)
    WHERE schema_nome IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_tenant_status ON neuroassist_global.tenant (status);

COMMENT ON TABLE  neuroassist_global.tenant              IS 'ACS: tenant registry — schema-per-tenant isolation (D-202). Sem DELETE físico.';
COMMENT ON COLUMN neuroassist_global.tenant.schema_nome  IS 'Preenchido após MIG-service saga concluída. Null enquanto PROVISIONING.';

-- ────────────────────────────────────────────────────────────────────────────
-- Tabela: neuroassist_global.signing_key
-- Chaves RSA RS256 para assinatura/validação de JWT.
-- private_key_enc: material de chave privada criptografado — NUNCA retornar via API.
-- Invariante: sem DELETE físico — apenas rotação de status (ACTIVE→RETIRED→ROTATED).
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS neuroassist_global.signing_key (
    signing_key_id            uuid          NOT NULL DEFAULT gen_random_uuid(),
    kid                       varchar(120)  NOT NULL,
    algorithm                 varchar(20)   NOT NULL DEFAULT 'RS256',
    public_jwk                text          NOT NULL,
    private_key_enc           bytea         NOT NULL,
    status                    varchar(20)   NOT NULL DEFAULT 'ACTIVE',
    not_before                timestamptz   NULL,
    retired_at                timestamptz   NULL,
    criado_em                 timestamptz   NOT NULL DEFAULT now(),
    criado_por_usuario_id     uuid          NULL,
    atualizado_em             timestamptz   NULL,
    atualizado_por_usuario_id uuid          NULL,
    PRIMARY KEY (signing_key_id),
    CONSTRAINT ux_signing_key_kid    UNIQUE (kid),
    CONSTRAINT ck_signing_key_algo   CHECK (algorithm IN ('RS256','RS384','RS512')),
    CONSTRAINT ck_signing_key_status CHECK (status     IN ('ACTIVE','RETIRED','ROTATED'))
);

CREATE INDEX IF NOT EXISTS ix_signing_key_status ON neuroassist_global.signing_key (status);

COMMENT ON TABLE  neuroassist_global.signing_key                     IS 'ACS: RS256 signing keys — rotation supported (D-205). private_key_enc NUNCA exposto via API.';
COMMENT ON COLUMN neuroassist_global.signing_key.private_key_enc     IS 'Chave privada criptografada — NUNCA serializar em API responses ou logs. @JsonIgnore enforçado no JPA entity.';
COMMENT ON COLUMN neuroassist_global.signing_key.kid                 IS 'Key ID incluído no JWT header para seleção de chave de validação.';
