-- init-schema.sql — executado pelo PostgreSQLContainer antes dos testes (edge-service)
--
-- =============================================================================
-- E2E-022 — este arquivo NAO cria mais tabelas.
--
-- Ele tinha 11 CREATE TABLE escritos a mao: uma copia do schema mantida em paralelo as
-- migrations, que podia divergir nos DOIS sentidos, e com `ddl-auto: none` nada validava a
-- diferenca. Era a mesma classe de defeito do C-12, agravada — no create-drop dos outros
-- servicos ao menos a entity era a fonte; aqui a fonte era um terceiro arquivo que nao
-- correspondia nem a entity nem a migration.
--
-- Agora ele so prepara o terreno: extensoes e schemas vazios. Quem desenha as tabelas e o
-- Flyway, pelas migrations reais, aplicadas em BaseIntegrationTest antes de o Spring subir —
-- exatamente como no deploy. Ver testing-conventions.md §4.1.
-- =============================================================================

-- Extensoes exigidas pela arquitetura (persistence.arch.json, D-068).
-- Espelham a producao para que o teste falhe cedo se faltar alguma.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "unaccent";

-- Schema global: registro de tenants + os indices globais do EDGE
-- (enrollment_directory, device_directory), criados pelas migrations globais.
CREATE SCHEMA IF NOT EXISTS neuroassist_global;
GRANT ALL PRIVILEGES ON SCHEMA neuroassist_global TO test;
ALTER DEFAULT PRIVILEGES IN SCHEMA neuroassist_global GRANT ALL ON TABLES TO test;
ALTER DEFAULT PRIVILEGES IN SCHEMA neuroassist_global GRANT ALL ON SEQUENCES TO test;

-- Schemas de tenant usados nos testes BDD/integracao. Producao usa nomes como 't_emp_dev'.
-- Os dois existem para exercitar isolamento entre empresas.
CREATE SCHEMA IF NOT EXISTS t_emp_test;
GRANT ALL PRIVILEGES ON SCHEMA t_emp_test TO test;
ALTER DEFAULT PRIVILEGES IN SCHEMA t_emp_test GRANT ALL ON TABLES TO test;
ALTER DEFAULT PRIVILEGES IN SCHEMA t_emp_test GRANT ALL ON SEQUENCES TO test;

CREATE SCHEMA IF NOT EXISTS t_emp_other;
GRANT ALL PRIVILEGES ON SCHEMA t_emp_other TO test;
ALTER DEFAULT PRIVILEGES IN SCHEMA t_emp_other GRANT ALL ON TABLES TO test;
ALTER DEFAULT PRIVILEGES IN SCHEMA t_emp_other GRANT ALL ON SEQUENCES TO test;
