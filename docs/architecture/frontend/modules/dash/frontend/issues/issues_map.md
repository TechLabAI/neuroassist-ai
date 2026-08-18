# DASH FE Issues Map

**Module:** DASH — Dashboard e Notificações
**Lib:** `@neuroassist/dash`
**Total:** 30 issues funcionais + 6 issues de integração = **36 issues**
**Phase:** fe-backlog M5

---

## Sumário por prioridade

| Onda | Foco | Issues | Total |
|---|---|---|---|
| **P1 — Foundation** | Contrato + módulo + rotas + shell registration | FE-DASH-001..004 | 4 |
| **P2 — Data Access** | Services + Facades + SSE client | FE-DASH-005..008 | 4 |
| **P3 — Feature Libs** | Pages + componentes presentacionais | FE-DASH-009..015 | 7 |
| **P4 — Integrations** | Cross-module wiring + write-throughs | INT-FE-DASH-001..006 | 6 |
| **P5 — Guards** | authGuard + roleGuard | FE-DASH-016..017 | 2 |
| **P6 — Shell Integration** | DashShellIntegrationService + bootstrap + navigation | FE-DASH-018..020 | 3 |
| **P7 — UX Polish** | Loading state (G4) + 403 banner (G8) | FE-DASH-021..022 | 2 |
| **P8 — Observability** | Telemetria via @neuroassist/shared/observability | FE-DASH-023 | 1 |
| **P9 — e2e + Hardening** | Playwright specs cobrindo todas as features + gaps | FE-DASH-024..030 | 7 |
| **Total funcional** | | FE-DASH-001..030 | **30** |
| **Total integração** | | INT-FE-DASH-001..006 | **6** |

---

## Índice completo

### P1 — Foundation

| ID | Título | Camada | Spec |
|---|---|---|---|
| FE-DASH-001 | dash.contract.ts — MODULE_KEY, menus, permissions e feature-flags | libs/dash/shell-entry | SPEC-05 |
| FE-DASH-002 | DashModule + dash.routes.ts — módulo lazy e tabela de rotas | libs/dash/shell-entry | SPEC-01 |
| FE-DASH-003 | dash-descriptor.ts — fallback estático para o Shell | libs/dash/shell-entry | SPEC-06 |
| FE-DASH-004 | Shell: registrar DASH_MODULE_DESCRIPTOR em module-descriptors.ts | apps/neuroassist-ai-web | SPEC-06 |

### P2 — Data Access

| ID | Título | Camada | Spec |
|---|---|---|---|
| FE-DASH-005 | DashApiService + DTOs — wrapper HTTP sobre /panel/** | libs/dash/data-access | SPEC-04 |
| FE-DASH-006 | DashHomeFacade — signals summary/state/calm + mapeamento DTO→VM | libs/dash/data-access | SPEC-03/04 |
| FE-DASH-007 | NotificationsFacade — signals items/state/filter/unreadCount + writes | libs/dash/data-access | SPEC-03/04 |
| FE-DASH-008 | DashStreamClient — SSE EventSource + heartbeat + reconnect + long-poll fallback | libs/dash/data-access | SPEC-04 |

### P3 — Feature Libs

| ID | Título | Camada | Spec |
|---|---|---|---|
| FE-DASH-009 | EntradaPageComponent — brand entry chromeless (feature-entrada) | libs/dash/feature-entrada | SPEC-01/02 |
| FE-DASH-010 | HomeStatusPageComponent — container smart (feature-home) | libs/dash/feature-home | SPEC-02/03 |
| FE-DASH-011 | StatusCardComponent — ui apresentacional (calm/loading/error) | libs/dash/ui | SPEC-02/03 |
| FE-DASH-012 | PatientHeaderComponent + QuickLinkListComponent — ui apresentacionais | libs/dash/ui | SPEC-02/03 |
| FE-DASH-013 | NotificationCenterPageComponent — container smart (feature-notifications) | libs/dash/feature-notifications | SPEC-02/03 |
| FE-DASH-014 | NotificationFilterBarComponent + NotificationListComponent + NotificationListItemComponent — ui | libs/dash/ui | SPEC-02/03 |
| FE-DASH-015 | EmptyOrErrorStateComponent — estado inline vazio/erro (G5) | libs/dash/ui | SPEC-02/03 |

### P4 — Hub Integrations

| ID | Título | Camada | Spec |
|---|---|---|---|
| INT-FE-DASH-001 | Wire activeEvent (EVNT) → DashHomeFacade.calm via scope:web | apps/neuroassist-ai-web | SPEC-03/06 |
| INT-FE-DASH-002 | Wire DeviceBadge (EDGE signal) → PatientHeaderComponent via scope:web | apps/neuroassist-ai-web | SPEC-02/06 |
| INT-FE-DASH-003 | ShellOutletRegistry: declarar região dash.global-interrupt para EVNT | libs/dash/integration | SPEC-05/06 |
| INT-FE-DASH-004 | Write-through calibração → PAC (PUT /panel/patients/.../calibration) | libs/dash/feature-home | SPEC-04/07 |
| INT-FE-DASH-005 | Write-through confirm event → EVNT (POST /panel/events/{eventId}/confirm) | libs/dash/feature-notifications | SPEC-04/07 |
| INT-FE-DASH-006 | HIST cross-module navigation — quick-link de paciente (scope:web) | libs/dash/data-access | SPEC-02/04 |

### P5 — Guards

| ID | Título | Camada | Spec |
|---|---|---|---|
| FE-DASH-016 | authGuard — proteção das rotas / e /notificacoes | libs/dash/access | SPEC-01 |
| FE-DASH-017 | roleGuard — ocultar item de navegação Onboarding para cuidadora (G1) | libs/dash/access | SPEC-01/05 |

### P6 — Shell Integration + Navigation

| ID | Título | Camada | Spec |
|---|---|---|---|
| FE-DASH-018 | DashShellIntegrationService — registerWithShell + bindActiveEvent + bindDeviceBadge + releaseOutletRegion | libs/dash/integration | SPEC-06 |
| FE-DASH-019 | dash.bootstrap.ts — init (SSE + summary prime) e destroy (SSE close + region release) | libs/dash/shell-entry | SPEC-06 |
| FE-DASH-020 | libs/dash/navigation — BreadcrumbItem[] para Status e Notificações | libs/dash/navigation | SPEC-01 |

### P7 — UX Polish (Coverage Gaps)

| ID | Título | Gap | Camada |
|---|---|---|---|
| FE-DASH-021 | StatusCard loading state skeleton | G4 | libs/dash/ui |
| FE-DASH-022 | Banner 403 forbidden-scope inline | G8 | libs/dash/ui |

### P8 — Observability

| ID | Título | Camada |
|---|---|---|
| FE-DASH-023 | Telemetria — eventos via @neuroassist/shared/observability | libs/dash/data-access |

### P9 — e2e + Hardening

| ID | Título | Gaps cobertos | Spec |
|---|---|---|---|
| FE-DASH-024 | e2e: home-status calm + role-hide nav + /notificacoes | G1, G2 | feature home-status-calm.feature |
| FE-DASH-025 | e2e: chromeless /entrada | G3 | SPEC-01 |
| FE-DASH-026 | e2e: notification delivery (SSE + lista + acknowledge) | G5 | notification-delivery.feature |
| FE-DASH-027 | e2e: calibration write-through | — | calibration-writethrough.feature |
| FE-DASH-028 | e2e: bootstrap register nav+outlet | G6 | SPEC-06 |
| FE-DASH-029 | e2e: destroy SSE + region release | G7 | SPEC-06 |
| FE-DASH-030 | e2e: 403 forbidden-scope banner + 503 retry + SSE fallback | G8 | SPEC-07 |

---

## Coverage gaps mapeados

| Gap | Descrição | Issue(s) que fecha |
|---|---|---|
| G1 | role-hide nav (SPEC-01 — Onboarding oculto para cuidadora) | FE-DASH-017, FE-DASH-024 |
| G2 | rota /notificacoes (SPEC-01 — rota acessível + breadcrumb) | FE-DASH-024 |
| G3 | chromeless /entrada (SPEC-01 — sem sidebar, topbar, breadcrumb) | FE-DASH-009, FE-DASH-004, FE-DASH-025 |
| G4 | loading state StatusCard (SPEC-03 — skeleton sem "está bem") | FE-DASH-021 |
| G5 | lista vazia/erro inline (SPEC-03 — EmptyOrErrorState nunca bloqueia Home) | FE-DASH-015, FE-DASH-026 |
| G6 | bootstrap register nav+outlet (SPEC-06 — idempotente) | FE-DASH-018, FE-DASH-019, INT-FE-DASH-003, FE-DASH-028 |
| G7 | destroy SSE+region (SPEC-06 — cleanup no destroy) | FE-DASH-019, FE-DASH-029 |
| G8 | 403 banner (SPEC-07 — inline, sem revelar existência) | FE-DASH-022, FE-DASH-030 |

---

## Por SPEC de origem

| SPEC | Issues |
|---|---|
| SPEC-01 (Route Map) | FE-DASH-002, FE-DASH-004, FE-DASH-009, FE-DASH-016, FE-DASH-017, FE-DASH-020, FE-DASH-024, FE-DASH-025 |
| SPEC-02 (Component Hierarchy) | FE-DASH-009..015, FE-DASH-012, FE-DASH-014 |
| SPEC-03 (View Models) | FE-DASH-006..007, FE-DASH-010..013, FE-DASH-021 |
| SPEC-04 (Data Access) | FE-DASH-005..008, INT-FE-DASH-004..006, FE-DASH-026..027 |
| SPEC-05 (Module Contract) | FE-DASH-001, FE-DASH-003, FE-DASH-017, INT-FE-DASH-003 |
| SPEC-06 (Shell Integration) | FE-DASH-018..019, FE-DASH-020, INT-FE-DASH-001..003, FE-DASH-028..029 |
| SPEC-07 (Error Handling) | FE-DASH-022, INT-FE-DASH-004..005, FE-DASH-030 |
| SPEC-08 (e2e Scenarios) | FE-DASH-024..030 |

---

## Caminho crítico (sequência de bloqueio)

```
FE-DASH-001 (contrato)
  └── FE-DASH-002 (módulo + rotas)
        └── FE-DASH-004 (shell registration)
              └── FE-DASH-009 (feature-entrada)
                    └── FE-DASH-025 (e2e G3)

FE-DASH-005 (DashApiService)
  ├── FE-DASH-006 (DashHomeFacade)
  │     ├── FE-DASH-010 (HomeStatusPageComponent)
  │     │     ├── FE-DASH-011 (StatusCardComponent)
  │     │     │     └── FE-DASH-021 (loading state G4)
  │     │     └── FE-DASH-012 (PatientHeader + QuickLinks)
  │     └── INT-FE-DASH-001 (wire activeEvent)
  │           └── FE-DASH-024 (e2e G1/G2)
  └── FE-DASH-008 (DashStreamClient)
        └── FE-DASH-007 (NotificationsFacade)
              ├── FE-DASH-013 (NotificationCenterPageComponent)
              │     ├── FE-DASH-015 (EmptyOrErrorState G5)
              │     │     └── FE-DASH-026 (e2e G5)
              │     └── INT-FE-DASH-005 (confirm event)
              └── FE-DASH-019 (bootstrap/destroy)
                    ├── FE-DASH-028 (e2e G6)
                    └── FE-DASH-029 (e2e G7)

FE-DASH-018 (DashShellIntegrationService)
  ├── INT-FE-DASH-003 (outlet region)
  └── FE-DASH-019 (bootstrap)

FE-DASH-022 (403 banner)
  └── FE-DASH-030 (e2e G8)
```

---

## Estimativas por onda

| Onda | Issues | Estimativa |
|---|---|---|
| P1 Foundation | 4 | 2d |
| P2 Data Access | 4 | 2.5d |
| P3 Feature Libs | 7 | 3.5d |
| P4 Integrations | 6 | 2d |
| P5 Guards | 2 | 0.5d |
| P6 Shell Integration | 3 | 1.5d |
| P7 UX Polish | 2 | 0.5d |
| P8 Observability | 1 | 0.5d |
| P9 e2e + Hardening | 7 | 3d |
| **Total** | **36** | **~16d** |
