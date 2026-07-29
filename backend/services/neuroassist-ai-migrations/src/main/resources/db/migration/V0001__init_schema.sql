-- V0001__init_schema.sql
-- Platform baseline: create schema + extensions.
-- Range V0001–V0099 is reserved for platform-level DDL.
-- Module migrations start at V0100.

CREATE SCHEMA IF NOT EXISTS neuroassist_global;

-- Uncomment extensions as needed:
-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp" SCHEMA neuroassist_global;
-- CREATE EXTENSION IF NOT EXISTS pgcrypto SCHEMA neuroassist_global;
