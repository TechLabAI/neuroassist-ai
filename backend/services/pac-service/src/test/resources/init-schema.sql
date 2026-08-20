-- init-schema.sql — executado pelo PostgreSQLContainer antes dos testes
-- Cria schemas necessários para o serviço pac-service em testes

-- Extensions required by the architecture (persistence.arch.json, D-068).
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "unaccent";

-- Global schema: Hibernate ddl-auto=create-drop creates JPA tables here at startup.
-- MultiTenantConnectionProvider sets search_path to neuroassist_global when no tenant is active.
CREATE SCHEMA IF NOT EXISTS neuroassist_global;
GRANT ALL PRIVILEGES ON SCHEMA neuroassist_global TO test;
ALTER DEFAULT PRIVILEGES IN SCHEMA neuroassist_global GRANT ALL ON TABLES TO test;
ALTER DEFAULT PRIVILEGES IN SCHEMA neuroassist_global GRANT ALL ON SEQUENCES TO test;

-- Tenant schemas used in BDD tests: empresa-abc and empresa-xyz.
-- These schemas are empty; tables are found via search_path fallback to neuroassist_global.
CREATE SCHEMA IF NOT EXISTS "empresa-abc";
GRANT ALL PRIVILEGES ON SCHEMA "empresa-abc" TO test;
ALTER DEFAULT PRIVILEGES IN SCHEMA "empresa-abc" GRANT ALL ON TABLES TO test;
ALTER DEFAULT PRIVILEGES IN SCHEMA "empresa-abc" GRANT ALL ON SEQUENCES TO test;

CREATE SCHEMA IF NOT EXISTS "empresa-xyz";
GRANT ALL PRIVILEGES ON SCHEMA "empresa-xyz" TO test;
ALTER DEFAULT PRIVILEGES IN SCHEMA "empresa-xyz" GRANT ALL ON TABLES TO test;
ALTER DEFAULT PRIVILEGES IN SCHEMA "empresa-xyz" GRANT ALL ON SEQUENCES TO test;

-- Legacy schema kept for compatibility with existing tests.
CREATE SCHEMA IF NOT EXISTS t_emp_test;
GRANT ALL PRIVILEGES ON SCHEMA t_emp_test TO test;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA t_emp_test TO test;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA t_emp_test TO test;
