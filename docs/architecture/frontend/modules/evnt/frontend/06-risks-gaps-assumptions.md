# 06 — Risks, Gaps, Assumptions — EVNT (Frontend)

## 1. Risks

| # | Risk | Impact | Mitigation |
|---|---|---|---|
| R1 | **Missed emergency alarm** — live channel drops and the caregiver never sees the alarm | Safety-critical (Márcio cannot get help) | Polling fallback + degraded banner (ADR-005); hydrate `activeEvent` on load; e2e asserts no lost alarm |
| R2 | **Latency of delivery** — with messaging disabled, delivery is REST/SSE + edge outbox; a delayed sync delays the alarm | Delayed help | SSE for push; short poll interval as fallback; surface `lastEventAt` so staleness is visible; treat as a measured success signal |
| R3 | **Overlay does not pre-empt** — a route or modal traps focus above the AlarmOutlet | Alarm hidden behind a screen | AlarmOutlet at shell top-level, `role="alertdialog"`, focus trap to confirm; e2e opens each screen then fires an emergency |
| R4 | **Urgency mis-render** — `rotina` shown full-bleed or `emergencia` shown discreet | Alarm fatigue or missed emergency | Urgency read strictly from `Comando.urgencia`; unit tests on the VM mapping |
| R5 | **Double confirmation in clinic** — two caregivers confirm concurrently | Confusing state | Idempotent confirm; 409 → "Já confirmado por …"; first-wins |
| R6 | **Accessibility / attention** — caregiver is non-technical, possibly stressed | Confirmation errors | Single large high-contrast action; no competing controls; pt-BR plain language |
| R7 | **Bundle / repaint** — alarm must appear instantly | Slow time-to-alarm | OnPush, minimal alarm feature bundle, lazy but pre-warmed outlet |

## 2. Known gaps

- BE `evnt-service` contract not yet generated (this brief is FE-authored; BE will
  sync). The live-channel endpoint shape (`/events/stream` SSE vs `/events/active`
  poll) must be confirmed in Specs against the BE api-contract.
- Multi-caregiver routing policy (broadcast to all linked vs on-shift only) is an
  **ACS + EVNT** decision not yet fixed; FE assumes "all linked caregivers of the
  tenant receive it" until specified.
- Replacement policy when a second emergency arrives before the first is confirmed
  (queue vs replace) is BE-owned; FE must not silently drop an unconfirmed alarm.

## 3. Assumptions to validate

- Volume of events per patient/day is low (single patient, ~9 events/day in mock) —
  no virtualization needed for the routine feed at MVP scale.
- The caregiver keeps the web panel open (kiosk-like) during monitoring — SSE is
  viable; if the tab is backgrounded, polling-on-focus covers the gap.
- Target device is a desktop/tablet browser in the home; full-bleed overlay
  assumes a reasonable viewport (validate on the smallest supported device).
- `confirmadoPor` is derived server-side from the authenticated session (FE sends
  no actor id).

## 4. Critical dependencies

- **BE**: `evnt-service` endpoints — active event, live stream, confirm; urgency
  resolved from `Comando` (PAC) before delivery.
- **GEST**: detection contract feeding event generation (upstream, not FE-facing).
- **ACS**: caregiver ↔ patient vínculo for routing and the evnt-local guard.
- **EDGE**: device online state (composed at Home; gates whether events arrive).
- **Shared libs**: `access-http-base` (+ `X-Empresa-Id`), `ui-tokens`
  (`--color-alarm`), `ui-feedback`, `error-model`, `observability`, `access`.
- **Shell** (`neuroassist-ai-web`): hosts the global AlarmOutlet (ADR-005/ADR-006).
