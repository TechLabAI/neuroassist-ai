-- ACS migration — empresa schema — faixa V0800..V0899 — owner: acs-service
-- =============================================================================
-- Migration: V0800__acs_empresa_schema.sql
-- Service:   acs-service
-- Schema:    empresa (per-tenant, aplicado por Flyway no schema t_<tenantId>)
-- Based on:  acs-service/contracts/data-model.md
--
-- Cria as tabelas tenant-scoped do ACS:
--   1. cuidador               — identidade do cuidador (login, credentialHash, papel)
--   2. caregiver_patient_link — link cuidador-paciente (coverage invariant D-204)
--
-- NUNCA hardcode schema — o Flyway aplica este arquivo dentro do schema correto.
-- Pré-requisito: V0001__init_schema.sql (cria neuroassist_global + pgcrypto).
--
-- Ordem de criação respeita FKs:
--   1. cuidador (aggregate root)
--   2. caregiver_patient_link (FK → cuidador)
--
-- INVARIANTE D-204 (SAFETY-CRITICAL): nunca remover o último link ativo de um paciente.
-- Enforçado em application layer (CaregiverPatientLinkService) — não por constraint DB.
-- =============================================================================

-- ────────────────────────────────────────────────────────────────────────────
-- Tabela: cuidador
-- Identidade do cuidador no schema do tenant.
-- credential_hash: BCrypt (strength 12) — NUNCA retornar via API.
-- Invariante: sem DELETE físico — apenas transições de status (ACTIVE ↔ INACTIVE).
-- Index case-insensitive em login para autenticação eficiente.
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cuidador (
    cuidador_id               uuid          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                 uuid          NOT NULL,
    nome                      varchar(200)  NOT NULL,
    login                     varchar(100)  NOT NULL,
    credential_hash           varchar(200)  NOT NULL,
    papel                     varchar(50)   NOT NULL,
    status                    varchar(20)   NOT NULL DEFAULT 'ACTIVE',
    criado_em                 timestamptz   NOT NULL DEFAULT now(),
    criado_por_usuario_id     uuid          NULL,
    atualizado_em             timestamptz   NULL,
    atualizado_por_usuario_id uuid          NULL,
    PRIMARY KEY (cuidador_id),
    CONSTRAINT ck_cuidador_papel  CHECK (papel  IN ('COORDENADOR_CLINICA','CUIDADORA','PACIENTE_APOIO')),
    CONSTRAINT ck_cuidador_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

-- Index único em lower(login) apenas para cuidadores ACTIVE (partial unique index)
CREATE UNIQUE INDEX IF NOT EXISTS ux_cuidador_login_active
    ON cuidador (lower(login))
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS ix_cuidador_tenant_id ON cuidador (tenant_id);
CREATE INDEX IF NOT EXISTS ix_cuidador_status     ON cuidador (status);
CREATE INDEX IF NOT EXISTS ix_cuidador_papel      ON cuidador (papel);

COMMENT ON TABLE  cuidador                  IS 'ACS: caregiver identity — empresa-scoped (t_<tenantId>). Sem DELETE físico. credential_hash NUNCA via API.';
COMMENT ON COLUMN cuidador.credential_hash  IS 'BCrypt hash (strength 12) — NUNCA serializar em API responses. @JsonIgnore enforçado no JPA entity.';
COMMENT ON COLUMN cuidador.tenant_id        IS 'UUID lógico do tenant — sem FK cross-schema (PS.003).';

-- ────────────────────────────────────────────────────────────────────────────
-- Tabela: caregiver_patient_link
-- Associação cuidador-paciente, empresa-scoped.
-- paciente_id: referência lógica ao pac-service — sem FK cross-service (PS.003).
-- INVARIANTE D-204 (SAFETY-CRITICAL): enforçado em application layer — não por DB constraint.
-- Invariante: sem UPDATE/DELETE físico — apenas transições de status (ACTIVE→REVOKED/SUSPENDED).
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS caregiver_patient_link (
    caregiver_patient_link_id uuid          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                 uuid          NOT NULL,
    cuidador_id               uuid          NOT NULL,
    paciente_id               uuid          NOT NULL,
    status                    varchar(20)   NOT NULL DEFAULT 'ACTIVE',
    revogado_em               timestamptz   NULL,
    criado_em                 timestamptz   NOT NULL DEFAULT now(),
    criado_por_usuario_id     uuid          NULL,
    atualizado_em             timestamptz   NULL,
    atualizado_por_usuario_id uuid          NULL,
    PRIMARY KEY (caregiver_patient_link_id),
    CONSTRAINT fk_link_cuidador FOREIGN KEY (cuidador_id) REFERENCES cuidador (cuidador_id) ON DELETE RESTRICT,
    CONSTRAINT ck_link_status   CHECK (status IN ('ACTIVE','REVOKED','SUSPENDED'))
);

-- Partial unique index: apenas um link ACTIVE por (cuidador_id, paciente_id)
CREATE UNIQUE INDEX IF NOT EXISTS ux_link_active_cuidador_paciente
    ON caregiver_patient_link (cuidador_id, paciente_id)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS ix_link_tenant_id   ON caregiver_patient_link (tenant_id);
CREATE INDEX IF NOT EXISTS ix_link_paciente_id ON caregiver_patient_link (paciente_id);
CREATE INDEX IF NOT EXISTS ix_link_cuidador_id ON caregiver_patient_link (cuidador_id);
CREATE INDEX IF NOT EXISTS ix_link_status      ON caregiver_patient_link (status);

COMMENT ON TABLE  caregiver_patient_link            IS 'ACS: caregiver-patient assignment — empresa-scoped (D-204 coverage invariant SAFETY-CRITICAL).';
COMMENT ON COLUMN caregiver_patient_link.paciente_id IS 'UUID lógico do paciente em pac-service — sem FK cross-service (PS.003).';
COMMENT ON COLUMN caregiver_patient_link.revogado_em IS 'Preenchido ao revogar o link — status muda para REVOKED. Imutável após set.';
