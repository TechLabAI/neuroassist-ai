# SPEC-05 — Tool Contract — CONV (Frontend)

> Contents of `libs/conv/shell-entry/src/lib/conv.contract.ts` and siblings
> (`conv.permission-map.ts`, `conv.feature-flags.ts`, `conv.nav.config.ts`). CONV is a
> **patient-shell** tool: unlike admin modules it is **not** registered into the caregiver
> menu (`neuroassist-ai-web` sidebar). It also carries the `clientEventId` idempotency
> convention for `POST /select`.

## 1. `TOOL_KEY` and menu metadata

```ts
export const CONV_TOOL_KEY = 'conv' as const;

export const CONV_MENU_METADATA: ToolMenuMetadata = {
  key: CONV_TOOL_KEY,
  labelKey: 'conv.menu.title',        // i18n key (EN/PT), ADR-018
  route: '/paciente/arvore',
  surface: 'patient-only',            // NOT surfaced in the caregiver sidebar
  registerInAdminShell: false,        // structural: admin module registry ignores CONV
  icon: 'record_voice_over',          // Material Symbols
};
```

**Justification (`surface: 'patient-only'` / `registerInAdminShell: false`):** vision doc
04 §7 and ADR-012/014 — CONV must never appear in the caregiver shell; the patient shell
hosts it. Registering it into the admin menu would leak a patient surface into the
caregiver navigation.

## 2. `TOOL_PERMISSION_MAP`

```ts
export const CONV_PERMISSION_MAP = {
  navegar:    'conv.arvore.navegar',   // load tree, search, resolve binding, read health
  selecionar: 'conv.no.selecionar',    // speak a frase node (POST /select)
} as const;
```

| Permission | Justification | UI effect |
|---|---|---|
| `conv.arvore.navegar` | api-contract §2.1/2.2/2.4 — the read/navigate scope; without it the patient cannot even load the tree | route guard `convNavegarGuard`; whole surface hidden if absent |
| `conv.no.selecionar` | api-contract §2.3 — the speak action is a distinct authority so navigation can be granted without selection | disables the `frase` "speak/activate" affordance while absent; tree still navigable |

**No caregiver/admin authority is ever declared here.** A token carrying admin authority
is rejected by the BE with `403` (api-contract §3) — the FE mirrors this by treating any
caregiver-scope presence as an invalid patient session (defense in depth).

## 3. `TOOL_FEATURE_FLAGS`

```ts
export const CONV_FEATURE_FLAGS = {
  semanticAssistEnabled: true,   // OFF or ES down → silent manual navigation (OPS-003)
  gestureChannelEnabled: true,   // OFF → touch-only; channel indicator shows gesture off (ADR-014)
  ttsCacheEnabled: true,         // OFF → Web Speech fallback path (OPS-004 / ADR-007)
  defaultLocale: 'pt' as Locale, // seeded from JWT `locale`; within localesSuportados
} as const;
```

| Flag | Justification | Fallback when off |
|---|---|---|
| `semanticAssistEnabled` | assist is an optimization, not a dependency; ES may be down (risk R, OPS-003) | manual tree navigation, no error |
| `gestureChannelEnabled` | gesture reliability on a phone is a known high risk (ADR-014); touch is always a full peer | touch-only, indicator shows gesture off |
| `ttsCacheEnabled` | audio may be absent/unavailable (OPS-004) | Web Speech, then prominent text (voice-never-silent) |
| `defaultLocale` | bilingual EN/PT with per-locale content (ADR-018); the session drives it | JWT `locale`, constrained to `localesSuportados` |

Flags are read via the shared feature-flag service (mirrors EVNT's `FeatureFlagService`).

## 4. Idempotency convention (`clientEventId`)

`POST /nodes/{id}/select` carries a client-minted `clientEventId` (UUID v4). The FE mints
it **once per activation intent** and reuses it on retry, so a network retry of the same
selection produces exactly one HIST audit fact (BE dedupes via `uq_conv_outbox_client_event`,
data-model §2.4). A new activation (even of the same node) mints a fresh id.

## 5. Barrel export (`CONV_MODULE_DESCRIPTOR`)

```ts
export const CONV_MODULE_DESCRIPTOR = {
  key: CONV_TOOL_KEY, menu: CONV_MENU_METADATA,
  permissions: CONV_PERMISSION_MAP, flags: CONV_FEATURE_FLAGS,
  routes: CONV_ROUTES,
};
```

## 6. Criterios de aceite (EARS)

- THE SYSTEM SHALL declare CONV as a patient-only tool and SHALL NOT register CONV into the caregiver admin shell menu.
- WHILE the patient session lacks scope `conv.arvore.navegar`, THE SYSTEM SHALL hide the entire CONV surface and SHALL NOT render the speech tree.
- WHILE the patient session lacks scope `conv.no.selecionar`, THE SYSTEM SHALL disable the `frase` speak affordance while keeping the tree navigable.
- IF the patient session token carries any caregiver or admin authority, THEN THE SYSTEM SHALL treat the session as invalid and SHALL exit the patient shell.
- WHILE `semanticAssistEnabled` is off, THE SYSTEM SHALL hide the assist affordance and SHALL keep manual navigation available.
- WHILE `gestureChannelEnabled` is off, THE SYSTEM SHALL keep touch fully operational and SHALL show the gesture channel as off in the indicator.
- WHILE `ttsCacheEnabled` is off, THE SYSTEM SHALL speak selected phrases via the Web Speech fallback path.
- WHEN two selections of the same node carry the same `clientEventId`, THE SYSTEM SHALL send that idempotency key so exactly one audit fact is recorded.
