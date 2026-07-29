# Design System Tokens — Tailwind v4 (CSS-first) + oklch

> Standard for NeuroAssist AI. D-381/D-385. The design tokens are the
> **machine face** of the design system delivered by Estágio 0 in
> `design/design-system/theme.css` and vendored into the generated app as
> `frontend/apps/neuroassist-ai-web/src/theme.css` (a generic amber fallback
> is emitted when no design system is delivered).

## 1. Why CSS-first

Tailwind v4 reads design tokens from a `@theme { ... }` block and generates the
utility classes from them — there is **no classic `tailwind.config.js`**. The
engine runs as a PostCSS plugin (`@tailwindcss/postcss`, see `postcss.config.js`).
The app's entry stylesheet (`styles.css`) does:

```css
@import "tailwindcss";
@import "./theme.css";
```

Tokens are written in **oklch** (`oklch(L% C H / alpha)`) for perceptually
consistent light/dark. **Dark is the default**; `[data-theme="light"]` overrides
the same token names with light values (the sidebar stays dark in both modes).

## 2. Token contract (canonical names)

Components MUST style via Tailwind utilities bound to these tokens (e.g.
`bg-surface`, `text-ink`, `rounded-card`, `shadow-pop`) — never hardcoded hex/rgb.

| Group | Tokens |
|---|---|
| Surfaces | `--color-base` `--color-surface` `--color-surface-2` `--color-elevated` |
| Text | `--color-ink` `--color-ink-soft` `--color-ink-faint` |
| Borders/fields | `--color-line` `--color-line-strong` `--color-field` `--color-field-line` |
| Brand ramp | `--color-brand` `--color-brand-strong` `--color-brand-ink` `--color-brand-subtle` |
| Accent | `--color-accent` |
| Semantics (×4) | `--color-{success,warning,danger,info}` + `-bg` + `-ink` each |
| AI | `--color-ai` `--color-ai-bg` `--color-ai-ink` (purple — intelligence) |
| Sidebar (always dark) | `--color-sidebar` `-hover` `-ink` `-muted` `-active` `-active-ink` `-accent` `-line` |
| Typography | `--font-display` `--font-body` `--font-sans`; `--text-2xs..4xl`; `--weight-*`; `--leading-*` |
| Radius | `--radius-xs..2xl` `--radius-card` |
| Shadow | `--shadow-xs..lg` `--shadow-card` `--shadow-pop` |
| Motion | `--duration-{fast,normal,slow}` `--ease-default` |

Fonts are **DS tokens** (`--font-display` / `--font-body`); the project's
`fonts.html` (Google Fonts links) is injected into `index.html`. Default =
Montserrat (display) + Nunito Sans (body); each project overrides in its
`theme.css`.

## 3. Rules

- **Single source of truth:** the project's `theme.css`. Adding/renaming a token
  happens there, not scattered across components.
- **No SCSS token layer** (the old 3-layer SCSS approach is retired, D-382). No
  `var(--xxx)` inside component SCSS, no `$token-*` SCSS variables.
- **No hardcoded colour/spacing** in components — use utilities + tokens. The
  `arch-conformance.py` FE gate flags violations.
- **Components:** standalone + OnPush; primitives from `@angular/cdk` (no
  `@angular/material`); icons via Material Symbols (icon font).

## 4. Where it comes from / how it's adopted

Estágio 0 authors `theme.css` (+ `styleguide.html` human face, `fonts.html`). The
`/new-project` P5 phase vendors `design/` to `frontend/design-reference/` and
copies `design-system/theme.css` over the generic fallback. The `fe-architect-*`
pipeline restructures the seed screens into the canonical Nx modules, preserving
the Tailwind markup and these tokens.
