# SPEC-05 — Module Contract — GEST (`gest.contract.ts`)

> Lives in `libs/gest/shell-entry/src/lib/gest.contract.ts`. Consumed by the
> `scope:web` shell for descriptor fallback, permission gating and feature-flag
> resolution. GEST is an **embedded** feature — deliberately **not** a nav item.

## 1. `MODULE_KEY`

```ts
export const MODULE_KEY = 'gest' as const;
```

## 2. `MODULE_MENU_METADATA`

```ts
export const MODULE_MENU_METADATA = {
  key: MODULE_KEY,
  label: 'Teste ao vivo do gesto',   // pt-BR (human-facing)
  icon: 'camera',
  showInNav: false,                   // embedded surface; composed by PAC/onboarding at scope:web
  order: null,
} as const;
```

Rationale: GEST has no standalone product route (SPEC-01). `showInNav: false`
prevents a dead top-level menu entry; the surface is reached only through host
pages.

## 3. `MODULE_PERMISSION_MAP`

```ts
export const MODULE_PERMISSION_MAP = {
  'gest.live-test.run': ['CUIDADORA_FAMILIAR', 'CONDUCTOR'],  // run a live test
} as const;
```

| Permission | Justification |
|---|---|
| `gest.live-test.run` | Guards the "Testar agora" action. The family caregiver (`CUIDADORA_FAMILIAR`) runs it during calibração; the conductor runs it during onboarding. Read-heavy, non-destructive (no persisted state), so a single run-permission suffices. No `gest.manage`-style permission on the FE — catalog/profile mutations are BE/PAC surfaces, not this feature. |

The FE permission maps to the BE `gest.read` scope (api-contract §6.2); the live
test never invokes a `gest.manage` endpoint.

## 4. `MODULE_FEATURE_FLAGS`

```ts
export const MODULE_FEATURE_FLAGS = {
  edgeLiveBridge: { default: true },   // true ⇒ real localhost edge stream; false ⇒ forced degraded/mock
} as const;
```

| Flag | Default | Justification |
|---|---|---|
| `edgeLiveBridge` | `on` | Toggles the real edge stream vs. the degraded/mock path (ADR-005). Default on so production uses the real engine (no drift). Turning it **off** forces honest degraded mode everywhere — useful for demos, cross-device topology, or when the pilot edge agent is not yet deployed. It can never produce a fake "recognized" (SPEC-03/04 invariant holds regardless of flag). |

## 5. RBAC of UI (behavioral)

The run control is gated on `gest.live-test.run`. When the current session lacks
it, the control is hidden (not merely disabled) to avoid implying a capability the
caregiver does not have.

## Critérios de aceite (EARS)

- THE SYSTEM SHALL expose `MODULE_KEY = 'gest'` and SHALL register the module with `showInNav: false`.
- WHILE the current user lacks the `gest.live-test.run` permission, THE SYSTEM SHALL hide the "Testar agora" control.
- WHERE the `edgeLiveBridge` feature flag is off, THE SYSTEM SHALL run the live test in degraded mode using the mock stream.
- IF the `edgeLiveBridge` flag is on but the edge agent is unreachable, THEN THE SYSTEM SHALL fall back to degraded mode and SHALL NOT report a real recognition.
