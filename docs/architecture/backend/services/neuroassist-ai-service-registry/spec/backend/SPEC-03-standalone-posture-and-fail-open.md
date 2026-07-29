# SPEC-03 — Standalone Posture and Fail-Open Discovery

## Objetivo
Especificar a postura de nó único do MVP (ADR-002) e o comportamento fail-open que
protege o restante da plataforma de uma indisponibilidade breve de REG (sub-feature
crítica extraída do brief / princípio arquitetural #3 da visão).

## Escopo
**In:** decisão de single node (sem HA peer-replicada), self-flags de
`register-with-eureka`/`fetch-registry`, e o comportamento de cache client-side que
torna uma reinicialização de REG não-disruptiva.
**Out:** replicação peer real (passo futuro, deferido — ADR-002 Consequências),
config client-side `eureka.client.*` de cada serviço (fora do escopo de REG).

## Decisões técnicas
- **ADR-002 (local):** um único nó de registry é aceitável no MVP — sem código de
  negócio, sem peer replication. Aceito o risco de single point of failure para
  *novos* registros e discovery fresco durante um restart, mitigado pelo cache dos
  clientes.
- **Fail-open para clientes** (`01-architectural-vision.md` §Architectural
  Principles #3): outages de discovery devem degradar graciosamente; clientes usam
  o último registry conhecido em cache.
- Single node ⇒ `register-with-eureka=false`, `fetch-registry=false`.

## Implementação
- `application.yml`: self-flags de single-node, threshold de self-preservation,
  intervalo do loop de eviction, janela de expiração de lease coerente com o
  heartbeat dos clientes (30s/90s default).
- Métricas expostas (SPEC-07): renewals/min, evictions, apps registrados — base
  para decidir quando ligar HA no futuro.

## Casos de borda
- REG reinicia → clientes seguem roteando pelo registry cacheado até o
  re-registro completar; nenhuma parada permanente.
- Queda transitória do renewal rate (ex.: partição de rede) → self-preservation
  ativa, membership preservada; não esvazia o registry.
- Frota cresce a ponto de justificar HA → abrir nova rodada de vision para a
  decisão de peer replication (não decidida aqui — `06-risks-gaps-assumptions.md`
  §Gaps Conhecidos).

## Critérios de aceite (EARS)
`THE SYSTEM SHALL run as a single node for the MVP and SHALL NOT attempt peer replication.`
`WHEN REG restarts, THE SYSTEM SHALL cause no permanent outage — clients continue routing on their cached registry and re-register automatically.`
`WHILE the observed renewal rate is below the self-preservation threshold, THE SYSTEM SHALL preserve membership and stop evicting.`
`WHEN the renewal rate recovers above the threshold, THE SYSTEM SHALL resume normal eviction.`
`WHERE peer-replicated HA is later enabled, THE SYSTEM SHALL replicate registry state across peers (deferred).`
