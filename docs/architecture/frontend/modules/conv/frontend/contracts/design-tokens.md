# Design Tokens — CONV (Frontend)

> CONV consumes the **project design system** tokens from
> `docs/architecture/frontend/standards/design-system-tokens.md` via
> `@neuroassist/shared/ui-theme` (Tailwind utilities + CSS-variable tokens). **No** ad-hoc
> colors/spacing, **no** SCSS token files. This doc records only CONV-specific usage
> intent, not new tokens.

## 1. Source of truth

- Tokens: `standards/design-system-tokens.md` → `@neuroassist/shared/ui-theme` (`theme.css`).
- Fonts: derived from the `--font-*` tokens (`--font-display`, `--font-body`, `--font-mono`).
- Icons: Material Symbols (prototype baseline).

## 2. Patient-shell aesthetic (from the BA wireframe / prototype)

The patient surface is a **calm, full-bleed** shell tuned for reduced motor control:
- **Large hit targets** — speech-tree node buttons are oversized; generous spacing tokens.
- **High contrast** — text/background contrast at or above the wireframe's calm palette.
- **No dense chrome** — topbar only, no sidebar, minimal breadcrumb.
- **Prominent text fallback** — the `text-only` playback state renders the phrase full-bleed at display scale (`--font-display`), the largest legible treatment (ADR-007 voice-never-silent).

## 3. CONV state → token usage (semantic, not new tokens)

| UI state | Token intent |
|---|---|
| selected node | `--color-accent` ring/background (single selection, ADR-005) |
| pending-locale node (disabled) | reduced-emphasis / `--color-muted` + disabled treatment; visibly esmaecido |
| gesture channel on / off (indicator) | `--color-success` / `--color-muted` |
| web-speech fallback badge | `--color-warning` (discreet — degradation, not error) |
| terminal session-ended screen | neutral full-screen surface, no alarm coloring |

## 4. M6 promote risk (Learning-layer signal — carried to scaffold/promote)

`fe-architect-promote:app-theme:design-system-not-vendorized` (measured on this project's
WEB app: 369 utility-class uses resolved only in the prototype theme, 46 only in the
Estágio-0 theme, 86 in neither). CONV's patient shell is a **new shell** — especially
exposed. At M6 the promote MUST:
1. copy `design-system/{theme.css,fonts.html}` over the generated fallback (never leave the GENERATED FALLBACK when a design-reference exists);
2. derive `fonts.html` `<link>`s from the `--font-*` tokens (not a blind copy);
3. run a token-parity gate (fail if any referenced utility token is absent from the app `theme.css`).

Do **not** trust a green build as proof of correct patient-shell UI (build stays green
when CSS is absent). This is a **promote/scaffold** obligation recorded here for traceability;
it is not a specs-phase artifact.
