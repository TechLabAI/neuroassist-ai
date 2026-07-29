<!-- OUTPUT-SCHEMA
- Riscos
- Gaps Conhecidos
- Suposicoes a Validar
- Dependencias Criticas
-->

# 06 — Risks, Gaps, Assumptions

## Riscos
- **R1 — Migração destrutiva em produção.** Uma DDL de drop/rename mal revisada pode corromper
  dados de tenant. Mitigação: guardrail de "no automatic destructive DDL" + revisão obrigatória
  (ADR-005 local).
- **R2 — Custo de fan-out por tenant.** "Migrate all tenants" cresce linearmente com o número de
  empresas; uma migração pesada pode ter janela longa. Mitigação: aplicar por schema com lock,
  parar no primeiro erro, reportar schema que falhou.
- **R3 — Privilégio elevado.** MIG detém credenciais DDL; vazamento compromete todos os tenants.
  Mitigação: role dedicada, isolada das roles DML dos serviços; least privilege.
- **R4 — Drift silencioso entre template e schemas provisionados.** Um schema antigo pode ficar
  atrás do template. Mitigação: `validate` + `info` por schema; fail-fast em checksum mismatch.
- **R5 — Concorrência de execução.** Dois runs simultâneos no mesmo schema. Mitigação: advisory
  lock por schema.

## Gaps Conhecidos
- **G1 — Deprovisioning/offboarding de tenant** não automatizado no MVP (operação destrutiva
  manual).
- **G2 — Sem orquestração zero-downtime** (expand-contract / online schema change) no MVP.
- **G3 — Arquivamento/particionamento** de dados históricos fora de escopo do MVP.
- **G4 — Gatilho de provisionamento.** Contrato exato do trigger síncrono vindo de ACS (interface
  interna) a ser definido em `/be-architect-specs`.
- **G5 — Execução de purge/anonimização LGPD** em linhas de domínio pertence aos serviços de
  negócio (ex.: HIST); MIG só fornece a estrutura — fronteira a confirmar.

## Suposicoes a Validar
- **A1 —** "Centralized Flyway (all schemas)" é a decisão final (vs Flyway por serviço). Confirmado
  no registry (`description`); registrar como ADR-002 local.
- **A2 —** Uma única instância PostgreSQL atende o MVP (sem sharding/multi-DB).
- **A3 —** O template de tenant é idêntico para `familia` e `clinica` (sem divergência estrutural
  por tipo). A validar com PAC/ACS.
- **A4 —** Broker desabilitado no MVP; provisionamento síncrono é aceitável para o volume inicial
  (reavaliar se nº de pacientes/edge crescer — nota do stack).
- **A5 —** Migrações globais que o template de tenant depende estão sempre à frente (ordering
  global→tenant garantido pelo pipeline de deploy).

## Dependencias Criticas
- **PostgreSQL** disponível com role de DDL de alto privilégio para MIG.
- **REG (Eureka)** para registro/health (convenção de infra; não bloqueia a migração em si).
- **ACS (Cuidadores e Acesso)** como origem do gatilho de provisionamento de nova empresa.
- **Ordem de deploy:** MIG roda antes de qualquer serviço de negócio subir (todos assumem schema
  em head).
- **Trilha de atividade (D-423)** para observabilidade das execuções (best-effort; ES indisponível
  não bloqueia a migração).
