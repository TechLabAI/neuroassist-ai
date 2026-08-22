-- PAC migration — faixa V0200..V0299 — owner: pac-service
-- =============================================================================
-- Migration: V0208__pac_paciente_erasure.sql
-- Service:   pac-service
-- Schema:    empresa (per-tenant)
-- Motivo:    E2E-012 / Flow-04 — apagar os dados pessoais de um paciente (LGPD).
-- =============================================================================
--
-- Por que anonimizar em vez de apagar a linha:
--
--   * `comando` e `paciente_cuidador` referenciam `paciente.id`. Apagar a linha
--     exigiria apagar em cascata o histórico clínico junto — o oposto do que a
--     LGPD pede, que é remover o dado pessoal preservando o registro do que
--     aconteceu.
--   * O hist-service já resolve a mesma tensão do mesmo jeito (ADR-006): o
--     crypto-shredding torna o payload ilegível e mantém a cadeia de hash
--     verificável. Aqui é o análogo: o identificador sobrevive, o nome não.
--
-- `erased_at` é o que distingue "paciente sem nome cadastrado" de "paciente cujo
-- nome foi apagado a pedido" — sem essa coluna as duas situações seriam
-- indistinguíveis, e a segunda precisa ser auditável.

ALTER TABLE paciente
    ADD COLUMN IF NOT EXISTS erased_at timestamptz NULL;

COMMENT ON COLUMN paciente.erased_at IS
    'Quando os dados pessoais foram apagados a pedido (LGPD). NULL = paciente ativo. '
    'A linha e o id permanecem para preservar integridade referencial e a trilha de '
    'auditoria; o nome é substituído por um marcador.';

CREATE INDEX IF NOT EXISTS ix_paciente_erased_at
    ON paciente (tenant_id, erased_at)
 WHERE erased_at IS NOT NULL;
