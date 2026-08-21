-- HIST migration — faixa V0001..V0099 — owner: hist-service
-- =============================================================================
-- Migration: V0007__hist_reference_tables.sql
-- Service:   hist-service
-- Schema:    empresa (per-tenant)
-- Creates stub reference tables used by HistApplication JPA entities.
-- =============================================================================

CREATE TABLE IF NOT EXISTS actor_ref (
    actor_ref_id  uuid         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     varchar(255) NOT NULL,
    status        varchar(50)  NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (actor_ref_id)
);

CREATE TABLE IF NOT EXISTS fact_type (
    fact_type_id  uuid         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     varchar(255) NOT NULL,
    status        varchar(50)  NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (fact_type_id)
);

CREATE TABLE IF NOT EXISTS hash_chain (
    hash_chain_id uuid         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     varchar(255) NOT NULL,
    status        varchar(50)  NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (hash_chain_id)
);

CREATE TABLE IF NOT EXISTS subject_ref (
    subject_ref_id uuid        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      varchar(255) NOT NULL,
    status         varchar(50)  NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (subject_ref_id)
);
