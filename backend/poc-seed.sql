-- PoC seed: insere dados iniciais no schema neuroassist_poc
-- Execute APÓS o Flyway rodar no startup dos serviços
-- Uso: psql -h localhost -U neuroassist -d neuroassist_poc -f poc-seed.sql

SET search_path = neuroassist_poc;

-- Tipos de gesto suportados no PoC
INSERT INTO tipo_gesto (id, nome, detectability_state, engine_feature_ref, created_at, updated_at)
VALUES
  ('tg-piscar-duplo-001',      'Piscar duas vezes',            'SUPPORTED', 'mp.blink.double.v1',         now(), now()),
  ('tg-piscar-esquerdo',       'Piscar olho esquerdo',         'SUPPORTED', 'mp.blink.left.v1',           now(), now()),
  ('tg-piscar-direito',        'Piscar olho direito',          'SUPPORTED', 'mp.blink.right.v1',          now(), now()),
  ('tg-sobrancelha-dupla',     'Levantar duas sobrancelhas',   'SUPPORTED', 'mp.brow.inner.up.v1',        now(), now()),
  ('tg-sobrancelha-direita',   'Levantar sobrancelha direita', 'SUPPORTED', 'mp.brow.outer.up.right.v1',  now(), now()),
  ('tg-sobrancelha-esquerda',  'Levantar sobrancelha esquerda','SUPPORTED', 'mp.brow.outer.up.left.v1',   now(), now())
ON CONFLICT (id) DO UPDATE SET detectability_state = EXCLUDED.detectability_state, updated_at = now();

-- Garante que gestos já existentes com 'ACTIVE' sejam promovidos para 'SUPPORTED' em runs anteriores
UPDATE tipo_gesto
SET detectability_state = 'SUPPORTED', updated_at = now()
WHERE id IN (
  'tg-piscar-esquerdo', 'tg-piscar-direito',
  'tg-sobrancelha-dupla', 'tg-sobrancelha-direita', 'tg-sobrancelha-esquerda'
)
AND detectability_state != 'SUPPORTED';

-- Perfil de detecção ativo (version=1)
INSERT INTO detection_profile (id, version, active, contract_version, published_at)
VALUES ('c5a3b2d1-0001-0001-0001-000000000001', 1, true, '1.0.0', now())
ON CONFLICT DO NOTHING;

-- Primitivos vinculando gestos ao perfil v1
INSERT INTO detection_profile_primitive (id, profile_id, tipo_gesto_id, nome, engine_feature_ref)
VALUES
  ('c5a3b2d1-0002-0002-0002-000000000002', 'c5a3b2d1-0001-0001-0001-000000000001', 'tg-piscar-duplo-001',     'Piscar duas vezes',            'mp.blink.double.v1'),
  ('c5a3b2d1-0002-0002-0002-000000000003', 'c5a3b2d1-0001-0001-0001-000000000001', 'tg-piscar-esquerdo',      'Piscar olho esquerdo',         'mp.blink.left.v1'),
  ('c5a3b2d1-0002-0002-0002-000000000004', 'c5a3b2d1-0001-0001-0001-000000000001', 'tg-piscar-direito',       'Piscar olho direito',          'mp.blink.right.v1'),
  ('c5a3b2d1-0002-0002-0002-000000000005', 'c5a3b2d1-0001-0001-0001-000000000001', 'tg-sobrancelha-dupla',    'Levantar duas sobrancelhas',   'mp.brow.inner.up.v1'),
  ('c5a3b2d1-0002-0002-0002-000000000006', 'c5a3b2d1-0001-0001-0001-000000000001', 'tg-sobrancelha-direita',  'Levantar sobrancelha direita', 'mp.brow.outer.up.right.v1'),
  ('c5a3b2d1-0002-0002-0002-000000000007', 'c5a3b2d1-0001-0001-0001-000000000001', 'tg-sobrancelha-esquerda', 'Levantar sobrancelha esquerda','mp.brow.outer.up.left.v1')
ON CONFLICT DO NOTHING;
