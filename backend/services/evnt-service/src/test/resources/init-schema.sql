-- init-schema.sql — executado pelo PostgreSQLContainer antes dos testes (e antes do Flyway).
-- Cria schemas e concede permissoes — as TABELAS sao criadas pelo Flyway em SchemaMigrations
-- (E2E-022). Com ddl-auto=validate, uma divergencia entre migration e entity derruba o contexto.

-- Extensions required by the architecture (persistence.arch.json, D-068).
-- Mirror the production V0001 so tests fail fast when an extension is missing.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp"; -- uuid_generate_v4() used in evnt migrations
CREATE EXTENSION IF NOT EXISTS "pgcrypto";  -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "pg_trgm";   -- trigram indexes
CREATE EXTENSION IF NOT EXISTS "unaccent";  -- accent-insensitive search

-- Global schema — Flyway vai criar neuroassist_global se nao existir, mas criar
-- antes garante que os GRANTs funcionem antes que as migrations globais rodem.
CREATE SCHEMA IF NOT EXISTS neuroassist_global;
GRANT ALL PRIVILEGES ON SCHEMA neuroassist_global TO test;

-- Tenant schema primário de integração
CREATE SCHEMA IF NOT EXISTS t_emp_test;
GRANT ALL PRIVILEGES ON SCHEMA t_emp_test TO test;

-- Tenant "empresa_demo" — usado pelos cenários BDD (SharedSteps.givenATenantEmpresademoWithPatientPac1).
-- Precisa existir aqui para que o Flyway tenha permissão de criar tabelas nele.
-- Antes de E2E-022, as tabelas eram criadas em neuroassist_global pelo ddl-auto=create-drop
-- (search_path da role 'test'), e o MultiTenantConnectionProvider as achava lá como fallback.
-- Com Flyway, as tabelas ficam no schema do tenant — logo empresa_demo precisa ser provisionado.
CREATE SCHEMA IF NOT EXISTS empresa_demo;
GRANT ALL PRIVILEGES ON SCHEMA empresa_demo TO test;

-- Tenant "empresa_other" — usado pelo cenário BDD de isolamento multi-tenant
-- (CaregiverConfirmationFeedbackSteps: "A caregiver from another tenant cannot confirm").
-- O servidor roteia a requisição para este schema via TenantContextFilter/MultiTenantConnectionProvider;
-- se o schema não existir, a query falha com 42P01 antes mesmo da guarda de tenant ser avaliada.
CREATE SCHEMA IF NOT EXISTS empresa_other;
GRANT ALL PRIVILEGES ON SCHEMA empresa_other TO test;
