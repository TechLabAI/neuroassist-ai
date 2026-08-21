# NeuroAssist AI — Checklist

> Progresso do projeto. Setup (ETAPA 1) é sincronizado de `project-context.json` via `scripts/checklist.py sync`.

## ETAPA 1 — Setup

### P0 — Repository bootstrap (minimal structure + git init)
- [x] Repository bootstrap (minimal structure + git init) (`new-project.py bootstrap`)

### P1 — Copy .claude/ + generated CLAUDE.md
- [x] Copy .claude/ + generated CLAUDE.md (`new-project.py claude-folder`)

### P2 — Create docs/architecture/ per rigor level
- [x] Create docs/architecture/ per rigor level (`new-project.py docs-skeleton --rigor complete-bosq`)

### P3 — Sync TechLab AI docs snapshot (docs-only) and populate integration contract  _(condicional: ai.hasAi == true)_
- [/] Sync TechLab AI docs snapshot (docs-only) and populate integration contract (`sync-techlab-ai.py --target-sha <pinned>`)

### P3b — Generate elastic-stack overlay (compose + integration contract + Java ports/adapters)  _(condicional: stack.elastic.enabled == true)_
- [/] Generate elastic-stack overlay (compose + integration contract + Java ports/adapters) (`new-project.py backend-skeleton (elastic-stack overlay triggered automatically)`)

### P4 — backend/ Gradle root + empty settings.gradle.kts
- [x] backend/ Gradle root + empty settings.gradle.kts (`new-project.py backend-skeleton`)

### P5 — frontend/ empty Nx workspace  _(condicional: stack.frontend.enabled == true)_
- [x] frontend/ empty Nx workspace (`new-project.py frontend-skeleton`)

### Pg — Growth skeleton (docs/growth/ scaffold + admin-module service-spec)  _(condicional: growth.enabled == true)_
- [/] Growth skeleton (docs/growth/ scaffold + admin-module service-spec) (`new-project.py growth-skeleton`)

### P6 — Handoff message: setup done, next step is /be-architect-vision <MODULE>
- [x] Handoff message: setup done, next step is /be-architect-vision <MODULE>

## ETAPA 2 — Desenvolvimento

### Módulos backend

#### GEST — Detecção de Gesto (Edge) (`gest-service`)
- [x] **Arquitetura** — `/be-architect-vision GEST` → specs → backlog → scaffold
- [ ] **Ciclo de desenvolvimento** — `/be-validator GEST` → planner → implement
- **Status atual:** scaffolded

#### EVNT — Eventos e Alarmes (`evnt-service`)
- [x] **Arquitetura** — `/be-architect-vision EVNT` → specs → backlog → scaffold
- [ ] **Ciclo de desenvolvimento** — `/be-validator EVNT` → planner → implement
- **Status atual:** scaffolded

#### PAC — Pacientes e Catálogo de Comandos (`pac-service`)
- [x] **Arquitetura** — `/be-architect-vision PAC` → specs → backlog → scaffold
- [ ] **Ciclo de desenvolvimento** — `/be-validator PAC` → planner → implement
- **Status atual:** scaffolded

#### ACS — Cuidadores e Acesso (`acs-service`)
- [x] **Arquitetura** — `/be-architect-vision ACS` → specs → backlog → scaffold
- [ ] **Ciclo de desenvolvimento** — `/be-validator ACS` → planner → implement
- **Status atual:** scaffolded

#### HIST — Histórico e Auditoria (`hist-service`)
- [ ] **Arquitetura** — `/be-architect-vision HIST` → specs → backlog → scaffold
- [ ] **Ciclo de desenvolvimento** — `/be-validator HIST` → planner → implement
- **Status atual:** declared

#### DASH — Dashboard e Notificações (`dash-service`)
- [ ] **Arquitetura** — `/be-architect-vision DASH` → specs → backlog → scaffold
- [ ] **Ciclo de desenvolvimento** — `/be-validator DASH` → planner → implement
- **Status atual:** declared

#### EDGE — Integração de Dispositivo Edge (`edge-service`)
- [x] **Arquitetura** — `/be-architect-vision EDGE` → specs → backlog → scaffold
- [ ] **Ciclo de desenvolvimento** — `/be-validator EDGE` → planner → implement
- **Status atual:** scaffolded

#### GWY — API Gateway (`neuroassist-ai-api-gateway`)
- [x] **Arquitetura** (4 etapas)
  - [x] `/be-architect-vision GWY` → docs/services/neuroassist-ai-api-gateway/01..06.md + adr/
  - [x] `/be-architect-specs GWY` → contracts/ (N/A — módulo infra)
  - [x] `/be-architect-backlog GWY` → issues + plan
  - [x] `/be-architect-scaffold GWY` → backend/services/neuroassist-ai-api-gateway/
- [ ] **Ciclo de desenvolvimento**
  - [ ] `/be-validator GWY BASELINE`
  - [ ] `/be-planner GWY`
  - [ ] `/be-implement GWY P1`
- **Status atual:** scaffolded

#### REG — Service Registry (`neuroassist-ai-service-registry`)
- [x] **Arquitetura** (4 etapas)
  - [x] `/be-architect-vision REG` → docs/services/neuroassist-ai-service-registry/01..06.md + adr/
  - [x] `/be-architect-specs REG` → contracts/ (N/A — módulo infra)
  - [x] `/be-architect-backlog REG` → issues + plan
  - [x] `/be-architect-scaffold REG` → backend/services/neuroassist-ai-service-registry/
- [ ] **Ciclo de desenvolvimento**
  - [ ] `/be-validator REG BASELINE`
  - [ ] `/be-planner REG`
  - [ ] `/be-implement REG P1`
- **Status atual:** scaffolded

#### MIG — Migrations (`neuroassist-ai-migrations`)
- [x] **Arquitetura** (4 etapas)
  - [x] `/be-architect-vision MIG` → docs/services/neuroassist-ai-migrations/01..06.md + adr/
  - [x] `/be-architect-specs MIG` → contracts/ (N/A — módulo infra)
  - [x] `/be-architect-backlog MIG` → issues + plan
  - [x] `/be-architect-scaffold MIG` → backend/services/neuroassist-ai-migrations/
- [ ] **Ciclo de desenvolvimento**
  - [ ] `/be-validator MIG BASELINE`
  - [ ] `/be-planner MIG`
  - [ ] `/be-implement MIG P1`
- **Status atual:** scaffolded

### Módulos frontend

#### GEST — Detecção de Gesto (Edge) (`gest`)
- [x] **Arquitetura** — `/fe-architect-vision GEST` → specs → specs-validate → backlog → promote
- [x] **Ciclo de desenvolvimento** — `/fe-autopilot GEST` (2026-08-15)
- **Status atual:** score 80/100 (Maduro). P1-P6 DONE (26/33 issues) — foundation, unit tests (122), ADR-006 interim-detector safeguards, multi-empresa seam, full e2e (14/14 Playwright, incl. mandatory gate GEST-FE-026). Remaining (non-blocking): P7 UX polish (breadcrumb, loading/error states), P8 observability sink, P9 lib-split refactor, P10 auth bridge (joint decision with EDGE, mTLS vs OAuth2 pending). PR #4 (merged).

#### EVNT — Eventos e Alarmes (`evnt`)
- [x] **Arquitetura** — `/fe-architect-vision EVNT` → specs → specs-validate → backlog → promote
- [x] **Ciclo de desenvolvimento** — `/fe-autopilot EVNT` (closed prior session, confirmed 2026-08-14)
- **Status atual:** score 90/100 (Maduro). 37/37 issues DONE. Gates lint/build/test(34/34) OK. Multi-empresa OK. PR #2 (merged).

#### PAC — Pacientes e Catálogo de Comandos (`pac`)
- [x] **Arquitetura** — `/fe-architect-vision PAC` → specs → specs-validate → backlog → promote
- [x] **Ciclo de desenvolvimento** — `/fe-autopilot PAC` (2026-08-13/14)
- **Status atual:** score 88/100 (Maduro). P1-P9 DONE (22/31 core; P9 reduced scope: command-catalog e2e 12/12 PASS, incl. safety-critical emergency-gate blocking modal). Deferred: editor lifecycle-button e2e scenarios + patient-register wizard composition (pending ACS), RBAC-write e2e (pending AccessDecisionService). PR #2 + #3 (merged).

#### ACS — Cuidadores e Acesso (`acs`)
- [x] **Arquitetura** — `/fe-architect-vision ACS` → specs → specs-validate → backlog → promote
- [x] **Ciclo de desenvolvimento** — `/fe-autopilot ACS` (2026-08-15, safety cap reached — resume pending)
- **Status atual:** score 41→80/100 (Maduro) in one invocation. P1-P5 DONE (16/36): lint gate, RBAC guards closed (all 3 were open stubs returning `true` unconditionally — real security gap, now fixed via Wave-0 InjectionToken pattern matching EDGE), module contract, 4 feature components fully functional (reactive forms, real data flows), 170 unit tests (84.9% coverage). plan-FE-ACS.md was a dead stub pointing to a nonexistent external path — reconstructed locally (9 waves). **Remaining P6 (e2e, 25+ scenarios still test.skip()) is the real MVP-gate blocker** — resume with `/fe-autopilot ACS --resume`. Also P6b (X-Empresa-Id HTTP wiring), P7 (observability), P8 (UX polish incl. safety-critical coverage-blocking modal, ADR-005), P9 (go-live gate). PR #3 (merged).

#### HIST — Histórico e Auditoria (`hist`)
- [ ] **Arquitetura** — `/fe-architect-vision HIST` → specs → specs-validate → backlog → promote
- [ ] **Ciclo de desenvolvimento** — `/fe-validator HIST` → planner → implement
- **Status atual:** declared (untouched — not even scaffolded)

#### DASH — Dashboard e Notificações (`dash`)
- [ ] **Arquitetura** — `/fe-architect-vision DASH` → specs → specs-validate → backlog → promote
- [ ] **Ciclo de desenvolvimento** — `/fe-validator DASH` → planner → implement
- **Status atual:** declared (untouched — not even scaffolded)

#### EDGE — Integração de Dispositivo Edge (`edge`)
- [x] **Arquitetura** — `/fe-architect-vision EDGE` → specs → specs-validate → backlog → promote
- [x] **Ciclo de desenvolvimento** — `/fe-autopilot EDGE` (2026-08-14/15, 2 invocations)
- **Status atual:** score 45→93/100 (Maduro). P1-P7 DONE (25/33): Nx/Jest infra, safety invariants tested (honest device state, no premature "paired"), 6 UI components, real app-shell integration (topbar chip + onboarding pairing step via NgComponentOutlet), RBAC guards, breadcrumb nav, G6 retry-backoff safety closure. 231 tests. Remaining: P8 (observability), P9 (full e2e — only invariant/shell tests exist so far). Also fixed a cross-module bug (`relative-time.component.ts` `_isoDate` field visibility) that was silently blocking all clicks app-wide in dev mode, found via GEST's e2e work. PR #3 + #4 (merged).

### Infraestrutura desta sessão (2026-08-13 a 2026-08-15)

- **App-shell bootstrap:** `frontend/apps/neuroassist-ai-web/` (main.ts, app.config.ts, index.html, project.json) não existia — `nx serve`/`nx build`/e2e falhavam para TODOS os módulos. Criado do zero; `@angular/build` + `@angular-devkit/build-angular` instalados (peer deps do `@nx/angular` que faltavam). Todos os 5 módulos com FE promovido (GEST/EVNT/PAC/ACS/EDGE) agora compostos de verdade no shell real (nav, topbar, onboarding wizard), não apenas via mock isolado.
- **Investigação backend GEST (2026-08-15):** tentativa de conectar o FE GEST ao `gest-service` real revelou que **nenhum serviço backend passou pelo próprio ciclo de desenvolvimento** (`/be-validator` → planner → implement) — todos permanecem em "scaffolded". `gest-service` tem lógica de domínio real implementada (P1-P3, não são stubs como a seção "Gaps" do seu próprio README sugere — doc desatualizada), mas P4 (integração EDGE), P6 (messaging), P8 (BDD e2e) estão ausentes e P5 (security/multi-tenancy) é parcial. Adicionalmente, o bring-up local está com drift de configuração: o `docker-compose.yml` do `gest-service` cria um banco isolado (`gest_dev`) que não bate com a convenção centralizada de `neuroassist-ai-migrations` (banco `neuroassist` + schemas por tenant `t_<tenantId>`) que o `CLAUDE.md` instrui rodar primeiro. Ninguém rodou esse fluxo local end-to-end ainda. Decisão: adiado — retomar via ciclo de desenvolvimento backend do GEST antes de tentar integração real novamente.

