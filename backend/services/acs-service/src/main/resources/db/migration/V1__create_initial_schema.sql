-- =============================================================================
-- Migration: V1__create_initial_schema.sql
-- Service:   acs-service
-- Generated: 2026-07-29 by be-migration-generator.py
-- Source:    contracts/data-model.md (extraído de 5 blocos ```sql```)
--
-- ATENÇÃO: este arquivo é GERADO. Para alterar DDL:
--   1. Edite contracts/data-model.md (blocos ```sql```).
--   2. Re-rode: python scripts/be-migration-generator.py <service> --spec-dir <path> --force
-- =============================================================================

-- schema: global
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- schema: global
CREATE TABLE tenant (
    id                          uuid          NOT NULL DEFAULT uuid_generate_v7(),
    nome                        varchar(150)  NOT NULL,
    tipo                        varchar(20)   NOT NULL,
    status                      varchar(20)   NOT NULL DEFAULT 'provisioning',
    schema_name                 varchar(63)   NULL,
    criado_em                   timestamptz   NOT NULL DEFAULT now(),
    criado_por_usuario_id       uuid          NULL,
    atualizado_em               timestamptz   NOT NULL DEFAULT now(),
    atualizado_por_usuario_id   uuid          NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_tenant_tipo   CHECK (tipo IN ('familia','clinica')),
    CONSTRAINT ck_tenant_status CHECK (status IN ('provisioning','active','suspended')),
    CONSTRAINT ck_tenant_active_needs_schema
        CHECK (status <> 'active' OR schema_name IS NOT NULL)
);

CREATE UNIQUE INDEX ux_tenant_schema_name ON tenant (schema_name) WHERE schema_name IS NOT NULL;
CREATE INDEX ix_tenant_status ON tenant (status);

COMMENT ON TABLE tenant IS 'ACS: authoritative tenant (empresa) directory; root of the tenant tree (ADR-004).';
COMMENT ON COLUMN tenant.schema_name IS 'Per-tenant schema t_<tenantId> materialized by MIG; required before active.';

-- schema: global
CREATE TABLE signing_key (
    id                  uuid          NOT NULL DEFAULT uuid_generate_v7(),
    kid                 varchar(80)   NOT NULL,
    algorithm           varchar(20)   NOT NULL DEFAULT 'RS256',
    public_jwk          jsonb         NOT NULL,
    private_key_enc     bytea         NOT NULL,
    status              varchar(20)   NOT NULL DEFAULT 'active',
    not_before          timestamptz   NOT NULL DEFAULT now(),
    retired_at          timestamptz   NULL,
    criado_em           timestamptz   NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT ux_signing_key_kid    UNIQUE (kid),
    CONSTRAINT ck_signing_key_status CHECK (status IN ('active','retired')),
    CONSTRAINT ck_signing_key_jwk_object CHECK (jsonb_typeof(public_jwk) = 'object')
);

CREATE UNIQUE INDEX ux_signing_key_single_active ON signing_key (status) WHERE status = 'active';
CREATE INDEX ix_signing_key_status ON signing_key (status);

COMMENT ON TABLE signing_key IS 'ACS: JWT signing keys; only public_jwk is published at /oauth2/jwks (ADR-005).';
COMMENT ON COLUMN signing_key.private_key_enc IS 'Encrypted private key material; never exposed via any API.';

-- schema: empresa
CREATE TABLE cuidador (
    id                          uuid          NOT NULL DEFAULT uuid_generate_v7(),
    tenant_id                   uuid          NOT NULL,
    nome                        varchar(150)  NOT NULL,
    login                       varchar(150)  NOT NULL,
    credential_hash             varchar(200)  NOT NULL,
    papel                       varchar(30)   NOT NULL,
    status                      varchar(20)   NOT NULL DEFAULT 'active',
    criado_em                   timestamptz   NOT NULL DEFAULT now(),
    criado_por_usuario_id       uuid          NULL,
    atualizado_em               timestamptz   NOT NULL DEFAULT now(),
    atualizado_por_usuario_id   uuid          NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_cuidador_papel  CHECK (papel IN ('cuidadora','coordenador_clinica')),
    CONSTRAINT ck_cuidador_status CHECK (status IN ('active','inactive'))
);

CREATE UNIQUE INDEX ux_cuidador_login_active
    ON cuidador (tenant_id, lower(login))
    WHERE status = 'active';

CREATE INDEX ix_cuidador_tenant ON cuidador (tenant_id);
CREATE INDEX ix_cuidador_papel  ON cuidador (tenant_id, papel);

COMMENT ON TABLE cuidador IS 'ACS: caregiver account, tenant-scoped; identity + access metadata only (LGPD).';
COMMENT ON COLUMN cuidador.credential_hash IS 'bcrypt/argon2 hash; never cleartext, never returned by any API.';
COMMENT ON COLUMN cuidador.tenant_id IS 'Logical FK to global tenant.id (PS.003, no physical cross-schema FK).';

-- schema: empresa
CREATE TABLE caregiver_patient_link (
    id                          uuid          NOT NULL DEFAULT uuid_generate_v7(),
    tenant_id                   uuid          NOT NULL,
    cuidador_id                 uuid          NOT NULL,
    paciente_id                 uuid          NOT NULL,
    status                      varchar(20)   NOT NULL DEFAULT 'active',
    criado_em                   timestamptz   NOT NULL DEFAULT now(),
    criado_por_usuario_id       uuid          NULL,
    atualizado_em               timestamptz   NOT NULL DEFAULT now(),
    atualizado_por_usuario_id   uuid          NULL,
    revogado_em                 timestamptz   NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_link_status CHECK (status IN ('active','revoked')),
    CONSTRAINT fk_link_cuidador
        FOREIGN KEY (cuidador_id) REFERENCES cuidador (id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX ux_link_active
    ON caregiver_patient_link (cuidador_id, paciente_id)
    WHERE status = 'active';

CREATE INDEX ix_link_paciente ON caregiver_patient_link (tenant_id, paciente_id) WHERE status = 'active';
CREATE INDEX ix_link_cuidador ON caregiver_patient_link (cuidador_id, status);

COMMENT ON TABLE caregiver_patient_link IS 'ACS: caregiver-patient linkage; single source of alarm routing + data-scope (ADR-006).';
COMMENT ON COLUMN caregiver_patient_link.paciente_id IS 'Logical FK to PAC Paciente (PS.003); validated at link time, not a DB FK.';
