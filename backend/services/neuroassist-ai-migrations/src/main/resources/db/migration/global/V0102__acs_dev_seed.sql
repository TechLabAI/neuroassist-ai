-- ACS migration — global schema — faixa V0100..V0199 — owner: acs-service
-- =============================================================================
-- Migration: V0102__acs_dev_seed.sql
-- Service:   acs-service
-- Schema:    neuroassist_global
--
-- Insere perfis default para validação E2E e desenvolvimento local.
-- Um tenant de desenvolvimento + um cuidador por papel (3 roles).
--
-- CREDENCIAIS DE DESENVOLVIMENTO (NUNCA usar em produção):
--   Tenant:           e2e00000-0000-0000-0000-000000000001
--   COORDENADOR:      coord@neuroassist.dev        / Test@1234!
--   CUIDADORA:        cuidadora@neuroassist.dev    / Test@1234!
--   PACIENTE_APOIO:   apoio@neuroassist.dev        / Test@1234!
--
-- Hashes BCrypt (strength 12) gerados por pgcrypto — compatíveis com
-- Spring Security BCryptPasswordEncoder (formato $2a$).
-- =============================================================================

-- Tenant E2E dev (schema_nome = t_emp_dev = schema empresa do ambiente local)
INSERT INTO neuroassist_global.tenant
    (tenant_id, nome, tipo, status, schema_nome, criado_em)
VALUES
    ('e2e00000-0000-0000-0000-000000000001',
     'NeuroAssist Dev Tenant',
     'CLINICA',
     'ACTIVE',
     't_emp_dev',
     now())
ON CONFLICT (tenant_id) DO NOTHING;

-- COORDENADOR_CLINICA — acesso completo de administração
INSERT INTO neuroassist_global.cuidador
    (cuidador_id, tenant_id, nome, login, credential_hash, papel, status, criado_em)
VALUES
    ('e2e00000-0000-0000-0000-000000000010',
     'e2e00000-0000-0000-0000-000000000001',
     'Coord Dev',
     'coord@neuroassist.dev',
     public.crypt('Test@1234!', public.gen_salt('bf', 12)),
     'COORDENADOR_CLINICA',
     'ACTIVE',
     now())
ON CONFLICT DO NOTHING;

-- CUIDADORA — acesso operacional (confirmar eventos, ler histórico dos seus pacientes)
INSERT INTO neuroassist_global.cuidador
    (cuidador_id, tenant_id, nome, login, credential_hash, papel, status, criado_em)
VALUES
    ('e2e00000-0000-0000-0000-000000000011',
     'e2e00000-0000-0000-0000-000000000001',
     'Cuidadora Dev',
     'cuidadora@neuroassist.dev',
     public.crypt('Test@1234!', public.gen_salt('bf', 12)),
     'CUIDADORA',
     'ACTIVE',
     now())
ON CONFLICT DO NOTHING;

-- PACIENTE_APOIO — acesso mínimo (cuidador de apoio / familiar)
INSERT INTO neuroassist_global.cuidador
    (cuidador_id, tenant_id, nome, login, credential_hash, papel, status, criado_em)
VALUES
    ('e2e00000-0000-0000-0000-000000000012',
     'e2e00000-0000-0000-0000-000000000001',
     'Apoio Dev',
     'apoio@neuroassist.dev',
     public.crypt('Test@1234!', public.gen_salt('bf', 12)),
     'PACIENTE_APOIO',
     'ACTIVE',
     now())
ON CONFLICT DO NOTHING;
