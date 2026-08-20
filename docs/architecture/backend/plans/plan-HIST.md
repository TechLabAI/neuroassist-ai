# Plano -- HIST (hist-service)

> Módulo: **HIST — Histórico e Auditoria** · Service: `hist-service` · Port: 8089
> Package: `com.neuroassist.hist` · Stack: Java 21, Spring Boot, PostgreSQL schema-per-empresa
> Messaging: **disabled** (REST/HTTPS sync only) · Multi-tenant: schema-per-empresa
> ADRs fundadores: ADR-004 (append-only), ADR-005 (sync idempotent ingestion), ADR-006 (hash chain)
> Gerado por: `/be-architect-backlog neuroassist-ai HIST` · 2026-08-16

---

## Status das prioridades

| P | Título | Status | Estimativa |
|---|---|---|---|
| P1 | Scaffold + contratos mínimos | ✅ done | M |
| P2 | Orquestração do domínio (core append + hash chain + leitura) | ✅ done | L |
| P3 | Persistência + migrations (JPA + Flyway) | ✅ done | L |
| P4 | Integração externa (ACS Feign + history controllers + producer stubs) | ✅ done | L |
| P5 | Security + multi-tenancy + LGPD guard | ✅ done | L |
| P7 | Observability + health | ✅ done | M |
| P8 | BDD end-to-end | ✅ done | XL |
| P9 | Hardening (concurrency retry + vocab catalog) | ✅ done | M |
| P10 | LGPD crypto-shredding (AES-256-GCM per-patient key erasure) | ✅ done | M |

> P6 (Messaging) **não existe** neste módulo: `stack.messaging.enabled=false`. Sem broker, sem consumers, sem producers.

**Legenda de estimativa:** M = 1-2 sessions | L = 2-3 sessions | XL = 3-4 sessions

---

## 0. Leitura executiva do estado atual

### Papel do serviço

`hist-service` é o **ledger imutável de auditoria** da plataforma NeuroAssist AI.
Funciona como sink: recebe audit facts dos produtores (EVNT, PAC, ACS, EDGE) via
`POST /api/v1/hist/audit-facts` e responde com o record persistido, posicionado e
encadeado por hash no stream `(tenant, subject)`. HIST nunca inicia escrita — é
sempre chamado pelos produtores. A leitura é oferecida a DASH e à biblioteca FE
`@neuroassist/hist` via `GET /history` e `GET /history/verify`.

Invariantes não-negociáveis:
- **Write-once**: nenhum endpoint de UPDATE ou DELETE existe (ADR-004).
- **Idempotência on factId**: mesmo fact submetido N vezes → 1 record (ADR-005).
- **Hash chain**: `recordHash = sha256(prevHash ‖ canonical(record))` por stream (ADR-006).
- **Tenant isolation**: todos os dados em `t_<empresa>`; sem cross-tenant.

### Estado atual (pós-ciclo P1–P10 — 2026-08-20)

- **Score**: 100/100 (Maduro).
- **Checklist**: 9/9 done (P1–P10, exceto P6 inexistente por design).
- **Gates**: lint=PASS, arch=PASS (RULE-01..09), test=PASS (92/92), build=PASS, e2e=PASS (30 BDD).
- **Bugs críticos resolvidos**:
  1. **ADR-006 hash chain mismatch** — PostgreSQL TIMESTAMPTZ arredonda (não trunca) nanossegundos sub-microsecond ao fazer INSERT. O hash era computado com o valor truncado, mas na verificação era relido o valor arredondado pelo banco, causando mismatch. Fix: `AuditLedgerAdapter.toEntity()` agora trunca `occurredAt` e `recordedAt` para microseconds antes de persistir via `instant.truncatedTo(ChronoUnit.MICROS)`.
  2. **Feign timeout YAML ignorado** — Spring Cloud OpenFeign 4.x / Spring Cloud 2025.0.1 não propaga `feign.client.config.acs-directory.readTimeout` ao cliente HTTP subjacente. Fix: bean programático `Request.Options` em `FeignClientConfig` lendo dos mesmos valores YAML com defaults de 2000ms.

### MVP scope

Dois surfaces REST:
1. **Ingestão** (`/audit-facts`): write-once, idempotente.
2. **Histórico** (`/history`, `/history/verify`): leitura paginada + verificação de cadeia.

Fora do MVP: broker/events (messaging disabled), UI própria (HIST é backend puro).
LGPD crypto-shredding implementado no P10 (AES-256-GCM per-patient key, `DELETE /{subjectId}/personal-data`).

### Estado multi-empresa: EmpresaContext configurado?

- **Schema alvo**: `t_<empresa>` (schema-per-empresa, não `neuroassist_global`).
- **Mecanismo**: `TenantContextHolder` próprio do módulo (implementado em P5) — extrai `tenant` do JWT claim.
- **EmpresaContextHolder global**: NÃO usado diretamente; HIST implementa seu próprio tenant-pinning via `TenantContextFilter`.
- **Propagação JMS**: não aplicável (messaging disabled).
- **Checagem obrigatória**: `TenantContextHolder.require()` em todos os adapters de persistência — confirmado em RULE-07.

---

## 1. Principais achados

### Pontos fortes

- Estrutura hexagonal completa e aderente às ArchUnit RULE-01..09.
- ADRs fundadores (ADR-004, ADR-005, ADR-006) claramente documentados e refletidos no código.
- Isolamento multi-tenant por schema-per-empresa verificado em teste de integração.
- SHA-256 hash chain determinístico com canonical JSON (sorted-key, microseconds truncados).
- 30 BDD cenários cobrindo golden path, idempotência, adulteração, segurança, LGPD.
- Messaging disabled: elimina complexidade de broker para o MVP.

### Gaps residuais (não bloqueadores)

| Gap | Severidade | Decisão |
|---|---|---|
| Messaging (HIST_AUDIT_FACT_RECORDED) | Baixo — intencional no MVP | D-490 — ativado em fase futura |

### Aderência às regras (RULE-01..09)

Todas as 9 regras confirmadas por `./gradlew :services:hist-service:architectureCheck` → PASS.

---

## 2. Estratégia geral

### Objetivo macro

✅ Concluído. `hist-service` funcional, testado e production-ready:
1. Aceita audit facts de 4 produtores com validação estrita (factType, payload LGPD, clock).
2. Persiste de forma imutável com hash chain verificável.
3. Serve histórico paginado e verificação de cadeia para DASH e FE.
4. Isola rigorosamente os dados por tenant schema-per-empresa.

### Ordem das prioridades (executada)

| P | Título | Status |
|---|---|---|
| P1 | Scaffold + contratos mínimos | ✅ DONE |
| P2 | Domínio + orchestração (append + hash + query) | ✅ DONE |
| P3 | Persistência + migrations | ✅ DONE |
| P4 | Integração externa + history REST | ✅ DONE |
| P5 | Security + multi-tenancy + LGPD | ✅ DONE |
| P7 | Observability | ✅ DONE |
| P8 | BDD e2e | ✅ DONE |
| P9 | Hardening | ✅ DONE |

---

## 3. Plano por prioridade

### Prioridade P1 -- Scaffold + contratos mínimos

Status: ✅ done

#### Issues vinculadas

- HIST-001 (scaffold) ✅
- HIST-002 (domain aggregate + value objects) ✅
- HIST-003 (inbound ports) ✅
- HIST-004 (outbound ports) ✅
- HIST-005 (ingestion REST controllers) ✅

#### Criterios de aceite

- [x] `./gradlew :services:hist-service:compileJava` exit 0.
- [x] `GET /actuator/health` → 200.
- [x] `POST /api/v1/hist/audit-facts` com body válido → 201.
- [x] `POST` com factType desconhecido → 422.
- [x] `GET /api/v1/hist/fact-types` → 200 com lista v1.

---

### Prioridade P2 -- Orquestração do domínio (core append + hash chain + leitura)

Status: ✅ done

#### Issues vinculadas

- HIST-006 (RecordAuditFactService) ✅
- HIST-007 (HashChainPort impl + FactTypeCatalogService) ✅
- HIST-008 (HistoryQueryService + ChainVerificationService) ✅

#### Criterios de aceite

- [x] Teste unitário de `RecordAuditFactService` com ports mockados: append novo → 201; replay → 200.
- [x] Teste unitário de `Sha256HashChainAdapter`: vetor de referência fixo confirma hash.
- [x] Teste unitário de `ChainVerificationService`: N records intactos → intact; 1 adulterado → broken.

---

### Prioridade P3 -- Persistência + migrations (JPA + Flyway)

Status: ✅ done

#### Issues vinculadas

- HIST-009 (JPA entities) ✅
- HIST-010 (JPA adapters) ✅
- HIST-011 (Flyway migrations V0001..V0005) ✅
- INT-HIST-004 (migration integration test) ✅

#### Criterios de aceite

- [x] `ApplicationContextLoadsIT` → PASS.
- [x] `FlywayMigrationTest` via Testcontainers → PASS.
- [x] `AuditLedgerAdapter.insert()` usa `EntityManager.persist()`.
- [x] `StreamHeadAdapter.lockAndFetchTip()` usa `@Lock(PESSIMISTIC_WRITE)`.
- [x] `./gradlew :services:hist-service:architectureCheck` → PASS.
- [x] `./gradlew :services:hist-service:test` → PASS.

#### Nota crítica — Bug resolvido em P8

O bug de hash chain (PostgreSQL TIMESTAMPTZ rounding) foi identificado e resolvido durante P8:
`AuditLedgerAdapter.toEntity()` agora trunca `occurredAt` e `recordedAt` para microseconds antes de persistir. Sem este fix, a cadeia era quebrada por arredondamento do banco após INSERT.

---

### Prioridade P4 -- Integração externa + history REST controllers

Status: ✅ done

#### Issues vinculadas

- HIST-012 (AcsActorDirectoryClient + ActorDirectoryAdapter) ✅
- HIST-013 (GET /history, GET /history/verify) ✅
- INT-HIST-001 (producer contract tests) ✅
- INT-HIST-002 (ACS integration test) ✅

#### Criterios de aceite

- [x] `GET /history` com `hist:read` + ACS OK → resultados filtrados por scope.
- [x] `GET /history` com ACS indisponível → 503.
- [x] `GET /history` com `hist:read:all` → sem chamada ACS.
- [x] Testes de produtor para EVNT, PAC, ACS, EDGE passam.

#### Nota crítica — Bug resolvido em P8

O bug de Feign timeout (YAML não propagado em Spring Cloud 2025.0.1) foi identificado e resolvido durante P8:
bean programático `Request.Options` em `FeignClientConfig` garante timeout de 2s independente do cliente HTTP subjacente. O `AcsIntegrationTest.resolvePatientScope_acsTimeout_throwsActorDirectoryUnavailableException` agora passa em 2s (WireMock delay de 3.5s).

---

### Prioridade P5 -- Security + multi-tenancy + LGPD guard

Status: ✅ done

#### Issues vinculadas

- HIST-014 (OAuth2 resource server + scopes) ✅
- HIST-015 (TenantContextFilter + TenantContextHolder) ✅
- HIST-016 (LGPD payload guard) ✅
- INT-HIST-003 (multi-tenant isolation test) ✅
- INT-HIST-005 (GWY routing contract test) ✅

#### Criterios de aceite

- [x] Sem token → 401 em todos os endpoints.
- [x] `hist:read` em POST → 403.
- [x] Body `tenantId != JWT.tenant` → 403 `/errors/tenant-mismatch`.
- [x] Record de `t_empresa_a` invisível para JWT `tenant=empresa_b`.
- [x] Payload com chave `"image"` → 422 `/errors/forbidden-payload`.

---

### Prioridade P7 -- Observability + health

Status: ✅ done

#### Issues vinculadas

- HIST-017 (Micrometer metrics: 6 meters) ✅
- HIST-018 (structured logging + X-Correlation-Id) ✅
- HIST-019 (actuator health + readiness) ✅

#### Criterios de aceite

- [x] `GET /actuator/prometheus` inclui `hist_audit_fact_recorded_total`, `hist_duplicate_fact_ignored_total`, `hist_chain_verification_total`, `hist_ingestion_latency_seconds`, `hist_acs_scope_resolution_total`.
- [x] Log de append em JSON contém `correlationId`, `tenant`, `factType`, `streamKey`.
- [x] Log não contém `payload` em nenhuma condição.
- [x] `GET /actuator/health/readiness` → DOWN quando Testcontainers PostgreSQL desligado.

---

### Prioridade P8 -- BDD end-to-end

Status: ✅ done

#### Issues vinculadas

- HIST-020 (BDD steps: audit-fact-ingestion + idempotency-and-concurrency) ✅
- HIST-021 (BDD steps: hash-chain + history-query + security-and-auth) ✅
- INT-HIST-006 (producer e2e: ingest → query → verify) ✅

#### Criterios de aceite

- [x] `./gradlew :services:hist-service:test` → 0 falhas, 0 pendentes.
- [x] 5 features com todos os cenários cobertos (30 total).
- [x] Cenário de adulteração de hash em chain-verification usa SQL direto no Testcontainers.
- [x] INT-HIST-006 completa em < 30s.

#### Bugs resolvidos nesta prioridade

1. **Hash chain mismatch (ADR-006)**: PostgreSQL TIMESTAMPTZ rounding — resolvido em `AuditLedgerAdapter.truncateMicros()`.
2. **Feign timeout YAML ignorado**: Spring Cloud OpenFeign 4.x — resolvido com `Request.Options` bean em `FeignClientConfig`.

---

### Prioridade P9 -- Hardening

Status: ✅ done

#### Issues vinculadas

- HIST-022 (concurrency retry hardening) ✅
- HIST-023 (FactType vocabulary catalog configurável) ✅

#### Criterios de aceite

- [x] 10 threads simultâneas no mesmo stream → `sequenceNo` consecutivos sem gap.
- [x] `GET /api/v1/hist/fact-types` retorna 10 tipos v1 seed.
- [x] Adicionar tipo em `application.yml` sem recompilar → aceito na ingestão.

---

## 4. Dependências externas

| Dependência | Tipo | Notas |
|---|---|---|
| ACS (`acs-service`) | REST outbound | Somente em leituras `hist:read`; não em ingestão (ADR-005). Fail-closed. WireMock em CI. |
| PostgreSQL | Infra | Schema `t_<empresa>`; migrations via MIG (sem DDL no boot). |
| MIG (`neuroassist-ai-migrations`) | Infra | Aplica `V<n>__hist_*.sql`; hist-service não invoca Flyway em produção. |
| REG (`neuroassist-ai-service-registry`) | Infra | Eureka registration. |
| GWY (`neuroassist-ai-api-gateway`) | Infra | Edge routing; JWT relay; X-Correlation-Id gerado se ausente. |
| ACS JWK set | Auth | JWT validation local; `neuroassist.acs.jwk-set-uri` por profile. |

---

## 5. Restrições arquiteturais

- **ArchUnit RULE-01..09**: todas passando — confirmado por `architectureCheck` em 2026-08-20.
- **ADR-004**: zero endpoint de mutação exposto (nenhum PUT, PATCH, DELETE em `/audit-facts`).
- **ADR-005**: ingestão não bloqueia em ACS.
- **ADR-006**: hash chain computado na mesma transação do append; truncamento para microseconds obrigatório.
- **LGPD**: `payload` nunca logado; guard rejeita chaves biométricas.

---

## 6. Referências

- `docs/architecture/backend/services/hist-service/` (todos os documentos de visão, specs e contracts)
- `docs/architecture/backend/services/hist-service/contracts/api-contract.md`
- `docs/architecture/backend/services/hist-service/contracts/data-model.md`
- `docs/architecture/backend/services/hist-service/contracts/integration-contracts.md`
- `docs/architecture/backend/services/hist-service/contracts/event-contracts.md`
- `docs/architecture/backend/services/hist-service/spec/backend/SPEC-01..SPEC-10`
- `docs/architecture/backend/services/hist-service/spec/backend/SPEC-VAL-bdd-coverage-report.md`
- `docs/architecture/backend/services/hist-service/tests/cucumber/*.feature`
- `docs/architecture/backend/services/hist-service/adr/ADR-004,005,006.md`
- `docs/architecture/backend/services/hist-service/generator-config.yml`
- `docs/architecture/backend/issues/issues_map-HIST.md`
