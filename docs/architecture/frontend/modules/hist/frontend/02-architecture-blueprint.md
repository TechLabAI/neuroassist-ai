# 02 — Architecture Blueprint — HIST (Frontend)

## 1. System boundary

HIST-FE is a **client feature** of the backend `hist-service` (the audit read
model) via `@neuroassist/shared/access-http-base` → API Gateway, always
tenant-scoped by the `X-Empresa-Id` interceptor (schema-per-empresa). It holds
**no persistence and no urgency logic**: it queries the immutable ledger, renders
the period-filtered timeline, and posts a single feedback verdict per event.

The browser is a **review and audit surface**. It does not talk to the edge agent
or the live event channel (that is GEST/EVNT); it reads the historical `Evento`
records — already resolved with their command label and urgency — from the read
model, newest first, paginated.

## 2. External actors and systems

| Actor / system | Role | Channel |
|---|---|---|
| Caregiver / coordinator (authenticated) | Reviews history, marks feedback | Browser UI |
| API Gateway (GWY) → `hist-service` | Serves the paginated, period-filtered ledger query | REST via `access-http-base` |
| `evnt-service` (via GWY) | Owns the feedback verdict transition (`real`/`falso_positivo`) | REST (feedback write — see doc 06 open contract) |
| PAC module | `Comando` label + urgência for each item (resolved server-side or joined) | shared read model |
| ACS module | Caregiver ↔ patient vínculo (tenant scope, "quem confirmou") | shared session/state |
| Shell (`neuroassist-ai-web`) / Home | Hosts the "Ver histórico de hoje" quick link | `scope:web` composition |

## 3. End-to-end user flows

### Golden path — review the day + mark feedback (wireframe §5, fluxo 4.4/4.7)

```
[caregiver on Home] --clicks "Ver histórico de hoje"-->
        |
        v
   route /historico?period=hoje   (Shell lazy-loads @neuroassist/hist)
        |
        v
   HistoryFacade.load(period=hoje)
   state: loading  ->  GET /history?period=hoje&page=0
        |
        v
   state: loaded   ->  HistoryTimelineComponent renders EventListItem[]
   "14h32 — 'água'  · [rotina]   Você confirmou às 14h33   [ Foi real? ]"
   "08h05 — 'Sapo'  · [rotina]   Você confirmou às 08h06   [ ✓ Marcado real ]"
        |
   caregiver presses [ Foi real? ] on an item, chooses real | falso_positivo
        v
   HistoryFacade.markFeedback(id, verdict)   (per-item marking state)
        |
        v
   POST /events/{id}/feedback  (non-optimistic)  -> item re-renders terminal
   "[ ✓ Marcado real ]"   (action now disabled)
```

### Filter change

```
   caregiver taps [ Semana ] / [ Mês ]
        v
   HistoryFacade.setPeriod(week|month) -> reload; list resets to page 0
```

Alternative / edge cases:

```
empty period            -> state: empty; "Nenhum evento neste período — ótimo sinal"
fetch fails (network)   -> state: error; inline "Não foi possível carregar o
                           histórico. Tentar de novo." with retry; no blank screen
feedback write fails    -> item stays nao_avaliado; inline "Não foi possível
                           registrar. Tentar de novo."; never shown as marked
already terminal (409)  -> "Já marcado por <nome> às <hora>"; item reconciles to
                           the backend verdict (no client override)
large history           -> paginate / virtual scroll; "carregar mais" or infinite
                           scroll beyond N items (SPEC to confirm threshold)
```

## 4. Internal boundaries (module slicing per module-pattern.md)

HIST follows the canonical NeuroAssist module slicing. The entry is a **library**
(ADR-006), not an app folder.

```
libs/hist/shell-entry/        (type:app, scope:hist)   # @neuroassist/hist — lazy entry
└── src/lib/
    ├── hist.module.ts                 # the module's only NgModule
    ├── hist.contract.ts               # MODULE_KEY + metadata + permissions + flags
    ├── hist.bootstrap.ts              # init/destroy lifecycle
    ├── hist.routes.ts                 # HIST_ROUTES[] (timeline; optional detail)
    ├── hist.permission-map.ts
    ├── hist.feature-flags.ts          # e.g. defaultPeriod, pageSize, virtualScroll on/off
    ├── hist.nav.config.ts
    └── hist.descriptor.ts             # HIST_MODULE_DESCRIPTOR (consumed by Shell)

apps/hist/                    (type:app, scope:hist)   # thin host: loads @neuroassist/hist
apps/hist-e2e/                (type:e2e)               # Playwright specs

libs/hist/
├── data-access/  (type:data-access) # HistClient (REST) + HistoryFacade (signals)
├── feature-timeline/ (type:feature) # HistoryTimelineComponent (list + FilterTabs) + feedback
├── access/       (type:domain)      # hist-specific guard (caregiver linked to patient)
├── integration/  (type:domain)      # HistShellIntegrationService (menu/route registration)
├── navigation/   (type:domain)      # breadcrumb/menu state ("Histórico")
├── ui/ (opcional, type:ui)          # local presentational bits (EventListItem, UrgencyBadge,
│                                     #   FilterTabs, EmptyState, ListSkeleton)
└── styles/ (opcional)               # Tailwind utilities + tokens (no SCSS tokens)
```

**Composition rule.** HIST is a **routed feature**, not a global outlet. It exposes
its route (`/historico`) and menu descriptor via `HistShellIntegrationService`; the
Shell (`scope:web`) registers it in `config/module-descriptors.ts` and wires the
Home "Ver histórico de hoje" deep-link. `scope:hist` never imports another module;
cross-module reuse (e.g. sharing the `UrgencyBadge` treatment with EVNT) lives in
`@neuroassist/shared`, not in a hist↔evnt dependency.

## 5. Cross-cutting concerns

- **Auth / entitlement** — the timeline route is guarded by the shared
  `AccessDecisionService`; a hist-local guard additionally requires the caregiver to
  be linked to the patient's tenant (ACS vínculo).
- **Tenancy** — every backend call carries `X-Empresa-Id` via the shared
  `access-http-base` interceptor (schema-per-empresa); the history never crosses tenants.
- **Observability** — history queried, filter changed, feedback posted and error
  transitions emitted via the shared observability lib (no PII beyond ids; no clinical
  content, no image/landmark data).
- **Design tokens** — urgency colors, `success`/`danger`, `brand`, and typography from
  `@neuroassist/shared/ui-tokens`; the urgency badge treatment is token-driven, and
  reused consistently with EVNT's alarm palette (via shared, not a cross-module import).
- **i18n** — UI copy pt-BR (human-facing, e.g. "Nenhum evento neste período — ótimo
  sinal", "Foi real?"); code/identifiers en-US. Timestamps localized to the tenant locale.
- **Accessibility / safety** — list items are semantic list rows; the feedback action
  is a clearly labelled button with large target; terminal state is conveyed by text +
  icon, not color alone; the empty state is reassuring, not alarming.
