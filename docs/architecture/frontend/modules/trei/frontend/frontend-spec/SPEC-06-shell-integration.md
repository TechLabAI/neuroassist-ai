# SPEC-06 — Shell Integration — TREI (Frontend)

> How TREI registers into the **caregiver shell** (`neuroassist-ai-web`) without owning its
> chrome (ADR-006, doc 04 §2/§7). Covers the module descriptor, the
> `TreiShellIntegrationService`, and the `trei.bootstrap.ts` lifecycle.

## 1. `TREI_MODULE_DESCRIPTOR` (fallback registration)

The caregiver shell composes tools at `scope:web` via `config/module-descriptors.ts`,
which imports `TREI_MODULE_DESCRIPTOR` from `@neuroassist/trei` (the barrel, SPEC-05 §4).
This is the **static** registration path: the shell learns TREI's menu item, permission
map, feature flags, and lazy routes from the descriptor, with **no** compile-time
dependency from `scope:web` into TREI internals beyond the descriptor and the lazy
`loadChildren`.

```ts
// apps/neuroassist-ai-web/src/app/config/module-descriptors.ts (scope:web)
import { TREI_MODULE_DESCRIPTOR } from '@neuroassist/trei';
export const MODULE_DESCRIPTORS = [ /* …GEST, DASH, EVNT… */, TREI_MODULE_DESCRIPTOR ];
```

The descriptor is the **fallback** the shell renders even before the lazy chunk loads: the
"Treinamento" sidebar item appears from the static descriptor; the routes hydrate on first
navigation (`loadChildren`).

## 2. `TreiShellIntegrationService` (`libs/trei/integration`)

A `scope:trei` service that performs the **runtime** wiring the descriptor cannot express
statically:

- **Menu gating** — subscribes to the shared feature-flag + access signals and reports
  whether the "Treinamento" item should be visible (`treinamentoEnabled` AND scope
  `treinamento.sessao.conduzir`), so the shell hides the item reactively without TREI
  reaching into the shell's sidebar component.
- **Breadcrumb feed** — publishes the TREI breadcrumb subtree (`trei.nav.config.ts`,
  `libs/trei/navigation`) into the shared `BreadcrumbItem[]` contract; the shell renders it.
- **AI-authoring affordance registration** — declares, to the shell, that this subtree
  carries the "não é o app do paciente" banner requirement (TECH-007), so the affordance is
  consistently present on every TREI route (INV-6).
- **No shell chrome ownership** — the service **registers into** the caregiver shell; it
  never renders the sidebar/topbar itself (ADR-006). It touches only the shared contracts
  (`@neuroassist/shared/*`), never a `scope:web` component.

## 3. `trei.bootstrap.ts` — init / destroy lifecycle

```ts
export const treiBootstrap = {
  init(ctx: ToolBootstrapContext): void {
    // 1. register breadcrumb + nav config with the shell (via TreiShellIntegrationService)
    // 2. seed defaultLocale from ctx.session.locale (constrained to {pt,en}, ADR-018)
    // 3. read GET /health once (best-effort) to surface embedModelId / degradedCapabilities
    //    (authoring readiness) — never blocks activation
    // 4. assert NO Anthropic/ElevenLabs/ES client is present in the graph (INV-2 dev guard)
  },
  destroy(): void {
    // 1. end any active training session (status → 'ended'); PRESERVE pending drafts (INV-4)
    // 2. clear in-memory conversation turns
    // 3. deregister breadcrumb/nav contributions from the shell
    // (no media/camera/AI SDK to release — TREI opens none)
  },
};
```

- **init** is idempotent and cheap: it wires contracts and does one best-effort health read;
  it must not block route activation on `trei-service` reachability (a degraded backend
  still lets the caregiver see the surface and any existing drafts).
- **destroy** guarantees the **no-loss** invariant (INV-4): tearing down TREI never discards
  drafts — they live in `trei-service`; only the browser working state is cleared.

## 4. Open scaffold decisions (carried to `/fe-architect-scaffold`)

- Exact caregiver-shell sidebar ordering / icon token — design-system decision at scaffold.
- Whether `ProposalReviewPageComponent` is a distinct route or an inline panel of the
  session page (SPEC-01 lists both; the route exists for deep-linking) — final composition
  at scaffold.
- The Angular i18n mechanism for UI chrome (`@angular/localize` vs transloco) — deferred
  specs/scaffold decision (ADR-018 §5); this SPEC fixes only that the labels are i18n keys.

## 5. Criterios de aceite (EARS)

- THE SYSTEM SHALL register TREI into the caregiver shell exclusively through `TREI_MODULE_DESCRIPTOR` at `scope:web` and SHALL NOT import any caregiver-shell component into `scope:trei`.
- WHILE `treinamentoEnabled` is off or the caregiver lacks scope `treinamento.sessao.conduzir`, THE SYSTEM SHALL hide the "Treinamento" sidebar item.
- WHEN the TREI subtree is entered, THE SYSTEM SHALL publish its breadcrumb into the shared breadcrumb contract and SHALL render the authoring affordance on every TREI route.
- WHEN TREI bootstraps, THE SYSTEM SHALL seed the default locale from the caregiver session within `{pt,en}` and SHALL read health best-effort without blocking route activation.
- WHEN the TREI subtree is destroyed, THE SYSTEM SHALL end the active session and clear in-memory turns and SHALL NOT discard any pending draft proposal.
- THE SYSTEM SHALL carry no Anthropic, ElevenLabs, or Elasticsearch client in the TREI dependency graph.
