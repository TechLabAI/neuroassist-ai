# Issues Map — HIST (Histórico e Auditoria)

> Service: `hist-service` · Port: 8089 · Package: `com.neuroassist.hist`
> Generated: 2026-08-16 · Total: 23 functional + 6 integration = 29 issues

---

## Por prioridade

| Issue | Título | P | Tamanho | Layer |
|---|---|---|---|---|
| HIST-001 | Hexagonal scaffold: package tree + Spring Boot entry point | P1 | M | infrastructure |
| HIST-002 | Domain: AuditRecord aggregate + value objects | P1 | M | domain |
| HIST-003 | Inbound ports (use-case interfaces) | P1 | S | application |
| HIST-004 | Outbound ports (repository + infrastructure interfaces) | P1 | S | application |
| HIST-005 | REST controllers: ingestion surface (POST /audit-facts, GET /audit-facts/{id}, GET /fact-types) | P1 | M | api |
| HIST-006 | RecordAuditFactService: golden path append (idempotência + chaining) | P2 | L | application |
| HIST-007 | HashChainPort implementation: SHA-256 + canonicalization v1 | P2 | M | infrastructure |
| HIST-008 | HistoryQueryService + ChainVerificationService: caminho de leitura completo | P2 | M | application |
| HIST-009 | JPA entities: AuditRecordEntity, IdempotencyEntryEntity, StreamHeadEntity | P3 | M | infrastructure |
| HIST-010 | JPA adapters: AuditLedgerAdapter, IdempotencyAdapter, StreamHeadAdapter | P3 | M | infrastructure |
| HIST-011 | Flyway migration scripts V0001..V0005 | P3 | M | infrastructure |
| INT-HIST-004 | MIG migration scripts integration test | P3 | S | integration |
| HIST-012 | AcsActorDirectoryClient (Feign) + ActorDirectoryAdapter | P4 | M | infrastructure |
| HIST-013 | REST controllers: history surface (GET /history, GET /history/verify) | P4 | M | api |
| INT-HIST-001 | Inbound contract tests: producer stubs (EVNT/PAC/ACS/EDGE) | P4 | M | integration |
| INT-HIST-002 | ACS ActorDirectory integration test | P4 | M | integration |
| HIST-014 | OAuth2 resource server: ACS JWK set local validation + scopes | P5 | M | infrastructure |
| HIST-015 | TenantContextHolder + schema-pinning filter | P5 | M | infrastructure |
| HIST-016 | LGPD payload guard: rejeição de chaves biométricas | P5 | S | domain |
| INT-HIST-003 | Multi-tenant schema isolation integration test | P5 | M | integration |
| INT-HIST-005 | GWY routing integration test | P5 | S | integration |
| HIST-017 | Micrometer metrics: contadores e timers do ledger | P7 | S | infrastructure |
| HIST-018 | Structured JSON logging + X-Correlation-Id propagation | P7 | S | infrastructure |
| HIST-019 | Actuator health + readiness endpoints | P7 | S | infrastructure |
| HIST-020 | BDD step definitions: audit-fact-ingestion + idempotency features | P8 | M | test |
| HIST-021 | BDD step definitions: hash-chain + history-query + security features | P8 | M | test |
| INT-HIST-006 | Producer end-to-end contract test: ingest → query → verify | P8 | M | integration |
| HIST-022 | Concurrency hardening: retry em stream-tip lock contention | P9 | M | application |
| HIST-023 | FactType vocabulary catalog: configurável + GET /fact-types | P9 | S | application |

---

## Por SPEC

| SPEC | Issues vinculadas |
|---|---|
| SPEC-01 (API Surface) | HIST-001, HIST-003, HIST-005, HIST-013 |
| SPEC-02 (Audit Ingestion & Hash Chaining) | HIST-002, HIST-006, HIST-007, HIST-016, HIST-023, INT-HIST-001 |
| SPEC-03 (History Query & Chain Verification) | HIST-008, HIST-013 |
| SPEC-04 (Data Model & Migrations) | HIST-009, HIST-010, HIST-011, INT-HIST-004 |
| SPEC-05 (Idempotency & Concurrency) | HIST-006, HIST-010, HIST-022 |
| SPEC-06 (Security) | HIST-014, HIST-015, HIST-016, INT-HIST-003, INT-HIST-005 |
| SPEC-07 (Observability & Health) | HIST-017, HIST-018, HIST-019 |
| SPEC-08 (BDD Test Scenarios) | HIST-020, HIST-021 |
| SPEC-09 (Messaging Architecture) | N/A (messaging disabled in MVP) |
| SPEC-10 (Integrations) | HIST-012, INT-HIST-001, INT-HIST-002, INT-HIST-005, INT-HIST-006 |
| integration-contracts.md | INT-HIST-001, INT-HIST-002, INT-HIST-003, INT-HIST-005, INT-HIST-006 |

---

## Por layer (hexagonal)

| Layer | Issues |
|---|---|
| **domain** | HIST-002, HIST-016 |
| **application** | HIST-003, HIST-004, HIST-006, HIST-008, HIST-022, HIST-023 |
| **api** | HIST-005, HIST-013 |
| **infrastructure** | HIST-001, HIST-007, HIST-009, HIST-010, HIST-011, HIST-012, HIST-014, HIST-015, HIST-017, HIST-018, HIST-019 |
| **test (functional)** | HIST-020, HIST-021 |
| **test (integration)** | INT-HIST-001, INT-HIST-002, INT-HIST-003, INT-HIST-004, INT-HIST-005, INT-HIST-006 |

---

## Grafo de dependências simplificado

```
HIST-001 (scaffold)
  └── HIST-002 (domain)
        ├── HIST-003 (inbound ports)
        │     └── HIST-005 (ingestion controllers) ─── P1 done
        └── HIST-004 (outbound ports)
              ├── HIST-006 (RecordAuditFactService) ─── P2
              │     └── HIST-022 (retry hardening) ─── P9
              ├── HIST-007 (HashChainPort impl) ─────── P2
              └── HIST-008 (QueryService + VerifyService) ── P2
                    └── HIST-013 (history controllers) ── P4

HIST-001
  └── HIST-011 (migrations) ─── P3
        ├── HIST-009 (JPA entities) ─── P3
        │     └── HIST-010 (JPA adapters) ─── P3
        └── INT-HIST-004 (migration test) ─── P3

HIST-012 (ACS Feign) ─── P4
  └── INT-HIST-002 (ACS integration test) ─── P4

HIST-014 (OAuth2) + HIST-015 (tenant) + HIST-016 (LGPD) ─── P5
  ├── INT-HIST-003 (multi-tenant test) ─── P5
  └── INT-HIST-005 (GWY routing test) ─── P5

HIST-017 + HIST-018 + HIST-019 ─── P7

HIST-020 + HIST-021 + INT-HIST-006 ─── P8

HIST-023 (vocab catalog) ─── P9
```

---

## Issues por produtor / integração

| Produtor / Sistema | Issues de integração |
|---|---|
| EVNT (evento.confirmado, descartado, feedback.registrado) | INT-HIST-001 |
| PAC (comando.sensibilidade.editada, recalibrado) | INT-HIST-001 |
| ACS (acesso.concedido, revogado + ActorDirectory) | INT-HIST-001, INT-HIST-002, HIST-012 |
| EDGE (dispositivo.pareado, despareado) | INT-HIST-001 |
| DASH / @neuroassist/hist FE | HIST-013, INT-HIST-006 |
| MIG (Flyway migrations) | HIST-011, INT-HIST-004 |
| GWY (routing) | INT-HIST-005 |

---

## Gaps e riscos para o Scaffold (avisos)

| # | Gap | Issue(s) afetadas | Risco |
|---|---|---|---|
| G1 | LGPD erasure sub-tenant (crypto-shredding vs imutabilidade) — decisão pendente | HIST-016 | Médio — sem D-NNN ainda; scaffold pode prosseguir; decidir antes de P8 |
| G2 | `factType` vocab co-owned com produtores — sem enforcement por produtor no MVP | INT-HIST-001, HIST-023 | Baixo — documentado como gap aberto |
| G3 | Feedback verdict owner (EVNT vs HIST proxy) — EVNT é owner; HIST somente espelha | HIST-008, INT-HIST-006 | Baixo — arquitetura definida; implementar conforme SPEC-03 |
| G4 | G5 `/historico/:eventId` detalhe route (FE) — feature flag `detailRoute=false` | N/A BE | Informativo — FE apenas |
| G5 | ACS JWK set URI deve estar acessível em todos os ambientes (test + prod) | HIST-014 | Médio — configurar `neuroassist.acs.jwk-set-uri` por profile |
