# 06 — Risks, Gaps, Assumptions — DASH (Frontend)

## 1. Risks

- **UX — false calm.** The gravest risk: Home shows "Márcio está bem" while an emergency is
  active. Mitigation: `calm` derives strictly from EVNT's confirmed `activeEvent`; loading/
  absent summary never defaults to green; e2e asserts pre-emption before calm render.
- **Composition coupling.** DASH is the composition root; wiring EVNT/EDGE signals at
  `scope:web` risks accidental `scope:dash → scope:evnt` imports (boundary violation).
  Mitigation: ADR-005 + nx-boundaries lint gate; signals injected via tokens at the shell.
- **Contract instability.** `GET /dashboard/summary` aggregate depends on EVNT/EDGE/HIST
  data shapes that are still being specced; a change ripples into DASH's VM. Mitigation:
  server-side aggregation (`dash-service`) absorbs the fan-out; DASH depends on one stable
  aggregate contract, not N module contracts.
- **Performance.** The shell mounts on every panel load; a heavy summary or eager module
  descriptors could hurt the ≤ 2 s target. Mitigation: lazy modules, thin summary payload,
  OnPush, no eager cross-module HTTP.
- **Outlet-host timing.** If DASH's global outlet region is not mounted before EVNT tries to
  register, the alarm could fail to appear. Mitigation: `ShellOutletRegistry` contract with
  deterministic host-first ordering (INT-FE-EVNT-001); e2e covers alarm-on-cold-load.
- **Browser compat.** Reference caregivers on modest devices/browsers; keep the shell light.

## 2. Known gaps

- The exact **`/dashboard/summary` aggregate shape** (which fields come from `dash-service`
  vs. composed client-side) is not frozen — to be pinned in Specs against BE dash-service.
- **`dash-service` backend does not yet exist** (no BE vision/service dir); DASH-FE Specs may
  precede it. Interim: front against a documented aggregate contract / mock.
- The **notification-center source** (dispatch list via `dash-service` vs. `evnt-service`) is
  a routing decision to settle in Specs.
- **Shell chrome ownership** — the layout lives in `scope:web`; the split of what DASH
  "owns" (contracts, descriptor, home) vs. what the web app renders needs a crisp Spec.

## 3. Assumptions to validate

- **Two-click reach** holds for the MVP screen set (6 telas) with a flat sidebar.
- **Session already valid** when Entrada's CTA is pressed (auth handled by ACS/`auth-session`);
  Entrada does not implement sign-in itself.
- **One active patient per caregiver session** in the MVP (topbar shows a single patient);
  clinic multi-patient switching is post-MVP.
- **Routine dispatch volume** is low enough that a simple recent-list (no pagination) suffices
  for the notification center in the MVP.
- **Reference devices** are desktop/tablet browsers in a home setting; no offline-first PWA.

## 4. Critical dependencies

- **EVNT-FE** — `activeEvent` signal + alarm/toast components registered into DASH's outlet
  (`ShellOutletRegistry`, INT-FE-EVNT-001); DASH's calm truth depends on it.
- **EDGE-FE** — device-status data for the topbar/Home badge.
- **HIST-FE** — target of the "Ver histórico de hoje" quick-link.
- **ACS / `auth-session` / `access-decision`** — session, role, RBAC gating.
- **`dash-service` (BE)** — the home summary + notification aggregate endpoints.
- **`@neuroassist/shared/*`** — `access-http-base`, `ui-components` (`ShellOutletRegistry`),
  `ui-tokens`, `ui-feedback`, `contracts`, `error-model`, `empresa-context`.
