# 05 — State and Data Flow — EDGE (Frontend)

## 1. State topology

| State | Where | Kind |
|---|---|---|
| Active patient's device (`DispositivoEdge`) | `DeviceFacade` (`libs/edge/data-access`) | `signal<DispositivoEdge \| null>` |
| Clock tick (for freshness computation) | shared clock signal | `signal<number>` |
| Derived connectivity (`online/offline/stale/unknown`) | `DeviceFacade` | `computed()` over device + clock |
| Pairing flow state | `PairingFacade` (`libs/edge/data-access`) | `signal<PairingVM>` |
| Connectivity read-model (Shell-facing) | `libs/edge/integration` | readonly signal (derived) |
| Active `Tenant`/`Paciente` context | `@hub/access` (global) | consumed, not owned |

No global store; no NgRx. EDGE state is local to its facades; the only thing that
leaves the module is the **readonly connectivity read-model** the Shell composes.

## 2. Data access pattern

- `EdgeHttpService` (`libs/edge/data-access`) uses `HttpClient` via
  `@hub/access/http-base` (gateway). Endpoints (contract to be firmed in Specs):
  - `POST /devices/pairing-codes` → issue a pairing code (+ TTL).
  - `GET /devices/{id}` (or by patient) → device status/version/last-sync (poll).
  - `POST /devices/{id}/repair` / `DELETE /devices/{id}` → re-pair / unpair.
- Facades call the service and **emit signals**; components read signals. No
  `BehaviorSubject` hand-rolling. The polling `timer` is internal to `DeviceFacade`.

## 3. Cache strategy

- **Default: poll fresh.** Connectivity is inherently time-sensitive — the device
  read-model is refreshed on a `timer` cadence (`edge.pollingEnabled` /
  interval flag), not cached across navigations.
- Polling **pauses** when the tab is hidden (Page Visibility) and resumes on focus,
  to avoid pointless gateway traffic while keeping the chip honest when visible.
- No caching of pairing codes beyond their TTL; an expired code is discarded.

## 4. Optimistic updates

- **None for connectivity** — connectivity is *observed*, never asserted, so there
  is nothing to be optimistic about (the whole ADR-005 point).
- **Pairing** is not optimistic either: the flow only reports `paired` after a real
  first heartbeat. Re-pair/unpair actions reflect the server result, not a hopeful
  local flip; on failure the previous state is restored and surfaced.

## 5. Error handling

- BE `ProblemDetail` → `AppError` via `@hub/error-model`; messages via
  `@hub/shared/ui-feedback`.
- **Poll failure** → derived connectivity becomes `unknown` (degraded, honest),
  with silent retry on the next tick; never assumed `online`.
- **Pairing-code request failure / 5xx** → retryable toast; the flow stays `idle`
  or moves to `error` with a clear "try again".
- **Code expired (410/expired signal)** → `expired` state, prompt to re-request.
- **Unpair/re-pair conflict (409)** → surface "device state changed, reloading" and
  refresh the device read-model rather than forcing a stale write.

## 6. Breadcrumbs and navigation state

- `libs/edge/navigation` feeds `BreadcrumbItem[]`:
  `Dispositivo` → `Parear` / `Detalhes`.
- The always-visible **connectivity chip** is not a breadcrumb — it is the Shell
  topbar consuming EDGE's connectivity read-model on every authenticated route
  (Wireframe §3.2.B). Breadcrumbs cover only EDGE's own routes.
