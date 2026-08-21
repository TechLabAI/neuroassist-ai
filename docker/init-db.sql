-- init-db.sql — Inicialização do PostgreSQL para dev E2E local
-- Executado pelo entrypoint do container postgres (uma vez, na criação do volume).
-- DB neuroassist_dev e user neuroassist já criados pelas env vars do compose.

-- Extensões necessárias pelas migrations
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Schema global (ACS + plataforma) — migrations-global cria as tabelas
CREATE SCHEMA IF NOT EXISTS neuroassist_global;

-- Schema de empresa dev — migrations-empresa cria as tabelas do módulo
CREATE SCHEMA IF NOT EXISTS t_emp_dev;

-- Garante que o usuário neuroassist pode operar nos schemas
GRANT ALL ON SCHEMA neuroassist_global TO neuroassist;
GRANT ALL ON SCHEMA t_emp_dev TO neuroassist;
