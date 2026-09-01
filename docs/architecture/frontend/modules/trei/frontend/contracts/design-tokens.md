# Design Tokens — TREI (Frontend)

> TREI consumes the **project design system** tokens from
> `docs/architecture/frontend/standards/design-system-tokens.md` via
> `@neuroassist/shared/ui-theme` (Tailwind utilities + CSS-variable tokens). **No** ad-hoc
> colors/spacing, **no** SCSS token files. TREI has **no BA prototype** (D-493 does not
> apply), so it composes the existing **caregiver-shell** chrome + shared UI primitives —
> it introduces no new theme (doc 02 §5 token-parity Learning). This doc records only
> TREI-specific usage intent, not new tokens.

## 1. Source of truth

- Tokens: `standards/design-system-tokens.md` → `@neuroassist/shared/ui-theme` (`theme.css`).
- TREI must reference **only** tokens present in the app `theme.css`; it must never invent
  its own theme (Learning: FE promote token-parity finding, doc 02 §5).
- Fonts: derived from `--font-*` tokens. Icons: Material Symbols (`model_training` menu icon).

## 2. Caregiver-shell aesthetic (inherited, not redefined)

TREI is a **dense caregiver admin surface** (unlike CONV's calm patient shell): standard
sidebar + topbar + breadcrumb owned by `neuroassist-ai-web`; TREI adds only the Tela 08
content. It reuses the same chrome as GEST/DASH/EVNT (doc 04 §1).

## 3. TREI state → token usage (semantic, not new tokens)

| UI state | Token intent |
|---|---|
| authoring banner "não é o app do paciente" (INV-6) | `--color-warning` / distinct authoring surface treatment — must read as *authoring*, structurally distinct from patient runtime (TECH-007) |
| "gerado por IA" disclosure (ADR-017) | `--color-info` chip on assistant turns / proposal cards |
| assistant turn bubble | `--color-surface-alt`; caregiver turn bubble `--color-surface` |
| assistant-thinking indicator | `--color-muted` animated affordance |
| cuidadora confirmada / aguardando (indicator) | `--color-success` / `--color-muted` |
| paciente confirmado / aguardando (read-only) | `--color-success` / `--color-muted`, rendered non-actionable (INV-1) |
| clinical-flag proposal (approve blocked) | `--color-warning` on the card + disabled Aprovar (SEC-002, api-contract §2.2) |
| conversation error banner (`llm-unavailable`) | `--color-danger` with Retry affordance (SPEC-07) |
| subEstado chip (rascunho / aguardando_aprovacao / ativo) | neutral → `--color-muted` → `--color-success` progression |

## 4. Scaffold obligation (carried to `/fe-architect-scaffold`)

TREI is **scaffolded**, not promoted (no design-reference clone). The scaffold MUST:
1. compose the caregiver-shell theme already vendorized for the WEB app (do not introduce a
   TREI-local theme);
2. run the token-parity gate — fail if any TREI utility class references a token absent from
   the app `theme.css`;
3. keep the "não é o app do paciente" affordance visually unmistakable from the patient
   runtime surface (a hard product invariant, not a style preference).

Recorded here for traceability; it is a scaffold obligation, not a specs-phase artifact.
