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
| P1 | Scaffold + contratos mínimos | done | M |
| P2 | Orquestração do domínio (core append + hash chain + leitura) | done | L |
| P3 | Persistência + migrations (JPA + Flyway) | em progresso | L |
| P4 | Integração externa (ACS Feign + history controllers + producer stubs) | pendente | L |
| P5 | Security + multi-tenancy + LGPD guard | pendente | L |
| P7 | Observability + health | pendente | M |
| P8 | BDD end-to-end | pendente | XL |
| P9 | Hardening (concurrency retry + vocab catalog) | pendente | M |

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

### Estado atual observado (pós-ciclo P1+P2 — 2026-08-20)

- **Score**: 0/100 FULL-VALIDACAO (Inicial) — provisório por bloqueador Spring Context.
- **Score intermediário**: 55/100 validação --light antes do bloqueador ser detectado.
- **Checklist**: 2/9 done (P1 e P2 concluídos); gaps: P3, P4, P5, P7, P8, P9.
- **Gates**: lint=PASS, arch-conformance=PASS (3/3 rules), test=FAIL, build=FAIL, e2e=FAIL.
- **Bloqueador crítico**: Spring Context initialization error em `CucumberIT` (`DefaultCacheAwareContextLoaderDelegate:145`).
  - Causa: `RecordAuditFactService` injeta `IdempotencyPort`, `AuditLedgerPort` e `StreamHeadPort` via `@RequiredArgsConstructor`, mas nenhuma dessas interfaces tem `@Component` implementando-as na camada `infrastructure/persistence/adapter/`.
  - Os adapters existentes (`IdempotencyEntryRepositoryAdapter`, `AuditRecordRepositoryAdapter`, `StreamHeadRepositoryAdapter`) implementam as portas de repositório de baixo nível (`*RepositoryPort`) — não as portas de domínio de alto nível.
  - Faltam: `IdempotencyAdapter` (→ `IdempotencyPort`), `AuditLedgerAdapter` (→ `AuditLedgerPort`), `StreamHeadAdapter` (→ `StreamHeadPort`).
- **Falha secundária**: `ChainVerificationServiceTest.verify_emptyStream_returnsEmpty` declara `when(hashChainPort.genesis()).thenReturn(GENESIS)` mas `ChainVerificationService` retorna `ChainStatus.empty` antes de chamar `genesis()` quando a lista está vazia → `UnnecessaryStubbingException` com `MockitoExtension` em modo `STRICT_STUBS`.

### Continuidade da última rodada

P1 (scaffold + contratos) e P2 (domínio + orchestração: append, hash chain, leitura) foram
concluídos com sucesso em 2026-08-20. A validação --light confirmou aderência arquitetural (3/3
ArchUnit rules). O bloqueador foi exposto pela FULL-VALIDACAO que tentou inicializar o
SpringBootTest completo via Cucumber. A próxima rodada é **P3 (Persistência + migrations)**
que resolve os adapters ausentes e desbloqueia o Spring Context.

### MVP scope

Dois surfaces REST:
1. **Ingestão** (`/audit-facts`): write-once, idempotente.
2. **Histórico** (`/history`, `/history/verify`): leitura paginada + verificação de cadeia.

Fora do MVP: erasure sub-tenant por paciente (crypto-shredding — decisão legal pendente),
broker/events (messaging disabled), UI própria (HIST é backend puro).

### Estado multi-empresa: EmpresaContext configurado?

- **Schema alvo**: `t_<empresa>` (schema-per-empresa, não `neuroassist_global`).
- **Mecanismo**: `TenantContextHolder` próprio do módulo (implementado em P5) — extrai `tenant` do JWT claim.
- **EmpresaContextHolder global**: NÃO usado diretamente; HIST implementa seu próprio tenant-pinning via `TenantContextFilter` (P5 → HIST-015).
- **Propagação JMS**: não aplicável (messaging disabled).
- **Checagem obrigatória**: `TenantContextHolder.require()` em todos os adapters de persistência — nenhum query sem tenant pinned.

### Diagnóstico inicial (gaps mapeados em 06-risks-gaps-assumptions.md)

| Gap | Impacto | Resolvido em |
|---|---|---|
| LGPD erasure sub-tenant vs imutabilidade | Médio — decisão legal pendente | Pré-P8: registrar D-NNN |
| factType vocabulary co-owned com produtores | Baixo — documentado | HIST-023 (P9) |
| G1: feedback write owner (EVNT, não HIST) | Baixo — HIST só espelha | HIST-008 (SPEC-03) |
| G3: HIST vs DASH overlap de leitura | Informativo | Sem ação BE |

---

## 1. Principais achados

### Pontos fortes

- Estrutura hexagonal completa e aderente às ArchUnit RULE-01..09 (scaffold gerado por ferramenta).
- ADRs fundadores (ADR-004, ADR-005, ADR-006) claramente documentados e refletidos no plano.
- Isolamento multi-tenant por schema-per-empresa bem delimitado (sem cross-tenant possível pelo design).
- Superficie REST mínima (ingestão + leitura) — escopo controlado, sem feature creep.
- Messaging disabled: elimina complexidade de broker para o MVP.

### Gaps identificados

| Gap | Severidade | Prioridade |
|---|---|---|
| 0 testes passando — nenhuma lógica real | Crítico | P1 imediato |
| SHA-256 + canonical v1 não implementado | Crítico (invariante ADR-006) | P2 |
| Entidades JPA sem mapeamento real (stubs) | Crítico | P3 |
| TenantContextHolder não implementado | Crítico | P5 |
| `uuid_generate_v7()` requer extensão PostgreSQL | Médio | P1/P3 |
| JCS canonical (RFC 8785) requer biblioteca | Médio | P2 |
| LGPD erasure decisão pendente (D-NNN) | Médio | Pré-P8 |

### Riscos

- **Concorrência no stream-tip**: race condition entre dois appends simultâneos no mesmo stream → lock pessimista em `StreamHeadAdapter` é obrigatório (P3/P9).
- **Hash chain invariante frágil**: qualquer bug em `canonical()` invalida toda a cadeia — validar com vetor de referência fixo antes de integrar com banco.
- **ACS ainda não implementado**: todos os testes que dependem de ACS usam WireMock — garantir que CI nunca chame ACS real.

### Aderência às regras (RULE-01..09)

- RULE-01/02: domínio sem Spring/JPA — garantido pelo scaffold; manter em P2.
- RULE-03: `@Transactional` somente em `RecordAuditFactService` — verificar em P2.
- RULE-04/05: api não depende de infra e vice-versa — garantido pelo ArchUnit.
- RULE-06: Feign somente em `infrastructure.clients` — verificar em P4.
- RULE-08: `spring.jpa.open-in-view=false` — confirmar em `application.yml` em P3.
- RULE-09: package base `com.neuroassist.hist` — confirmado pelo scaffold.

---

## 2. Estratégia geral

### Objetivo macro

Entregar um `hist-service` funcional, testado e production-ready que:
1. Aceite audit facts de 4 produtores com validação estrita (factType, payload LGPD, clock).
2. Persista de forma imutável com hash chain verificável.
3. Sirva histórico paginado e verificação de cadeia para DASH e FE.
4. Isole rigorosamente os dados por tenant schema-per-empresa.

### Ordem das prioridades

| P | Título | Motivo |
|---|---|---|
| P1 | Scaffold + contratos mínimos | Base para todos os outros; sem compile, nada anda |
| P2 | Domínio + orchestração (append + hash + query) | Golden path; bloqueia P3..P8 |
| P3 | Persistência + migrations | JPA depende de domain P2; migrations independentes mas testadas em P3 |
| P4 | Integração externa + history REST | ACS Feign depende de P3 (testes); history controllers completam a superfície |
| P5 | Security + multi-tenancy + LGPD | Transversal; sem security o serviço não é produtizável |
| P7 | Observability | Métricas e logs são adicionados aos services já implementados (P2..P5) |
| P8 | BDD e2e | Cucumber roda contra stack completa (P1..P7 prontos) |
| P9 | Hardening | Retry e catalog são refinamentos; não bloqueiam MVP |

---

## 3. Plano por prioridade

### Prioridade P1 -- Scaffold + contratos mínimos

Status: done

#### Objetivo / Resultado esperado

Estrutura compilável do `hist-service` com o módulo Gradle configurado, pacotes hexagonais
criados, portas inbound/outbound declaradas como interfaces, e controllers de ingestão
funcional (mapeamento HTTP ↔ use-case, error handler RFC 7807). O serviço sobe em
`localhost:8089` com `/actuator/health → 200`.

#### Issues vinculadas

- HIST-001 (scaffold)
- HIST-002 (domain aggregate + value objects)
- HIST-003 (inbound ports)
- HIST-004 (outbound ports)
- HIST-005 (ingestion REST controllers)

#### Specs cobertas

- SPEC-01 (API Surface)
- SPEC-02 (parcial — validações de domain sem implementação de chaining)

#### Sequencia de execucao

1. HIST-001: módulo Gradle + `HistServiceApplication` + `application.yml`.
2. HIST-002: `AuditRecord`, `SubjectRef`, `ActorRef`, `HashChain`, `FactType`, `SubjectType`, exceções.
3. HIST-003 → HIST-004: interfaces de port (sem implementação).
4. HIST-005: controllers de ingestão + `HistProblemDetailHandler`.

#### Criterios de aceite

- [ ] `./gradlew :hist-service:compileKotlin` exit 0.
- [ ] `GET /actuator/health` → 200.
- [ ] `POST /api/v1/hist/audit-facts` com body válido → 201 (stub de service retorna record fixo).
- [ ] `POST` com factType desconhecido → 422.
- [ ] `GET /api/v1/hist/fact-types` → 200 com lista v1.

#### Multi-empresa

EmpresaContext: N/A em P1 — portas e controllers são criados como stubs sem acesso a dados.
`TenantContextHolder` será implementado em P5; em P1 usar header `X-Tenant-Id` fixo nos testes.

#### Riscos

- `uuid_generate_v7()` disponível somente com extensão `uuid-ossp` no PostgreSQL — garantir no V0001.
- Kotlin data class + JPA: usar `open class` ou `@Entity` com campos `var` (Hibernate requer).

#### Instrucao para implementador

Começar por HIST-001 (Gradle multi-module setup). Verificar que `hist-service` aparece em
`settings.gradle.kts` do root. Usar `EntityManager.persist()` (nunca `save()`) desde o
início como convenção arquitetural. Portas em HIST-003 e HIST-004 são `interface` puras —
não adicionar `@Component` nelas. Controllers em HIST-005 injetam `RecordAuditFactUseCase`
(interface) — usar stub/mock via `@MockBean` nos testes de unidade.

---

### Prioridade P2 -- Orquestração do domínio (core append + hash chain + leitura)

Status: done

#### Objetivo / Resultado esperado

`RecordAuditFactService.append` funcional com idempotência, chaining e transação atômica.
`HashChainPort` implementado com SHA-256 + canonical v1. `HistoryQueryService` e
`ChainVerificationService` prontos. Testáveis em unitário sem banco.

#### Issues vinculadas

- HIST-006 (RecordAuditFactService)
- HIST-007 (HashChainPort impl + FactTypeCatalogService)
- HIST-008 (HistoryQueryService + ChainVerificationService)

#### Specs cobertas

- SPEC-02 (golden path de append + idempotência + chaining)
- SPEC-03 (history query + verify)
- SPEC-05 (idempotência e concorrência)

#### Sequencia de execucao

1. HIST-007: `Sha256HashChainAdapter` + `FactTypeCatalogService` (sem banco).
2. HIST-006: `RecordAuditFactService` (depende de ports P1 e hash impl).
3. HIST-008: `HistoryQueryService` + `ChainVerificationService`.

#### Criterios de aceite

- [ ] Teste unitário de `RecordAuditFactService` com ports mockados: append novo → 201; replay → 200.
- [ ] Teste unitário de `Sha256HashChainAdapter`: vetor de referência fixo confirma hash.
- [ ] Teste unitário de `ChainVerificationService`: N records intactos → intact; 1 adulterado → broken.

#### Riscos

- Canonical v1 (RFC 8785 JCS) requer biblioteca `org.erdtman.jcs:java-json-canonicalization` ou
  implementação manual. Verificar disponibilidade no Gradle; se ausente, usar Jackson com `SortedMap`.
- `@Transactional` em `RecordAuditFactService`: anotar somente em `append` (não em métodos de leitura).

#### Instrucao para implementador

Implementar HIST-007 primeiro — `Sha256HashChainAdapter` é testável em isolamento total.
Depois injetar em HIST-006. O fluxo de 8 passos está documentado em SPEC-02 — seguir a
ordem exata (idempotência antes do lock, lock antes do chain, tudo em 1 transação).
`RecordedAt` deve ser `now()` do serviço, nunca do payload do produtor.

---

### Prioridade P3 -- Persistência + migrations (JPA + Flyway)

Status: em progresso
Issues cobertas: HIST-009, HIST-010, HIST-011, INT-HIST-004

#### Objetivo / Resultado esperado

Desbloquear o Spring Context (CucumberIT), entregar os três adapters de domínio ausentes
(`IdempotencyAdapter`, `AuditLedgerAdapter`, `StreamHeadAdapter`) como `@Component` totalmente
funcionais, mapear as 6 entidades JPA, e aplicar as migrations Flyway V0001..V0005 via
Testcontainers. Ao final desta prioridade: `./gradlew :services:hist-service:test` passa com
gates test=PASS e build=PASS.

#### Correções mandatórias ANTES de qualquer implementação nova

**Correção 0 — Remover stub desnecessário em `ChainVerificationServiceTest`:**

Arquivo: `backend/services/hist-service/src/test/java/com/neuroassist/hist/application/service/ChainVerificationServiceTest.java`

No método `verify_emptyStream_returnsEmpty`, remover a linha:
```
when(hashChainPort.genesis()).thenReturn(GENESIS);
```
`ChainVerificationService.verifyStream()` retorna `ChainStatus.empty` antes de chamar
`genesis()` quando a lista está vazia — o stub nunca é consumido → `UnnecessaryStubbingException`
com `@ExtendWith(MockitoExtension.class)` (strict stubs).

**Correção 1 — Criar `IdempotencyAdapter` implementando `IdempotencyPort`:**

Arquivo novo: `backend/services/hist-service/src/main/java/com/neuroassist/hist/infrastructure/persistence/adapter/IdempotencyAdapter.java`

```java
@Component
@RequiredArgsConstructor
public class IdempotencyAdapter implements IdempotencyPort {
    private final IdempotencyEntryJpaRepository repository;
    // mapear IdempotencyJpaEntity ↔ IdempotencyEntry; usar EntityManager.persist()
}
```

Métodos a implementar:
- `findByFactId(UUID factId, String tenantId)` → query JPA por `factId` e `tenantId` → mapear para `IdempotencyEntry`
- `record(IdempotencyEntry entry, String tenantId)` → mapear `IdempotencyEntry` → `IdempotencyEntryJpaEntity` → `EntityManager.persist()`

**Correção 2 — Criar `AuditLedgerAdapter` implementando `AuditLedgerPort`:**

Arquivo novo: `backend/services/hist-service/src/main/java/com/neuroassist/hist/infrastructure/persistence/adapter/AuditLedgerAdapter.java`

```java
@Component
@RequiredArgsConstructor
public class AuditLedgerAdapter implements AuditLedgerPort {
    private final EntityManager entityManager;
    // NUNCA usar repository.save() — obrigatório EntityManager.persist()
}
```

Método a implementar:
- `insert(AuditRecord record, String tenantId)` → mapear `AuditRecord` → `AuditRecordJpaEntity` → `entityManager.persist()` → retornar record original. `@Column(updatable=false)` em todos os campos de negócio da entidade garante imutabilidade (ADR-004).

**Correção 3 — Criar `StreamHeadAdapter` implementando `StreamHeadPort`:**

Arquivo novo: `backend/services/hist-service/src/main/java/com/neuroassist/hist/infrastructure/persistence/adapter/StreamHeadAdapter.java`

```java
@Component
@RequiredArgsConstructor
public class StreamHeadAdapter implements StreamHeadPort {
    private final StreamHeadJpaRepository repository;
}
```

Métodos a implementar:
- `lockAndFetchTip(String streamKey, String tenantId)` → query com `@Lock(PESSIMISTIC_WRITE)` por `streamKey` e `tenantId` → mapear para `StreamHead`
- `upsert(StreamHead head, String tenantId)` → se existe: atualizar campos de sequência; se não existe: `entityManager.persist()` novo `StreamHeadJpaEntity`

#### Issues vinculadas

- HIST-009 (JPA entities — mapeamento completo das 6 entidades)
- HIST-010 (JPA adapters — inclui os 3 adapters de domínio acima + mappers bidirecionais)
- HIST-011 (Flyway migration scripts V0001..V0005)
- INT-HIST-004 (migration integration test em Testcontainers)

#### Specs cobertas

- SPEC-04 (Data Model & Migrations)
- SPEC-05 (concorrência — lock pessimista em `StreamHeadAdapter.lockAndFetchTip()`)
- ADR-004 (sem UPDATE/DELETE nas entities de ledger — `@Column(updatable=false)`)

#### Sequencia de execucao

1. Aplicar Correção 0 (test fix — sem risco de regressão).
2. Aplicar Correções 1, 2, 3 (criar os 3 adapters de domínio com JPA stubs funcionais — suficientes para resolver o Spring Context).
3. HIST-011: criar scripts Flyway V0001..V0005 em `backend/services/neuroassist-ai-migrations/src/main/resources/db/migration/empresa/`.
4. INT-HIST-004: `FlywayMigrationTest` em Testcontainers (valida V0001..V0005 no schema de empresa).
5. HIST-009: mapeamento completo das 6 entidades JPA (`AuditRecordJpaEntity`, `IdempotencyEntryJpaEntity`, `StreamHeadJpaEntity`, `SubjectRefJpaEntity`, `ActorRefJpaEntity`, `HashChainJpaEntity`).
6. HIST-010: completar mappers bidirecionais em todos os 3 adapters de domínio; substituir stubs JPA pelos mapeamentos reais.
7. Rodar `./gradlew :services:hist-service:test` — verificar test=PASS.

#### Alvos de alteracao (caminhos completos)

**Arquivos novos:**
- `backend/services/hist-service/src/main/java/com/neuroassist/hist/infrastructure/persistence/adapter/IdempotencyAdapter.java`
- `backend/services/hist-service/src/main/java/com/neuroassist/hist/infrastructure/persistence/adapter/AuditLedgerAdapter.java`
- `backend/services/hist-service/src/main/java/com/neuroassist/hist/infrastructure/persistence/adapter/StreamHeadAdapter.java`
- `backend/services/neuroassist-ai-migrations/src/main/resources/db/migration/empresa/V0001__hist_audit_record.sql`
- `backend/services/neuroassist-ai-migrations/src/main/resources/db/migration/empresa/V0002__hist_idempotency.sql`
- `backend/services/neuroassist-ai-migrations/src/main/resources/db/migration/empresa/V0003__hist_stream_head.sql`
- `backend/services/neuroassist-ai-migrations/src/main/resources/db/migration/empresa/V0004__hist_indexes.sql`
- `backend/services/neuroassist-ai-migrations/src/main/resources/db/migration/empresa/V0005__hist_role_grants.sql`

**Arquivos modificados:**
- `backend/services/hist-service/src/test/java/com/neuroassist/hist/application/service/ChainVerificationServiceTest.java` (Correção 0)
- `backend/services/hist-service/src/main/java/com/neuroassist/hist/infrastructure/persistence/entity/AuditRecordJpaEntity.java` (mapeamento completo)
- `backend/services/hist-service/src/main/java/com/neuroassist/hist/infrastructure/persistence/entity/IdempotencyEntryJpaEntity.java` (mapeamento completo)
- `backend/services/hist-service/src/main/java/com/neuroassist/hist/infrastructure/persistence/entity/StreamHeadJpaEntity.java` (mapeamento completo)
- `backend/services/hist-service/src/main/java/com/neuroassist/hist/infrastructure/persistence/entity/SubjectRefJpaEntity.java` (mapeamento completo)
- `backend/services/hist-service/src/main/java/com/neuroassist/hist/infrastructure/persistence/entity/ActorRefJpaEntity.java` (mapeamento completo)
- `backend/services/hist-service/src/main/java/com/neuroassist/hist/infrastructure/persistence/entity/HashChainJpaEntity.java` (mapeamento completo)

**Arquivos NÃO tocar:**
- `IdempotencyEntryRepositoryAdapter.java`, `AuditRecordRepositoryAdapter.java`, `StreamHeadRepositoryAdapter.java` — esses adapters de baixo nível permanecem como stubs P3 e serão completados junto com o mapeamento das entities (passo 6 acima).

#### Contrato operacional

Deve existir:
- `IdempotencyAdapter` registrado como `@Component` implementando `IdempotencyPort`
- `AuditLedgerAdapter` registrado como `@Component` implementando `AuditLedgerPort`
- `StreamHeadAdapter` registrado como `@Component` implementando `StreamHeadPort`
- `./gradlew :services:hist-service:compileJava` → exit 0
- `ApplicationContextLoadsIT` → PASS (Spring Context sobe sem erro)
- `FlywayMigrationTest` → PASS em Testcontainers

Não deve existir:
- Nenhuma implementação de `@Transactional` fora de `application.service.*` (RULE-03)
- `EntityManager.merge()` ou `repository.save()` em `AuditLedgerAdapter` — somente `persist()`
- Nenhum `UPDATE` ou `DELETE` exposto em `AuditLedgerAdapter` (ADR-004)

Condição de bloqueio: se após implementar os 3 adapters o `ApplicationContextLoadsIT` ainda falhar, parar e investigar antes de prosseguir para JPA mapping completo.

#### Politica de testes

- **Unit (obrigatório)**: `RecordAuditFactServiceTest` com `@MockBean` dos 3 novos adapters — verificar que o contexto Spring sobe limpo no perfil de teste unitário.
- **Integration (obrigatório)**: `FlywayMigrationTest` via Testcontainers — V0001..V0005 aplicados sem erro; schema `t_empresa` criado com as 3 tabelas.
- **Integration (obrigatório)**: `ApplicationContextLoadsIT` deve passar após Correções 1-3.

#### Multi-empresa

Schema alvo: `t_<empresa>`. Tabelas `audit_record`, `audit_idempotency`, `stream_head`
criadas por migration em cada schema de empresa via placeholder `${empresa_schema}`. Em Testcontainers
usar `application-test.yml` com `spring.flyway.placeholders.hist_service_role=hist_test_role`.
Confirmar que `spring.jpa.open-in-view=false` está em `application.yml` (RULE-08).
`TenantContextHolder` ainda não existe em P3 (implementado em P5) — os adapters devem receber
`tenantId` como parâmetro (já na assinatura das portas) e ignorar o holder por ora.

#### Migracoes Flyway necessarias

Sim. Scripts em `backend/services/neuroassist-ai-migrations/src/main/resources/db/migration/empresa/`:
- `V0001__hist_audit_record.sql` — tabela `audit_record` com `uuid_generate_v7()` (extensão `uuid-ossp` obrigatória)
- `V0002__hist_idempotency.sql` — tabela `audit_idempotency` com unique `(fact_id, tenant_id)`
- `V0003__hist_stream_head.sql` — tabela `stream_head` com índice por `(stream_key, tenant_id)`
- `V0004__hist_indexes.sql` — índices compostos para queries de leitura
- `V0005__hist_role_grants.sql` — grants para `${hist_service_role}` (SELECT, INSERT em audit_record; SELECT, INSERT, UPDATE em stream_head)

#### Criterios de aceite

- [ ] Correção 0 aplicada: `ChainVerificationServiceTest` passa com 5/5 testes sem `UnnecessaryStubbingException`.
- [ ] `ApplicationContextLoadsIT` → PASS (Spring Context inicializa sem `NoSuchBeanDefinitionException`).
- [ ] `./gradlew :services:hist-service:test -Dtest=FlywayMigrationTest` passa em Testcontainers.
- [ ] `AuditLedgerAdapter.insert()` usa `EntityManager.persist()` — confirmado por inspeção + teste que verifica que `UPDATE audit_record` pelo role de teste é negado pelo banco.
- [ ] `StreamHeadAdapter.lockAndFetchTip()` usa `@Lock(PESSIMISTIC_WRITE)` — confirmado por inspeção.
- [ ] `./gradlew :services:hist-service:architectureCheck` → PASS (RULE-01..09 mantidos).
- [ ] `./gradlew :services:hist-service:test` → gate test=PASS (todas as suites unitárias).

#### Riscos

- `uuid_generate_v7()` disponível somente com extensão `uuid-ossp` no PostgreSQL — garantir `CREATE EXTENSION IF NOT EXISTS "uuid-ossp"` em V0001.
- `HibernateMultiTenancyConfig` (já existente) requer que `TenantIdentifierResolver` retorne tenant válido; em testes de P3, resolver para `empresa_test` fixo via `application-test.yml`.
- `IdempotencyEntryRepositoryAdapter` e `AuditRecordRepositoryAdapter` existentes têm `throw new UnsupportedOperationException()` — em P3 esses low-level adapters são completados; os novos adapters de domínio delegam a eles (composição, não duplicação).

#### Instrucao para implementador

Iniciar pelas Correções 0-3 obrigatórias antes de qualquer outra tarefa de P3. Após criar os
3 adapters de domínio, executar `ApplicationContextLoadsIT` imediatamente para confirmar que o
Spring Context sobe — se falhar, depurar antes de prosseguir para JPA mapping.

Para `AuditLedgerAdapter`: injetar `EntityManager` diretamente (não via `JpaRepository`) —
`EntityManager.persist()` é a única operação permitida; nunca usar `save()` ou `merge()` em
registros de auditoria (ADR-004). Para `StreamHeadAdapter`: a query `lockAndFetchTip` deve usar
`@Lock(LockModeType.PESSIMISTIC_WRITE)` para serializar appends concorrentes no mesmo stream.

O mapper bidirecional deve preservar todos os 15 campos de `AuditRecord` sem conversão
silenciosa de tipos. `SubjectRef`, `ActorRef` e `HashChain` podem ser embedded (`@Embeddable`)
na entidade `AuditRecordJpaEntity` ou mapeados como entidades separadas — escolher embedded para
simplificar o mapper e evitar joins desnecessários.

---

### Prioridade P4 -- Integração externa + history REST controllers

Status: pendente

#### Objetivo / Resultado esperado

`AcsActorDirectoryClient` (Feign) com timeout 2s e CB Resilience4j, `ActorDirectoryAdapter`
fail-closed, controllers de histórico completos, e testes de contrato com produtores.

#### Issues vinculadas

- HIST-012 (ACS Feign + ActorDirectoryAdapter)
- HIST-013 (GET /history, GET /history/verify controllers)
- INT-HIST-001 (inbound contract tests — produtores)
- INT-HIST-002 (ACS integration test)

#### Specs cobertas

- SPEC-03 (history surface — filtros, paginação, scope de cuidador)
- SPEC-10 (integração ACS — timeout, CB, fail-closed)
- integration-contracts.md §1, §2

#### Sequencia de execucao

1. HIST-012: ACS client + adapter + WireMock setup.
2. INT-HIST-002: testes de integração ACS (timeout, CB, success).
3. HIST-013: history controllers.
4. INT-HIST-001: producer contract tests.

#### Criterios de aceite

- [ ] `GET /history` com `hist:read` + ACS OK → resultados filtrados por scope.
- [ ] `GET /history` com ACS indisponível → 503.
- [ ] `GET /history` com `hist:read:all` → sem chamada ACS.
- [ ] Testes de produtor para EVNT, PAC, ACS, EDGE passam.

#### Riscos

- Timeout Resilience4j + Feign: configurar `feign.client.config.acs-directory.connectTimeout=2000`
  e `readTimeout=2000` em sync com o CB threshold.
- ACS não está implementado ainda: usar WireMock em todos os testes (nunca ACS real em CI).

#### Instrucao para implementador

`ActorDirectoryAdapter` **nunca** é chamado em `POST /audit-facts` — apenas em leituras
com `hist:read`. Adicionar assert de "não chamado" nos testes de ingestão. O fallback do
CB deve lançar `ActorDirectoryUnavailableException`, nunca retornar lista vazia (que
widening-would o scope).

---

### Prioridade P5 -- Security + multi-tenancy + LGPD guard

Status: pendente

#### Objetivo / Resultado esperado

OAuth2 resource server validando JWTs ACS localmente, schema-pinning por JWT `tenant`
claim, rejeição de payload biométrico, e isolamento multi-tenant verificado em testes.

#### Issues vinculadas

- HIST-014 (OAuth2 resource server + scopes)
- HIST-015 (TenantContextHolder + schema-pinning)
- HIST-016 (LGPD payload guard)
- INT-HIST-003 (multi-tenant isolation test)
- INT-HIST-005 (GWY routing contract test)

#### Specs cobertas

- SPEC-06 (Security — JWT, scopes, tenant isolation, LGPD)
- api-contract.md §1.1, §6

#### Sequencia de execucao

1. HIST-016: `LgpdPayloadGuard` (domínio puro — sem Spring; pode ser feito em paralelo com P3).
2. HIST-014: `SecurityConfig` OAuth2 resource server.
3. HIST-015: `TenantContextFilter` + `TenantContextHolder`.
4. INT-HIST-003: multi-tenant isolation test.
5. INT-HIST-005: GWY routing contract test.

#### Criterios de aceite

- [ ] Sem token → 401 em todos os endpoints.
- [ ] `hist:read` em POST → 403.
- [ ] Body `tenantId != JWT.tenant` → 403 `/errors/tenant-mismatch`.
- [ ] Record de `t_empresa_a` invisível para JWT `tenant=empresa_b`.
- [ ] Payload com chave `"image"` → 422 `/errors/forbidden-payload`.

#### Multi-empresa

**EmpresaContextHolder.require(): NÃO usado.** HIST implementa `TenantContextHolder` próprio
(HIST-015) que extrai `tenant` do JWT claim e faz pin do schema `t_<empresa>` na conexão.
`TenantContextHolder.require()` deve ser chamado em todos os adapters de persistência
(HIST-010 em P3 deve ser revisado em P5 para garantir que o holder está ativo).
Leak de contexto entre requests: `TenantContextHolder.clear()` obrigatório no bloco `finally`
do `TenantContextFilter`. Propagação JMS: não aplicável (messaging disabled).

#### Riscos

- `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` deve apontar para ACS em todos os
  profiles. Em CI (sem ACS real): usar WireMock JWK endpoint + JWT assinado com chave de teste.
- LGPD open: erasure sub-tenant ainda não tem D-NNN. Registrar decisão antes de P8 (não bloqueia P5).

#### Instrucao para implementador

`TenantContextFilter` deve rodar **após** `SecurityFilter` (garantir ordem via `@Order` ou
`FilterRegistrationBean`). `TenantContextHolder.clear()` no bloco `finally` é obrigatório
para evitar leak de contexto entre requests no mesmo thread (thread pool). HIST-016 pode
ser implementado como `@Component` Spring mas testável sem context.

---

### Prioridade P7 -- Observability + health

Status: pendente

#### Objetivo / Resultado esperado

6 meters Micrometer exportados via `/actuator/prometheus`, JSON structured logging com
`correlationId` + `tenant` em todas as linhas, `X-Correlation-Id` propagado para ACS,
e `/actuator/health/readiness` DOWN quando DB indisponível (UP quando só ACS inacessível).

#### Issues vinculadas

- HIST-017 (Micrometer metrics)
- HIST-018 (structured logging + X-Correlation-Id)
- HIST-019 (actuator health + readiness)

#### Specs cobertas

- SPEC-07 (Observability & Health)

#### Sequencia de execucao

1. HIST-019: configuração do actuator (simples, sem dependência de P2..P5).
2. HIST-017: `HistMetrics` + injeção em services.
3. HIST-018: logback JSON + `CorrelationIdFilter` + `FeignCorrelationInterceptor`.

#### Criterios de aceite

- [ ] `GET /actuator/prometheus` inclui `hist_audit_fact_recorded_total`, `hist_duplicate_fact_ignored_total`, `hist_chain_verification_total`, `hist_ingestion_latency_seconds`, `hist_acs_scope_resolution_total`.
- [ ] Log de append em JSON contém `correlationId`, `tenant`, `factType`, `streamKey`.
- [ ] Log não contém `payload` em nenhuma condição.
- [ ] `GET /actuator/health/readiness` → DOWN quando Testcontainers PostgreSQL desligado.

#### Riscos

- `logstash-logback-encoder`: adicionar `net.logstash.logback:logstash-logback-encoder:8.0` em `build.gradle.kts` se não presente no BOM.

#### Instrucao para implementador

P7 é complemento: injetar `HistMetrics` nos services implementados em P2 e P4.
Não refatorar lógica de negócio — apenas adicionar `.increment()` / `.record()` nos pontos
documentados em HIST-017. `CorrelationIdFilter` em HIST-018 deve ter precedência alta
(`@Order(Ordered.HIGHEST_PRECEDENCE + 10)`) para rodar antes de `TenantContextFilter`.

---

### Prioridade P8 -- BDD end-to-end

Status: pendente

#### Objetivo / Resultado esperado

Todos os cenários Cucumber das 5 features passam contra a stack completa (Testcontainers
PostgreSQL + WireMock ACS). Inclui os 5 gaps fechados na fase M2.5 (specs-validate).

#### Issues vinculadas

- HIST-020 (BDD steps: audit-fact-ingestion + idempotency-and-concurrency)
- HIST-021 (BDD steps: hash-chain + history-query + security-and-auth)
- INT-HIST-006 (producer e2e: ingest → query → verify)

#### Specs cobertas

- SPEC-08 (BDD Test Scenarios)
- SPEC-VAL-bdd-coverage-report (5 gaps fechados na M2.5: occurredAt future, correctsFactId, GET by id cross-tenant, 401/403, X-Correlation-Id)

#### Sequencia de execucao

1. HIST-020: steps de ingestão + idempotência (features mais simples).
2. HIST-021: steps de chain + history + security (dependem de stack completa).
3. INT-HIST-006: e2e completo (usa infra dos steps anteriores).

#### Criterios de aceite

- [ ] `./gradlew :hist-service:test --tests "*Cucumber*"` → 0 falhas, 0 pendentes.
- [ ] 5 features com todos os cenários cobertos.
- [ ] Cenário de adulteração de hash em chain-verification usa SQL direto no Testcontainers.
- [ ] INT-HIST-006 completa em < 30s.

#### Riscos

- `security-and-auth.feature` requer JWT mock para múltiplos scopes (hist:write, hist:read, hist:read:all, sem scope). Usar builder de JWT de teste com HMAC ou RSA de teste.
- LGPD erasure D-NNN: se não decidido antes de P8, o cenário de erasure em `security-and-auth.feature` pode ser marcado como `@Pending` temporariamente.

#### Instrucao para implementador

`CucumberSpringConfiguration` usa `@SpringBootTest(webEnvironment=RANDOM_PORT)`. Compartilhar
`WireMockServer` entre steps via `@Autowired` singleton. Para o cenário de adulteração de
hash: usar `JdbcTemplate` direto no Testcontainers com o papel de teste (não o role imutável).
Zero `@Pending` no commit final.

---

### Prioridade P9 -- Hardening

Status: pendente

#### Objetivo / Resultado esperado

Retry configurável em contention de stream-tip (max 3, backoff 50ms) e vocabulary catalog
configurável via `application.yml` sem recompilação.

#### Issues vinculadas

- HIST-022 (concurrency retry hardening)
- HIST-023 (FactType vocabulary catalog configurável)

#### Specs cobertas

- SPEC-05 (idempotency & concurrency — retry em race conditions)
- SPEC-02 (factType controlado e extensível)

#### Sequencia de execucao

1. HIST-023: vocab config (simples; pode ser feito após P1 se necessário para P2).
2. HIST-022: retry wrapper em RecordAuditFactService (complemento de P2).

#### Criterios de aceite

- [ ] 10 threads simultâneas no mesmo stream → `sequenceNo` consecutivos sem gap.
- [ ] `GET /api/v1/hist/fact-types` retorna 10 tipos v1 seed.
- [ ] Adicionar tipo em `application.yml` sem recompilar → aceito na ingestão.

#### Riscos

- HIST-022 altera `RecordAuditFactService` (P2) — verificar que não quebra testes existentes de P8.

#### Instrucao para implementador

HIST-023 pode ser antecipado para logo após P1 (o vocab config é leve e desbloqueia validações
de domínio mais cedo). HIST-022 deve ser implementado com `RetryTemplate` Spring ou loop manual
com limite explícito — documentar `maxAttempts=3` em KDoc.

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

- **ArchUnit RULE-01**: `domain` não depende de `application`, `infrastructure`, `api`.
- **ArchUnit RULE-02**: `application` não depende de `infrastructure` ou `api`.
- **ArchUnit RULE-03**: `@Transactional` somente em `application.service.*`.
- **ArchUnit RULE-04**: nenhum acesso a `EntityManager` em `application` ou `domain`.
- **ArchUnit RULE-05**: sem `UPDATE` ou `DELETE` em `AuditRecordRepository`.
- **ArchUnit RULE-06**: `AuditRecord` é imutável (campos `val`, sem setter).
- **ADR-004**: zero endpoint de mutação exposto (nenhum PUT, PATCH, DELETE em `/audit-facts`).
- **ADR-005**: ingestão não bloqueia em ACS.
- **ADR-006**: hash chain computado na mesma transação do append.
- **LGPD**: `payload` nunca loga conteúdo bruto; guard rejeita chaves biométricas.

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
