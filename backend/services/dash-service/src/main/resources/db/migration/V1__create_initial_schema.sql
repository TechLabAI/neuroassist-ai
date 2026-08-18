-- =============================================================================
-- Migration: V1__create_initial_schema.sql
-- Service:   dash-service
-- Schema:    neuroassist_global
-- Generated: 2026-08-18 by be-migration-generator.py
--
-- Edit column types / constraints before applying to a real database.
-- Run: ./gradlew :dash-service:integrationTest to validate against Testcontainers.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS neuroassist_global;

-- WARNING: no fields parsed for table 3.1 `PanelSession` (in-memory registry)

-- WARNING: no fields parsed for table 3.2 `NotificationEnvelope` (in-flight, transient)

-- WARNING: no fields parsed for table 3.3 `PanelViewModel` family (transient)
