# SPEC-05 — Tool Contract — TREI (Frontend)

> Contents of `libs/trei/shell-entry/src/lib/trei.contract.ts` and siblings
> (`trei.permission-map.ts`, `trei.feature-flags.ts`, `trei.nav.config.ts`). TREI is a
> **caregiver-shell** tool: unlike CONV it **is** registered into the caregiver menu
> (`neuroassist-ai-web` sidebar) as **"Treinamento"** (doc 04 §7). Permission scopes are
> taken **verbatim from the authoritative `api-contract.md` §2** — the FE vision's
> provisional names (`trei.treinamento.iniciar/propor/aprovar`) are superseded here.

## 1. `TOOL_KEY` and menu metadata

```ts
export const TREI_TOOL_KEY = 'trei' as const;

export const TREI_MENU_METADATA: ToolMenuMetadata = {
  key: TREI_TOOL_KEY,
  labelKey: 'trei.menu.title',        // i18n key → "Treinamento" (EN/PT), ADR-018
  route: '/treinamento/sessao',
  surface: 'caregiver',               // caregiver shell sidebar item
  registerInAdminShell: true,         // structural: caregiver shell registers TREI (unlike CONV)
  icon: 'model_training',             // Material Symbols
  badgeKey: 'trei.menu.authoringBadge', // "não é o app do paciente" affordance hint (TECH-007)
};
```

**Justification (`surface: 'caregiver'` / `registerInAdminShell: true`):** doc 04 §7 and
wireframe §7 — TREI is the caregiver's authoring surface and belongs in the caregiver
sidebar, the exact opposite of CONV (patient-only). The AI-authoring badge is a
**screen-level** affordance (SPEC-02 `TreiAuthoringBannerComponent`), surfaced as a menu
hint but enforced on the route subtree, not the menu.

## 2. `TOOL_PERMISSION_MAP` (verbatim from api-contract §2)

```ts
export const TREI_PERMISSION_MAP = {
  conduzir:  'treinamento.sessao.conduzir',      // start session, inspect node, read publication status
  propor:    'treinamento.no.propor',            // request LLM proposal + materialize/edit a draft node
  aprovar:   'treinamento.no.aprovar.cuidadora', // caregiver-side approval — the ONLY approval TREI can write
  descartar: 'treinamento.no.descartar',         // discard a rascunho / aguardando_aprovacao draft
  // NOTE: 'treinamento.no.aprovar.paciente' is DELIBERATELY ABSENT — patient side is CONV's (INV-1, ADR-007)
} as const;
```

| Permission | Justification | UI effect |
|---|---|---|
| `treinamento.sessao.conduzir` | api-contract §2.1/§2.6/§2.8 — the session/read authority; without it the caregiver cannot open the training surface | route guard `treiGuard`; whole TREI surface hidden if absent |
| `treinamento.no.propor` | api-contract §2.2/§2.3 — request a proposal and materialize/edit a draft; distinct from conducting a session | route guard `treiProporGuard`; the compose/edit affordances are hidden/disabled if absent (review stays visible) |
| `treinamento.no.aprovar.cuidadora` | api-contract §2.4 — the caregiver approval; a distinct scope so a reviewer without approval authority can still review | disables the `Aprovar` button on every card while absent |
| `treinamento.no.descartar` | api-contract §2.7 — discard authority, separate from approval | hides/disables the `Descartar` action while absent |

**No patient authority and no `confirmadoPaciente` write is ever declared here** (INV-1). A
token carrying the patient approval scope on a TREI approval endpoint is rejected by the BE
with `403 forbidden-scope` (api-contract §2.5/§3); the FE never issues the patient call.

## 3. `TOOL_FEATURE_FLAGS`

```ts
export const TREI_FEATURE_FLAGS = {
  treinamentoEnabled: true,          // module kill-switch; OFF → menu item + routes hidden
  streamingConversation: false,      // ADR-005: OFF → request/response turns; ON → SSE (needs BE capability)
  voiceInputEnabled: false,          // deferred caregiver voice input (vision doc 01 §5 out-of-scope); default OFF
  defaultLocale: 'pt' as Locale,     // seeded from caregiver JWT locale; within {pt,en} (ADR-018)
} as const;
```

| Flag | Justification | Fallback when off |
|---|---|---|
| `treinamentoEnabled` | a new retrofit module needs a kill-switch to roll out safely | menu item and `/treinamento` routes are not registered |
| `streamingConversation` | BE SSE capability is not yet contracted (ADR-005, doc 06 G-2) | request/response turns with an `assistant-thinking` state |
| `voiceInputEnabled` | caregiver voice input is explicitly deferred from MVP (doc 01 §5) | text-only composer |
| `defaultLocale` | bilingual EN/PT with independent per-locale content (ADR-018) | JWT `locale`, constrained to `{pt,en}` |

Flags are read via the shared feature-flag service (mirrors the EVNT/GEST
`FeatureFlagService` pattern, doc 04 §7).

## 4. Barrel export (`TREI_MODULE_DESCRIPTOR`)

```ts
export const TREI_MODULE_DESCRIPTOR = {
  key: TREI_TOOL_KEY, menu: TREI_MENU_METADATA,
  permissions: TREI_PERMISSION_MAP, flags: TREI_FEATURE_FLAGS,
  routes: TREI_ROUTES,
};
```

Consumed at `scope:web` by `config/module-descriptors.ts` (SPEC-06) — the only place the
caregiver shell learns about TREI.

## 5. Criterios de aceite (EARS)

- THE SYSTEM SHALL register TREI into the caregiver shell menu as "Treinamento" and SHALL NOT register it into any patient shell.
- WHILE the caregiver session lacks scope `treinamento.sessao.conduzir`, THE SYSTEM SHALL hide the entire TREI surface and SHALL NOT render the training session.
- WHILE the caregiver session lacks scope `treinamento.no.propor`, THE SYSTEM SHALL disable the proposal compose and edit affordances while keeping the review surface visible.
- WHILE the caregiver session lacks scope `treinamento.no.aprovar.cuidadora`, THE SYSTEM SHALL disable the Aprovar action on every proposal card.
- WHILE the caregiver session lacks scope `treinamento.no.descartar`, THE SYSTEM SHALL disable the Descartar action on every proposal card.
- THE SYSTEM SHALL declare no patient approval scope and SHALL provide no control that writes the patient side of any proposal.
- WHILE `treinamentoEnabled` is off, THE SYSTEM SHALL hide the Treinamento menu item and SHALL NOT register the TREI routes.
- WHERE `streamingConversation` is off, THE SYSTEM SHALL drive the conversation via request/response turns with an assistant-thinking state.
