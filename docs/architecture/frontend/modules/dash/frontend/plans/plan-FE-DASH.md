# Plan FE-DASH — Dashboard e Notificações (Frontend)

> **Module:** DASH · **Lib:** `@neuroassist/dash` · **Shell app:** `neuroassist-ai-web`
> **Generated:** 2026-08-17 · **Phase:** fe-backlog (M5)
> **Sources:** SPEC-01..08, generator-config.yml, backend-api-cross-link.md, ADR-005/006, M3.5 Gate1 (8 gaps)

---

## Resumo do projeto

DASH é o painel de cuidado: a superfície padrão que o cuidador abre e em menos de 2 segundos sabe que Márcio está bem — StatusCard verde, dispositivo online, link rápido para o histórico de hoje. É também o host do outlet global para alarmes de emergência (EVNT) e o centro de notificações rotineiras.

O módulo opera como landing padrão (`/`), composição root do shell chrome, e declara a região `dash.global-interrupt` que EVNT ocupa com overlays de emergência. Toda escrita (calibração, confirmação de evento) é write-through via BFF `dash-service` (porta 8090); DASH não persiste nada. A honestidade do sinal `calm` — derivado de EVNT `activeEvent`, nunca do aggregate — é o invariante de segurança mais crítico do produto.

A execução cobre 36 issues (30 funcionais + 6 integração) organizadas em 9 ondas, com 8 coverage gaps do Gate1 fechados explicitamente.

---

## Mapa de ondas P1-P9

### P1 — Foundation (2d estimado)

**Meta:** Contrato, módulo lazy e shell registration prontos. DASH monta em `/`.

| Issue | Título | Dia |
|---|---|---|
| FE-DASH-001 | dash.contract.ts (MODULE_KEY, menus, permissions, feature-flags, descriptor) | D1 |
| FE-DASH-002 | DashModule + dash.routes.ts (3 rotas: /entrada, /, /notificacoes) | D1 |
| FE-DASH-003 | dash-descriptor.ts (fallback estático) | D1 |
| FE-DASH-004 | Shell: module-descriptors.ts + layout chromeless /entrada | D2 |

**DoD P1:** `npx nx build neuroassist-ai-web` PASS; navegar para `/` monta DASH; `/entrada` renderiza sem chrome.

---

### P2 — Data Access (2.5d estimado)

**Meta:** Camada HTTP + facades + SSE client operacionais com mocks.

| Issue | Título | Dia |
|---|---|---|
| FE-DASH-005 | DashApiService (9 métodos + DTOs em shared/contracts) | D3 |
| FE-DASH-006 | DashHomeFacade (signals summary/state/calm + mapper) | D3-D4 |
| FE-DASH-007 | NotificationsFacade (signals items/state/filter/unreadCount + markRead/acknowledge) | D4 |
| FE-DASH-008 | DashStreamClient (SSE + heartbeat + reconnect + long-poll fallback) | D4-D5 |

**DoD P2:** Todos os tests de `dash-data-access` PASS; `calm` reage corretamente ao activeEvent mockado.

---

### P3 — Feature Libs (3.5d estimado)

**Meta:** Todas as superfícies visuais implementadas; Home e Notification Center funcionais com mocks.

| Issue | Título | Dia |
|---|---|---|
| FE-DASH-009 | EntradaPageComponent + BrandHeroComponent + EnterPanelCtaComponent (chromeless) | D5 |
| FE-DASH-010 | HomeStatusPageComponent (smart container) | D5-D6 |
| FE-DASH-011 | StatusCardComponent (calm/loading/error kinds + retry + viewHistory) | D6 |
| FE-DASH-012 | PatientHeaderComponent + QuickLinkListComponent | D6 |
| FE-DASH-013 | NotificationCenterPageComponent (smart container) | D7 |
| FE-DASH-014 | NotificationFilterBarComponent + NotificationListComponent + NotificationListItemComponent | D7 |
| FE-DASH-015 | EmptyOrErrorStateComponent (inline, nunca bloqueia Home — G5) | D7-D8 |

**DoD P3:** MVP visual: Home exibe StatusCard nos 3 kinds; Notification Center lista, filtra e acknowledge.

---

### P4 — Hub Integrations (2d estimado)

**Meta:** Cross-module wirings e write-throughs prontos; calm honesty validado.

| Issue | Título | Dia |
|---|---|---|
| INT-FE-DASH-001 | Wire activeEvent (EVNT) → DashHomeFacade.calm via ACTIVE_EVENT_TOKEN | D8 |
| INT-FE-DASH-002 | Wire DeviceBadge (EDGE) → PatientHeaderComponent via DEVICE_BADGE_TOKEN | D8 |
| INT-FE-DASH-003 | ShellOutletRegistry: declarar região dash.global-interrupt | D9 |
| INT-FE-DASH-004 | Write-through calibração → PAC (PUT /panel/.../calibration) | D9 |
| INT-FE-DASH-005 | Write-through confirm event → EVNT (POST /panel/events/.../confirm) | D9 |
| INT-FE-DASH-006 | HIST quick-link via shell router (scope:dash não importa scope:hist) | D9 |

**DoD P4:** Lint boundary sem violações; `calm` reage ao EVNT real; outlet host presente no DOM.

---

### P5 — Guards (0.5d estimado)

| Issue | Título | Dia |
|---|---|---|
| FE-DASH-016 | authGuard (/, /notificacoes → redirect /entrada com returnUrl) | D10 |
| FE-DASH-017 | roleGuard (Onboarding nav-item oculto para cuidadora — G1) | D10 |

**DoD P5:** Não autenticado em `/` → redirect `/entrada`; cuidadora não vê Onboarding.

---

### P6 — Shell Integration + Navigation (1.5d estimado)

| Issue | Título | Dia |
|---|---|---|
| FE-DASH-018 | DashShellIntegrationService (registerWithShell, bindActiveEvent, bindDeviceBadge, releaseOutletRegion) | D10 |
| FE-DASH-019 | dash.bootstrap.ts (init: SSE open + summary prime; destroy: SSE close + region release — G6/G7) | D11 |
| FE-DASH-020 | libs/dash/navigation (BreadcrumbItem[] para Status e Notificações) | D11 |

**DoD P6:** Bootstrap idempotente; breadcrumb correto em /notificacoes; destroy fecha SSE e libera outlet.

---

### P7 — UX Polish / Coverage Gaps (0.5d estimado)

| Issue | Gap | Dia |
|---|---|---|
| FE-DASH-021 | StatusCard loading state skeleton (G4) | D11 |
| FE-DASH-022 | Banner 403 forbidden-scope inline (G8) | D11 |

**DoD P7:** Loading state sem "está bem"; 403 mostra banner genérico sem revelar existência do paciente.

---

### P8 — Observability (0.5d estimado)

| Issue | Título | Dia |
|---|---|---|
| FE-DASH-023 | Telemetria: panel_viewed, sse_connected, sse_fallback_activated, erros | D12 |

**DoD P8:** Eventos emitidos sem PII; correlationId em eventos de erro.

---

### P9 — e2e + Hardening (3d estimado)

| Issue | Gaps cobertos | Dia |
|---|---|---|
| FE-DASH-024 | home-status calm + role-hide nav (G1) + /notificacoes (G2) | D12-D13 |
| FE-DASH-025 | chromeless /entrada (G3) | D13 |
| FE-DASH-026 | notification delivery: SSE + lista + acknowledge (G5) | D13 |
| FE-DASH-027 | calibration write-through | D14 |
| FE-DASH-028 | bootstrap register nav+outlet (G6) | D14 |
| FE-DASH-029 | destroy SSE + region release (G7) | D14 |
| FE-DASH-030 | 403 banner + 503 retry + SSE fallback (G8) | D14-D15 |

**DoD P9:** Todos os 8 coverage gaps G1..G8 fechados; `npx nx e2e dash-e2e` PASS.

---

## Caminho crítico

```
D1: FE-DASH-001 → FE-DASH-002 → FE-DASH-003 → FE-DASH-004 [P1 done]
D3: FE-DASH-005 (DashApiService)
D4: FE-DASH-006 (DashHomeFacade) + FE-DASH-008 (DashStreamClient)
D5: FE-DASH-007 (NotificationsFacade) → FE-DASH-009/010 (pages)
D6: FE-DASH-011/012 (ui)
D7: FE-DASH-013..015 (notifications ui + EmptyOrError G5)
D8: INT-FE-DASH-001 (calm wiring — CRÍTICO)
D9: INT-FE-DASH-003 (outlet region) + INT-FE-DASH-004/005 (write-throughs)
D10: FE-DASH-016/017 (guards) + FE-DASH-018 (shell integration service)
D11: FE-DASH-019 (bootstrap/destroy G6/G7) + FE-DASH-020/021/022 (polish gaps)
D12: FE-DASH-023 (observability)
D12-D15: FE-DASH-024..030 (e2e cobrindo G1..G8)
```

**Bloqueio externo crítico:** INT-FE-DASH-001 requer que EVNT FE exporte `activeEvent` signal; INT-FE-DASH-002 requer que EDGE FE exporte `deviceBadge` signal; INT-FE-DASH-003 requer que `ShellOutletRegistry` esteja implementado em `@neuroassist/shared/ui-components`.

---

## Riscos top-3

| Risco | Impacto | Mitigação |
|---|---|---|
| **R1 — calm honesty**: activeEvent não disponível (EVNT FE não scaffoldado ainda) | CRÍTICO — StatusCard pode mostrar "está bem" falso | Providenciar token mockado com `signal(null)` por default; bloquear merge até EVNT FE expor o signal real |
| **R2 — SSE no browser**: EventSource não aceita headers custom (JWT via header) | ALTO — autenticação SSE pode falhar em produção | Alinhar com BE DASH-005 sobre mecanismo (cookie ou query param); decidir antes de P2 |
| **R3 — ShellOutletRegistry não estabilizado**: interface de `@neuroassist/shared/ui-components` pode mudar | MÉDIO — FE-DASH-018/019/INT-FE-DASH-003 bloqueados | Usar interface provisória; stub no teste; stabilizar em P4 com o time de plataforma |

---

## Definition of Done por marco

| Marco | Critério |
|---|---|
| **P1 done** | `nx build neuroassist-ai-web` PASS; `/` monta DASH; `/entrada` chromeless |
| **P3 done — MVP visual** | Home StatusCard + Notification Center funcionais com mocks; todos os unit tests PASS |
| **P4 done — calm honest** | `calm()` reage ao EVNT real; lint boundary sem violações; outlet region declarada |
| **P6 done — shell-stable** | Bootstrap + destroy cycle testados; breadcrumb correto; SSE abre e fecha corretamente |
| **P9 done — shippable** | `nx e2e dash-e2e` PASS; todos os 8 coverage gaps G1..G8 fechados; `nx build` PASS |

---

## Approval criteria por marco

- **P3 done = MVP shippable em dev?** Sim, com mocks de EVNT/EDGE — Home e Notifications funcionam, mas `calm` pode não ser real.
- **P4 done = integração real possível?** Sim — requer EVNT FE + EDGE FE scaffoldados.
- **P9 done = pronto para QA?** Sim — todos os gaps fechados, e2e verde.
