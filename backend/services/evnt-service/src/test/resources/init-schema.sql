-- init-schema.sql — executado pelo PostgreSQLContainer antes dos testes
-- Cria schema de tenant de teste para o serviço evnt-service

-- Extensions required by the architecture (persistence.arch.json, D-068).
-- Mirror the production V0001 so tests fail fast when an extension is missing.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp"; -- Provides uuid_generate_v4 fallback and is required by some uuid_v7 implementations.
CREATE EXTENSION IF NOT EXISTS "pgcrypto"; -- gen_random_uuid() used in seed migrations; some uuid_v7 implementations build on it.
CREATE EXTENSION IF NOT EXISTS "pg_trgm"; -- Trigram indexes for substring search on names, codes and descriptions.
CREATE EXTENSION IF NOT EXISTS "unaccent"; -- Accent-insensitive search (LOWER(unaccent(nome)) LIKE ...).

CREATE SCHEMA IF NOT EXISTS t_emp_test;

GRANT ALL PRIVILEGES ON SCHEMA t_emp_test TO test;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA t_emp_test TO test;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA t_emp_test TO test;
