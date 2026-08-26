-- HIST migration — faixa V0001..V0099 — owner: hist-service
-- =============================================================================
-- Migration: V0009__hist_drop_dead_tables.sql
-- Service:   hist-service
-- Schema:    empresa (per-tenant)
-- Motivo:    E2E-024 — remoção de scaffolding morto.
--
-- As três tabelas abaixo foram criadas em V0007 como stubs de referência para
-- adapters que nunca foram implementados (todos os métodos lançavam
-- UnsupportedOperationException). Nenhuma delas é referenciada por nenhum
-- application.service:
--
--   hash_chain   → a cadeia de hash real vive em audit_record (stream_key,
--                  sequence_no, prev_hash, record_hash) e é calculada pelo
--                  Sha256HashChainAdapter / HashChainPort. A tabela hash_chain
--                  era um caminho paralelo nunca conectado.
--
--   actor_ref    → os dados de ator vêm do ACS via ActorDirectoryPort /
--                  AcsActorDirectoryClient. Nunca há persistência local.
--
--   subject_ref  → os dados de sujeito são denormalizados em audit_record
--                  (subject_type, subject_id). Nunca há tabela de referência
--                  separada em uso.
--
--   fact_type    → o vocabulário de fact types é gerenciado como enum em
--                  P1-P8 (FactType.java). O adapter DB (HIST-023) era P9 mas
--                  nunca foi conectado a nenhum application.service. Mesmo
--                  padrão das demais tabelas de V0007.
--
-- Os adapters, entidades JPA e repositórios correspondentes foram removidos do
-- hist-service em conjunto com esta migration.
-- =============================================================================

DROP TABLE IF EXISTS hash_chain;
DROP TABLE IF EXISTS actor_ref;
DROP TABLE IF EXISTS subject_ref;
DROP TABLE IF EXISTS fact_type;
