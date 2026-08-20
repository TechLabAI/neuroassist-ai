-- init-schema.sql — executado pelo PostgreSQLContainer antes dos testes
-- Cria schemas necessários para o serviço acs-service

-- Extensions required by the architecture (persistence.arch.json, D-068).
-- Mirror the production V0001 so tests fail fast when an extension is missing.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp"; -- Provides uuid_generate_v4 fallback and is required by some uuid_v7 implementations.
CREATE EXTENSION IF NOT EXISTS "pgcrypto"; -- gen_random_uuid() used in seed migrations; some uuid_v7 implementations build on it.
CREATE EXTENSION IF NOT EXISTS "pg_trgm"; -- Trigram indexes for substring search on names, codes and descriptions.
CREATE EXTENSION IF NOT EXISTS "unaccent"; -- Accent-insensitive search (LOWER(unaccent(nome)) LIKE ...).

-- Global schema: holds Tenant and SigningKey (neuroassist_global.*).
-- Required for TenantJpaEntity (@Table(schema="neuroassist_global")) and ddl-auto=create-drop.
CREATE SCHEMA IF NOT EXISTS neuroassist_global;

GRANT ALL PRIVILEGES ON SCHEMA neuroassist_global TO test;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA neuroassist_global TO test;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA neuroassist_global TO test;
ALTER DEFAULT PRIVILEGES IN SCHEMA neuroassist_global GRANT ALL ON TABLES TO test;
ALTER DEFAULT PRIVILEGES IN SCHEMA neuroassist_global GRANT ALL ON SEQUENCES TO test;

-- Empresa test schema: used in BDD scenarios for multi-tenant data.
CREATE SCHEMA IF NOT EXISTS t_emp_test;

GRANT ALL PRIVILEGES ON SCHEMA t_emp_test TO test;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA t_emp_test TO test;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA t_emp_test TO test;
ALTER DEFAULT PRIVILEGES IN SCHEMA t_emp_test GRANT ALL ON TABLES TO test;
ALTER DEFAULT PRIVILEGES IN SCHEMA t_emp_test GRANT ALL ON SEQUENCES TO test;
