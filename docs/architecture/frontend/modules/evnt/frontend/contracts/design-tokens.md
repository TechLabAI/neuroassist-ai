# Design Tokens — EVNT (Frontend)

> Module: **EVNT — Eventos e Alarmes** · Tokens from `@neuroassist/shared/ui-tokens`.
> The full-bleed alarm treatment is **token-driven, never ad-hoc colors** (blueprint §5).

## 1. Tokens consumed

| Token | Usage in EVNT |
|---|---|
| `--color-alarm` | Full-bleed background of `AlarmOverlayComponent` (emergency) |
| `--color-danger` | Emergency urgency badge, error inline text |
| `--color-success` | "Confirmado" state, success-equivalent 409 message |
| `--color-brand` | Routine notice accent, primary confirm button |
| `--color-warning` | Degraded-mode banner |
| `--space-*`, `--radius-*` | Layout of overlay, notices, feed cards |
| `--font-size-alarm` / `--font-weight-bold` | Large, high-contrast alarm copy |

## 2. Urgency → token mapping

| `urgencia` | Tier | Surface | Tokens |
|---|---|---|---|
| `emergencia` | `full_screen_alarm` | AlarmOverlay (interrupt) | `--color-alarm` bg, `--color-danger` badge, high-contrast text |
| `rotina` | `discreet` | RoutineNotice (banner/toast) | `--color-brand` accent, standard type scale |

## 3. Accessibility constraints (safety)

- Emergency alarm: `role="alertdialog"`, focus trapped to the confirm button, high contrast
  (WCAG AA+ against `--color-alarm`), a single large target for the confirm action.
- No color is the sole signal — urgency also carries an icon + text label ("Emergência" / "Rotina").
