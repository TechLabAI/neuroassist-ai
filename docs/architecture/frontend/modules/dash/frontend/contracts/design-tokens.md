# Design Tokens — DASH (Frontend)

> Source: `@neuroassist/shared/ui-tokens` (the shared token set). DASH consumes tokens; it defines **no** new
> palette. FE 02 §5: the calm palette (green/sober) is DASH's default; `--color-alarm` is **reserved to EVNT**
> and never used by DASH.

## 1. Consumed token groups

| Group | Source token (shared) | DASH usage |
|---|---|---|
| Calm/health | `--color-status-ok` (green) | StatusCard "está bem", Status nav icon |
| Neutral surface | `--color-surface`, `--color-surface-muted` | shell chrome, cards |
| Text | `--color-text`, `--color-text-muted` | labels, "última verificação" |
| Device online | `--color-status-ok` | device badge online |
| Device offline/unknown | `--color-status-warn` (amber) | device badge amber (offline/unknown — NOT alarm) |
| Notification discreet | `--color-info` | discreet notification chip |
| Error surface | `--color-danger-soft` | inline error banner (summary/list) |
| Focus/interaction | `--focus-ring`, `--radius-md`, `--space-*` | a11y + layout rhythm |

## 2. Reserved — NOT used by DASH

- `--color-alarm` (full-bleed emergency red) — **EVNT only**. DASH's StatusCard and device badge never
  render alarm-red; an offline device is amber, not an alarm (FE 02 §3 edge cases). The only red interrupt in
  the panel is EVNT's `AlarmOverlayComponent` in the global outlet.

## 3. Typography & spacing

Inherited from `@neuroassist/shared/ui-tokens` (no local overrides). Home prioritizes a large, legible
StatusCard (time-to-answer ≤2s, two-click reach — FE 01 success criteria). No SCSS token files in DASH;
utilities via the shared Tailwind token layer.

## 4. Accessibility

- Calm/offline states must not rely on color alone — pair the green ✓ / amber dot with text ("está bem",
  "dispositivo desconhecido").
- The notification center list items expose `aria-label` with tier + timestamp; the discreet chip is not the
  sole signal.
- DASH raises no `role="alertdialog"` — that is EVNT's alarm overlay (a11y ownership stays with the emergency
  surface).
