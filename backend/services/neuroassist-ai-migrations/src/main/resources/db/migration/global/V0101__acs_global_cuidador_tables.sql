-- ACS migration — global schema — faixa V0100..V0199 — owner: acs-service
-- =============================================================================
-- Migration: V0101__acs_global_cuidador_tables.sql
-- Service:   acs-service
-- Schema:    neuroassist_global
--
-- Cria as tabelas de cuidador no schema GLOBAL, espelhando V0800 (empresa-scoped).
--
-- MOTIVO: durante o login (POST /oauth2/token) não há JWT nem tenant context.
-- O TenantIdentifierResolver cai no fallback "neuroassist_global", portanto
-- o Hibernate busca "cuidador" em neuroassist_global quando nenhum tenant está
-- no ThreadLocal. Esta migration garante que a tabela existe neste schema para
-- que o fluxo de autenticação funcione no ambiente docker-compose E2E.
--
-- Em produção, cada tenant tem seu próprio schema (t_<tenantId>) criado pela
-- MIG saga; esta tabela global serve como ponto de autenticação cross-tenant.
-- =============================================================================

-- ────────────────────────────────────────────────────────────────────────────
-- Tabela: cuidador (global — lookup de autenticação)
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS neuroassist_global.cuidador (
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
    CONSTRAINT ck_cuidador_global_papel  CHECK (papel  IN ('COORDENADOR_CLINICA','CUIDADORA','PACIENTE_APOIO')),
    CONSTRAINT ck_cuidador_global_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_cuidador_global_login_active
    ON neuroassist_global.cuidador (lower(login))
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS ix_cuidador_global_tenant_id ON neuroassist_global.cuidador (tenant_id);
CREATE INDEX IF NOT EXISTS ix_cuidador_global_status     ON neuroassist_global.cuidador (status);
CREATE INDEX IF NOT EXISTS ix_cuidador_global_papel      ON neuroassist_global.cuidador (papel);

COMMENT ON TABLE  neuroassist_global.cuidador IS 'ACS: global caregiver lookup — usado pelo fluxo de autenticação (sem tenant context). credential_hash NUNCA via API.';

-- ────────────────────────────────────────────────────────────────────────────
-- Tabela: caregiver_patient_link (global — espelho para operações sem tenant)
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS neuroassist_global.caregiver_patient_link (
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
    CONSTRAINT fk_link_global_cuidador FOREIGN KEY (cuidador_id)
        REFERENCES neuroassist_global.cuidador (cuidador_id) ON DELETE RESTRICT,
    CONSTRAINT ck_link_global_status CHECK (status IN ('ACTIVE','REVOKED','SUSPENDED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_link_global_active_cuidador_paciente
    ON neuroassist_global.caregiver_patient_link (cuidador_id, paciente_id)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS ix_link_global_tenant_id   ON neuroassist_global.caregiver_patient_link (tenant_id);
CREATE INDEX IF NOT EXISTS ix_link_global_paciente_id ON neuroassist_global.caregiver_patient_link (paciente_id);
CREATE INDEX IF NOT EXISTS ix_link_global_cuidador_id ON neuroassist_global.caregiver_patient_link (cuidador_id);
CREATE INDEX IF NOT EXISTS ix_link_global_status      ON neuroassist_global.caregiver_patient_link (status);

COMMENT ON TABLE neuroassist_global.caregiver_patient_link IS 'ACS: global caregiver-patient link — espelho do schema empresa para operações sem tenant context.';
