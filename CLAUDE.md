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
| PAC | Pacientes e Catálogo de Comandos | `pac-service` | `libs/pac` | 8082 | ✅ scaffolded |
| ACS | Cuidadores e Acesso | `acs-service` | `libs/acs` | 8088 | ✅ scaffolded |
| EDGE | Integração de Dispositivo Edge | `edge-service` | `libs/edge` | 8087 | 🔄 scaffolded + P1-P6 done |
| HIST | Histórico e Auditoria | `hist-service` | `libs/hist` | 8089 | 🔄 scaffolded |
| DASH | Dashboard e Notificações | `dash-service` | `libs/dash` | 8090 | 🔄 scaffolded |

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

---

## Git conventions

Valid commit scopes:
`gest, evnt, pac, acs, hist, dash, edge, web, libs, docs, infra, deps`.

Permanent branches: `dev`, `release`, `prod`. Never direct push -- always Pull Request.
