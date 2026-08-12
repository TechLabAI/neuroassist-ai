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
- [ ] **Ciclo de desenvolvimento** — `/fe-validator GEST` → planner → implement
- **Status atual:** promoted

#### EVNT — Eventos e Alarmes (`evnt`)
- [x] **Arquitetura** — `/fe-architect-vision EVNT` → specs → specs-validate → backlog → promote
- [ ] **Ciclo de desenvolvimento** — `/fe-validator EVNT` → planner → implement
- **Status atual:** promoted

#### PAC — Pacientes e Catálogo de Comandos (`pac`)
- [x] **Arquitetura** — `/fe-architect-vision PAC` → specs → specs-validate → backlog → promote
- [ ] **Ciclo de desenvolvimento** — `/fe-validator PAC` → planner → implement
- **Status atual:** promoted

#### ACS — Cuidadores e Acesso (`acs`)
- [~] **Arquitetura** — `/fe-architect-vision ACS` → specs → specs-validate → backlog → promote
- [ ] **Ciclo de desenvolvimento** — `/fe-validator ACS` → planner → implement
- **Status atual:** backlog-done (promote pendente — lib acs/ está vazia)

#### HIST — Histórico e Auditoria (`hist`)
- [ ] **Arquitetura** — `/fe-architect-vision HIST` → specs → specs-validate → backlog → promote
- [ ] **Ciclo de desenvolvimento** — `/fe-validator HIST` → planner → implement
- **Status atual:** declared

#### DASH — Dashboard e Notificações (`dash`)
- [ ] **Arquitetura** — `/fe-architect-vision DASH` → specs → specs-validate → backlog → promote
- [ ] **Ciclo de desenvolvimento** — `/fe-validator DASH` → planner → implement
- **Status atual:** declared

#### EDGE — Integração de Dispositivo Edge (`edge`)
- [x] **Arquitetura** — `/fe-architect-vision EDGE` → specs → specs-validate → backlog → promote
- [ ] **Ciclo de desenvolvimento** — `/fe-validator EDGE` → planner → implement
- **Status atual:** promoted

