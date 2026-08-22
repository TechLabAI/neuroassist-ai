# NeuroAssist AI -- A NeuroAssist AI propõe uma plataforma de tecnologia assistiva que transforma pequenos movimentos voluntários residuais — de pessoas com doenças neurodegenerativas como ALS/MND — em pedidos de ajuda estruturados, capturados por uma webcam comum e processados por visão computacional.

> Monorepo: `/backend` (Java 21 microservices) + `/frontend` (Angular + Nx) + `/docs/architecture/` (specification).
>
> **Stack:** Java 21 . Spring Boot 3.5.0 . Gradle 8.x (Kotlin DSL) . ActiveMQ Artemis (JMS) . PostgreSQL 15+ . Angular 18+ . Nx 19+

---

## Monorepo layout

```
/backend/                              # Java 21 microservices
  build.gradle.kts (root)
  settings.gradle.kts
  services/
    gest-service/      # port 8084 — Detecção de Gesto (Edge)
    evnt-service/      # port 8085 — Eventos e Alarmes
    pac-service/       # port 8082 — Pacientes e Catálogo de Comandos
    acs-service/       # port 8088 — Cuidadores e Acesso
    edge-service/      # port 8087 — Integração de Dispositivo Edge
    hist-service/      # port 8089 — Histórico e Auditoria
    dash-service/      # port 8090 — Dashboard e Notificações
    neuroassist-ai-api-gateway/        # port 8080 — Spring Cloud Gateway
    neuroassist-ai-service-registry/   # port 8761 — Eureka
    neuroassist-ai-migrations/         # Centralized Flyway (all schemas)

/frontend/                             # Nx workspace, npmScope: @neuroassist
  apps/
    neuroassist-ai-web/                # single Angular app
    gest-e2e/ evnt-e2e/ edge-e2e/ pac-e2e/ acs-e2e/
  libs/
    gest/ evnt/ edge/ pac/ acs/ hist/ dash/
    shared/{ui-tokens,ui-feedback,ui-components,error-model,access-http-base,
            access-decision,auth-session,contracts,empresa-context,observability}/

/docs/architecture/
  backend/{adr,services,standards,issues,plans}/
  frontend/{adr,libs,standards,issues,plans}/
  shared/{modelo-dados,contratos,rbac}/

/docs/
  architecture/...
  paper/
    PAPER-PLAN.md        # Plano completo do paper acadêmico

```

---

## Business modules (= unit of work of the AI pipeline)

| Code | Name | Microservice | Nx Lib | Port | Status |
|---|---|---|---|---|---|
| GEST | Detecção de Gesto (Edge) | `gest-service` | `libs/gest` | 8084 | ✅ scaffolded + P1-P9 done |
| EVNT | Eventos e Alarmes | `evnt-service` | `libs/evnt` | 8085 | ✅ scaffolded + P1-P9 done |
| PAC | Pacientes e Catálogo de Comandos | `pac-service` | `libs/pac` | 8082 | ✅ scaffolded + P1-P9 done (160/160 BDD) |
| ACS | Cuidadores e Acesso | `acs-service` | `libs/acs` | 8088 | ✅ scaffolded + P1-P7 done (26/26 issues, 85/100 Maduro) — PRODUCTION-READY |
| EDGE | Integração de Dispositivo Edge | `edge-service` | `libs/edge` | 8087 | ✅ scaffolded + P1-P10 done |
| HIST | Histórico e Auditoria | `hist-service` | `libs/hist` | 8089 | ✅ scaffolded + P1-P10 done (92/92 tests, 100/100) |
| DASH | Dashboard e Notificações | `dash-service` | `libs/dash` | 8090 | ✅ scaffolded + P1-P8 done (100/100) |

Issue prefix: `<MODULE>-NNN` (e.g.: `GEST-001`, `EVNT-001`, `PAC-001`). Issues live in `docs/architecture/{backend,frontend}/issues/`. GitHub repository: `TechLabAI/neuroassist-ai`.

---

## Hexagonal -- mandatory package structure (each BE microservice)

Base package: `com.neuroassist.<module>` (e.g.: `com.neuroassist.gest`).

```
com.neuroassist.<module>/
├── api/
│   ├── controller/          # @RestController
│   ├── dto/                 # Java records (request/response)
│   └── exception/           # @RestControllerAdvice + ProblemDetail (RFC 7807)
├── application/
│   ├── service/             # @Service -- use cases (@Transactional ONLY here)
│   └── port/
│       ├── in/              # Inbound (use cases)
│       └── out/             # Outbound (repos, clients)
├── domain/
│   ├── model/               # POJOs/records -- ZERO Spring/JPA
│   └── service/             # Domain services -- ZERO Spring
└── infrastructure/
    ├── clients/             # @FeignClient
    ├── config/              # @Configuration (includes DomainServiceConfig)
    ├── messaging/{producer,consumer}/
    ├── persistence/{entity,repository,adapter}/
    └── security/            # Spring Security filters, MDC
```

---

## Non-negotiable rules (ArchUnit enforced in `:<service>:architectureCheck`)

```
RULE-01  domain.* does not import org.springframework
RULE-02  domain.* does not import jakarta.persistence
RULE-03  @Transactional only in application.service
RULE-04  api.* does not depend on infrastructure.*
RULE-05  infrastructure.* does not depend on api.*
RULE-06  @FeignClient only in infrastructure.clients
RULE-07  TenantContextHolder.require() is the FIRST call in every public method that touches empresa data
RULE-08  spring.jpa.open-in-view=false mandatory
RULE-09  mandatory base package: com.neuroassist.<module>
```

If `./gradlew :services:<service>:architectureCheck` fails → **do not commit**, fix first.

---

## Migrations (CENTRALIZED in `neuroassist-ai-migrations`)

- All migrations for ALL schemas live in `backend/services/neuroassist-ai-migrations/`.
- Naming: `V<N>__<module>_<description>.sql`. Schema-aware: each V indicates which schema it applies to (in path: `db/migration/empresa/V*.sql` or `db/migration/global/V*.sql`).
- Individual microservices use `spring.jpa.hibernate.ddl-auto=validate` (schema prepared by `neuroassist-ai-migrations`).
- **Local dev:** run `./gradlew :services:neuroassist-ai-migrations:bootRun` FIRST; then the other services.

---

## Parallel development (pole-position)

When running multiple modules in parallel across `git worktrees`, cross-module state
documents must **only** be edited in the main dev repo — never inside a worktree:

- `docs/architecture/backend/plans/architecture-master-plan.md`
- `docs/architecture/frontend/plans/architecture-master-plan.md`
- `project-journal.md`, `project-checklist.md`

Each worktree runs `git checkout -- <file>` before pushing. Pull master-plan updates
from the dev repo into worktrees with `git pull` — do not author them inside the
worktree. Violating this causes merge conflicts that erase other terminals' history
(D-397).

---

## Essential commands

```bash
# Build a service  (Gradle project path: :services:<service>)
./gradlew :services:<service>:build

# Unit tests (fast, no Docker)
./gradlew :services:<service>:test

# ArchUnit -- BLOCKING -- run before each commit
./gradlew :services:<service>:architectureCheck

# Integration tests (Testcontainers, requires Docker)
./gradlew :services:<service>:integrationTest

# Full BE monorepo
./gradlew build

# Local infra
docker compose -f docker/docker-compose.dev.yml up -d

# Frontend (in /frontend folder)
npx nx serve neuroassist-ai-web                 # dev server
npx nx build neuroassist-ai-web                 # prod build
npx nx affected:test                     # tests affected by diff
npx nx affected:lint

```

---

## AI Pipeline -- per module

The pipeline operates per **MODULE** (GEST/EVNT/PAC/ACS/HIST/DASH/EDGE). There are **two complementary layers**:

```
┌─ LAYER 1 — ARCHITECT (creation of a NEW microservice, run once)
│   /be-architect-vision      → docs 01-06 + ADR skeleton
│   /be-architect-specs       → contracts (api / data-model / events / integration)
│   /be-architect-backlog     → atomic issues <MODULE>-NNN + plan-<MODULE>.md + generator-config.yml
│   /be-architect-scaffold    → calls scripts/be-module-generator.py (Spring Boot)
│   /be-architect-discovery   → reverse engineering of existing microservice
│
├─ LAYER 2 — EXECUTION (iterative cycle to evolve the module)
│   VO → PLANNER → VO → IMPLEMENTER (1 priority) → VO → ...
│   /be-validator <MODULE>    → sole entry point; BASELINE or DECISION mode
│   /be-planner   <MODULE>    → triggered by VO when strategy is needed
│   /be-implement <MODULE> P  → triggered by VO; ONE priority at a time
│   /be-autopilot <MODULE>    → orchestrator chaining VO+Planner+Implement with checkpoints
│
└─ LAYER 3 — Python SCRIPTS (in scripts/, invoked by commands)
    be-module-generator.py     — document-driven microservice generator
    be-entity-generator.py     — domain + entity + repo + service + ctrl + DTO via data-model.md
    be-migration-generator.py  — Flyway SQL via data-model.md
    be-bdd-generator.py        — Cucumber step stubs via .feature
    be-infra-generator.py      — infra files
    be-scaffold.py             — files via plan-<ISSUE>.md
    be-file-guard.py           — protection of critical files
    apply_file_updates.py      — patcher utility
```

### Frontend commands (mirror of backend)

```bash
# Architect (NEW module — 4 steps)
/fe-architect-vision   <MODULE>
/fe-architect-specs    <MODULE>
/fe-architect-backlog  <MODULE>
/fe-architect-scaffold <MODULE>

# Execution (same cycle as BE)
/fe-validator <MODULE>
/fe-planner   <MODULE>
/fe-implement <MODULE> <P>

# Pipeline per FEATURE (new feature in existing module)
/fe-feature-architect-discover      <MODULE> <feature-slug>
/fe-feature-architect-spec-delta    <MODULE> <feature-slug>
/fe-feature-architect-backlog       <MODULE> <feature-slug>
/fe-feature-architect-scaffold      <MODULE> <feature-slug>

# Meta-orchestrator (automates pipelines)
/fe-autopilot <MODULE> <feature-slug>    # Pipeline A (create feature)
/fe-autopilot <MODULE>-NNN               # Pipeline B (resolve issue)
```

### Recommended flow for a new module (from scratch)

```
1. /be-architect-vision  <MODULE>     → docs/architecture/backend/services/<svc>/01..06.md + adr/
2. /be-architect-specs   <MODULE>     → contracts/ (api, data-model, events)
3. /be-architect-backlog <MODULE>     → issues/<MODULE>-NNN.md + plans/plan-<MODULE>.md + <svc>/generator-config.yml
4. /be-architect-scaffold <MODULE>    → backend/services/<svc>/ (Spring Boot skeleton)
5. /be-validator <MODULE>             → enters BASELINE; generates service README
6. /be-planner   <MODULE>             → creates initial plan (if not yet existing)
7. /be-implement <MODULE> P1, P2, ... → each priority, with VO between them
   OR
   /be-autopilot <MODULE>             → automatic orchestrator with checkpoints
```

### Canonical outputs

- Plans: `docs/architecture/backend/plans/plan-<MODULE>.md` (BE) and `docs/architecture/frontend/plans/plan-<MODULE>.md` (FE)
- Operational README: `backend/services/<service>/README.md`
- Per-service spec: `docs/architecture/backend/services/<service>/{contracts,diagrams,execution,tests}/`
- Issues: `docs/architecture/{backend,frontend}/issues/<MODULE>-NNN.md`
- Project ADRs: `docs/architecture/backend/adr/ADR-NNN-*.md`
- Transversal standards: `docs/architecture/backend/standards/*.md`

---

## Activity observability — record everything (D-423)

Every substantive unit of work must leave a trace in Elasticsearch
(`techlab-pipeline-events-v1`), **even work done outside the official
`/be-*` / `/fe-*` pipeline** (ad-hoc fixes, refactors, investigations, manual
edits). The pipeline commands already journal themselves; for everything else,
follow the **same pattern** by hand:

```bash
# 1. open a trace once at the start of the work (mints a correlation id;
#    all events + any techlab-ai calls in this session inherit it)
python scripts/pipeline_events.py trace-start --command <label> [--module <MODULE>] [--model <model>]

# 2. emit one event per milestone (client is auto-derived from project-context.json)
python scripts/pipeline_events.py emit \
  --category <PHASE-START|PHASE-END|COMMAND-END|DECISION> \
  --actor <who> --module <MODULE> --status <started|completed|blocked> \
  --message "<one line>" [--details '{...}'] \
  [--duration-ms <N>] [--tokens-input <N> --tokens-output <N>]

# 3. close the trace at the end
python scripts/pipeline_events.py trace-end
```

Granularity = per milestone, not per keystroke. `available:false` (ES down) never
blocks — the call is best-effort. This is how the project stays fully observable:
one `trace_id` query reconstructs any work session, pipeline or not.

**Managed activity (D-428).** When `TECHLAB_AI_URL` is set, these events are routed
through techlab-ai's managed sink (`POST /v1/framework/record-activity`) instead of
being written to Elasticsearch directly — a single, governed writer, with the run
`trace_id` carried in the `X-Correlation-Id` header so your activity correlates with
the LLM/RAG calls techlab-ai records for the same run. Leave the var unset and the
event is written straight to ES (zero-infra). Either way the call is best-effort and
the command above is unchanged.

---

## Reference documents (read before implementing)

| Document | Contains |
|---|---|
| `docs/architecture/backend/standards/hexagonal-patterns.md` | Ports & Adapters, boundaries, ArchUnit rules |
| `docs/architecture/backend/standards/java-conventions.md` | Lombok, SLF4J, DI, JPA, naming, validation |
| `docs/architecture/backend/standards/snippets.md` | Snippets reference (ports, adapters, services, JMS, Feign, tests) |
| `docs/architecture/backend/standards/platform-patterns.md` | JMS Artemis, Feign+Resilience4j, centralized Flyway, MDC, Saga |
| `docs/architecture/backend/standards/service-identity.md` | **Service-to-service auth**: when to propagate the caller's JWT vs. use a service token, the `POST /oauth2/token/service` contract, and the interceptor-ordering trap |
| `docs/architecture/backend/standards/testing-conventions.md` | Pyramid, Testcontainers, Cucumber, Pact |
| `docs/architecture/backend/standards/services-catalog.md` | Catalog of microservices + canonical packages + events + Feign clients |
| `docs/architecture/backend/standards/service-spec-authoring.md` | How to write per-service SPECs (Phase 1-3) |
| `docs/architecture/backend/standards/api-mock-convention.md` | Mock profile, /api/v0/, fixtures |


| `docs/architecture/frontend/standards/nx-boundaries.md` | Nx tags, allowed directions, ESLint config |
| `docs/architecture/frontend/standards/access-layer-contracts.md` | AuthSession, AccessDecision |
| `docs/architecture/shared/blueprint-core.md` | Mental model: app + modules + shared, golden rules |
| `docs/paper/PAPER-PLAN.md` | Paper acadêmico do NeuroAssist AI — contribuições, IMRaD, experimentos, venues, instruções para o Claude Code CLI |
---

## Never do this

- **Never** use base package different from `com.neuroassist.<module>`
- **Never** introduce "tool/PREFIX" concept -- here only MODULE exists
- **Never** put `@Transactional` in controller, repository or adapter
- **Never** return JPA `@Entity` directly from controller -- always DTO record
- **Never** use `@Autowired` in field -- always `@RequiredArgsConstructor`
- **Never** use `System.out` -- `@Slf4j` via Lombok
- **Never** hardcode secret -- use `${VAR_NAME}` in `application.yml`
- **Never** commit with `ddl-auto: create|update` -- always `validate`
- **Never** use `@Testcontainers` + `@Container` -- singleton container pattern (static initializer)
- **Never** forget `DomainServiceConfig.java` (domain services are POJOs and need `@Bean`)
- **Never** run a microservice before `neuroassist-ai-migrations`
- **Never** use `feign.client.config` prefix — since Spring Cloud 2025.0.1 it is silently ignored; always use `spring.cloud.openfeign.client.config` (infinite default timeout if wrong)
- **Never** let the application assign an entity id while the entity also declares
  `@GeneratedValue` — Spring Data's `isNew()` becomes id-based, `save()` issues `merge()`
  instead of `persist()`, and an INSERT of a not-yet-existing row fails with
  `ObjectOptimisticLockingFailureException`. Implement `Persistable<ID>` and let the adapter
  decide (`existsById`); device registration in EDGE never inserted a row because of this
- **Never** resolve the tenant *inside* a `@Transactional` method — Hibernate fixes the
  `search_path` when it acquires the connection, so binding the tenant afterwards routes
  nothing. Resolve before the transaction (see `DeviceEnrollmentService` in EDGE)
- **Never** declare a Feign response record as package-private — JDK dynamic proxies (`jdk.proxy3.$ProxyN`) cannot access it; always `public record`
- **Never** write a Feign client (path, body or response DTO) from the consumer's assumption — open the producer's `@RestController` and its request/response records and mirror them. A WireMock stub written from the same assumption makes the test pass *precisely when the assumption is wrong* (E2E Session 2: 10 of 16 defects)
- **Never** invent an authority name in `RoleAuthorityPolicy` — every value must match a literal `@PreAuthorize` / SecurityConfig rule in the owning service, verbatim
- **Never** treat the tenant identifier as a schema name — see "Cross-service contracts" below
- **Never** rely on a green module gate as proof of integration — the 7 modules were all green while 16 cross-service defects were live

---

## TODOs are not reminders — report every one

**Whenever you encounter a `TODO`, `FIXME`, `XXX`, `HACK`, a fake `@Primary` adapter, an
empty `@FeignClient`, or a method that throws `UnsupportedOperationException`: report it to
the user AND emit an ES signal.** Even when it is outside the scope of the task at hand.
Include the same instruction in any subagent briefing you write.

```bash
python scripts/pipeline_events.py emit --category LEARNING --actor <who> --module <MODULE> \
  --status blocked --message "[TODO-LIVE][<SVC>] <location> — <what is missing>" \
  --details '{"kind":"live-stub","location":"...","missing":"...","blocks":"..."}'
```

In this repo a TODO is rarely a reminder — it is **absent capability disguised as present
capability**, and the module gates go green anyway because the logic is tested against its
own fakes. Distinguish two kinds:

- **Live stub** (`blocked`) — reachable at runtime and lies about working: an endpoint that
  throws 501, a fake `@Primary` adapter, an empty Feign client whose adapter silently skips
  the call. These are the ones that bite in a later session.
- **Dead scaffolding** (`completed`, minor) — a generated port/adapter that throws but is
  never referenced by any application service. Harmless at runtime, still misleading.

Current inventory and what each item blocks:
`docs/architecture/backend/plans/plan-e2e-integration.md` §11.3.

**Related trap — a guard that guards nothing.** Two variants seen in this repo: a
`@CircuitBreaker` without `ignoreExceptions` turns a 403 into "service degraded" (an
authorization bug becomes indistinguishable from an outage), and an ArchUnit catalogue pinned
inline that contains *both* sides of a divergence it is supposed to detect. When a guard is
green, check what it actually compares.

**`.gitignore` patterns without a leading slash match at any depth.** `out/` silently
excluded `application/port/out/` — the hexagonal outbound-port package — in all 7 services:
61 of 62 port interfaces were never in the repository. Nothing caught it, because Gradle
compiles the **working directory**, not the git index. Anchor build-artifact patterns
(`/out/`, `/build/`) and, when adding a package whose name collides with a build convention,
check `git status` shows the new file.

**The `:test` gate does not run `integrationTest`.** `./gradlew :services:<svc>:test
:services:<svc>:architectureCheck` can stay green while an `*IT` class breaks. When Docker is
available, run `integrationTest` too before declaring a change safe.

---

## Cross-service contracts (learned the hard way — E2E Session 2)

Catalogue of the 16 defects with root cause and why the unit gates missed each:
`docs/architecture/backend/plans/plan-e2e-integration.md` §11.

### Auth backbone — the canonical shapes

`acs-service` (`TokenIssuer`) issues an RS256 JWT with:

| Claim | Value | Note |
|---|---|---|
| `authorities` | list of fine-grained authorities | **This is where authorities live — NOT `scope`** |
| `tenant_id` | tenant UUID | canonical |
| `tenant`, `empresaId` | same UUID | compat aliases; converge on `tenant_id` |
| `papel`, `nome`, `sub` | role, display name, caregiver id | |

Every resource server MUST therefore configure its converter to read the **union** of
`scope` and `authorities` — the Spring default reads only `scope`, which yields zero
authorities and a 403 on every `@PreAuthorize`:

```java
JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
scopeConverter.setAuthorityPrefix("");                      // or "SCOPE_" — see below
JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
authoritiesConverter.setAuthorityPrefix("");
authoritiesConverter.setAuthoritiesClaimName("authorities"); // ← the claim ACS populates
// converter = union of both
```

Authority naming is `<service>.<resource>.<action>` with **empty prefix** everywhere,
except `hist-service`, which enforces the colon form with the `SCOPE_` prefix
(`SCOPE_hist:read`, `hist:read:all`, `hist:write`). Do not mix the two in one service.

### Multi-tenancy — tenant id ≠ schema name

ACS issues the tenant **UUID**; the provisioned schema is recorded in
`neuroassist_global.tenant.schema_nome` (e.g. `t_emp_dev`). `MultiTenantConnectionProvider`
must resolve `tenant_id → schema_nome` (cached) before `SET search_path`, falling back to
the identifier when the catalogue has no row. Never derive the schema from the identifier
(neither raw nor `t_<id>`) — that reintroduces the defect.

### Schema drift: the suites do not validate the real schema

Module suites run `ddl-auto=create-drop`, so **Hibernate designs the schema in tests while
Flyway designs it in production**. Any divergence between a migration and a JPA mapping is
invisible until runtime (a Postgres `ENUM` column mapped as `String` broke every outbox
write). When you touch a migration or an entity, check the other side by hand.

Flyway `out-of-order: true` is required by the per-module V-range convention (PAC
V0200-V0299, GEST V0400-V0499, EDGE V0700-V0799, ACS V0800-V0899…): a new migration in a
low range is by construction out of order against higher ranges already applied.

### Service ports (host → container, docker-compose.dev.yml)

`acs 8088 · pac 8082 · gest 8084 · edge 8087 · hist 8089 · evnt 18085 · dash 18090 · gwy 18080 · eureka 18761 · pg 5433`

Feign `url` defaults pointing at `localhost` resolve to **the calling container itself**.
Always provide the docker env var (`ACS_SERVICE_URL`, etc.) in `docker-compose.dev.yml`.

---

## Git conventions

Valid commit scopes:
`gest, evnt, pac, acs, hist, dash, edge, web, libs, docs, infra, deps`.

Permanent branches: `dev`, `release`, `prod`. Never direct push -- always Pull Request.
