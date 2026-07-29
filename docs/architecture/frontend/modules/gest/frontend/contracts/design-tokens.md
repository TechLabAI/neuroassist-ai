# Design Tokens — GEST (Frontend)

> GEST consumes shared tokens from `@neuroassist/shared/ui-tokens`; it defines
> **no** ad-hoc colors (doc 02 §5). Tokens listed here are the ones the live-test
> surface relies on. Values are owned by the shared token layer, not by GEST.

## 1. Consumed tokens

| Token | Role in GEST | Used by |
|---|---|---|
| `--color-brand` | primary action ("Testar agora") | `LiveTestControlsComponent` |
| `--color-success` | `recognized` verdict badge | `TestStateBadgeComponent` |
| `--color-danger` | `camera-error` panel / not-recognized emphasis | `TestStateBadgeComponent`, error panel |
| `--color-alarm` | emergency-command context accent (composite/urgência = emergência) | `CompositeStepIndicatorComponent` |
| `--color-warning` | degraded banner | `DegradedBannerComponent` |
| `--color-neutral-*` | preview frame chrome, idle/testing states | `PreviewFrameComponent`, badge |
| typography scale (`--font-*`) | human-language verdict copy (never raw numbers) | all presentational |
| spacing / radius tokens | preview frame + control layout | feature layout |

## 2. Accessibility & UX constraints

- Verdict is **never** conveyed by color alone — every state pairs a color token
  with a pt-BR text label and an icon (WCAG, non-technical persona R3 doc 06).
- No raw confidence/threshold values are ever styled onto the screen (doc 03 §5).
- The degraded banner uses `--color-warning` and is visible for the whole
  duration `degraded === true` (honest degraded, ADR-005).

## 3. No SCSS token layer

Styling uses Tailwind utilities bound to the CSS custom properties above; there
is no SCSS token layer in this project (doc 04 §1).
