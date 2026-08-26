-- init-schema.sql — executado pelo PostgreSQLContainer antes dos testes (e antes do Flyway).
-- Cria schemas e concede permissoes — as TABELAS sao criadas pelo Flyway em SchemaMigrations
-- (E2E-022). Com ddl-auto=validate, uma divergencia entre migration e entity derruba o contexto.

-- ────────────────────────────────────────────────────────────────────────────
-- Extensions required by the architecture (persistence.arch.json, D-068).
-- ────────────────────────────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "unaccent";

-- ────────────────────────────────────────────────────────────────────────────
-- Global schema — Flyway vai criar neuroassist_global se nao existir, mas criar
-- antes garante que o GRANT funcione antes que as migrations globais rodem.
-- ────────────────────────────────────────────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS neuroassist_global;
GRANT ALL PRIVILEGES ON SCHEMA neuroassist_global TO test;

-- ────────────────────────────────────────────────────────────────────────────
-- Tenant schemas for integration tests
-- t_emp_test  — primary test tenant
-- t_emp_other — secondary tenant for cross-tenant isolation tests (INT-GEST-005)
-- ────────────────────────────────────────────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS t_emp_test;
CREATE SCHEMA IF NOT EXISTS t_emp_other;

GRANT ALL PRIVILEGES ON SCHEMA t_emp_test  TO test;
GRANT ALL PRIVILEGES ON SCHEMA t_emp_other TO test;
