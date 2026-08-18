# SPEC-07 — Error Handling — DASH (Frontend)

> Sources: BE api-contract §7 (RFC 7807 error model) + §2.1 degraded sections, FE 05 §5.
> `ProblemDetail` (BE) → `AppError` (FE) via `@neuroassist/shared/error-model`. User-facing strings are
> pt-BR, non-technical; no `type` codes are surfaced to the caregiver.

## 1. `ProblemDetail → AppError` mapping

The shared HTTP interceptor parses `application/problem+json` into `AppError { code, status, title, detail,
correlationId }`. DASH facades translate `AppError.code` (the `type` suffix) into a user-facing outcome. The
`correlationId` is attached to every AppError for support/observability (never shown prominently).

## 2. Failure-code → FE message table

| BE `type` suffix | Status | Where it arises | FE surface | User message (pt-BR) |
|---|---|---|---|---|
| `#unauthenticated` | 401 | any `/panel/**` with invalid/expired JWT | **redirect** to `/entrada` | "Sua sessão expirou. Entre novamente." (via login screen) |
| `#forbidden-scope` | 403 | patient/dispatch outside caregiver scope | **banner** (inline) | "Você não tem acesso a este paciente." |
| `#not-found` | 404 | command/event not found within scope | **toast** | "Item não encontrado. Ele pode ter sido removido." |
| `#validation` | 422 | malformed calibration/confirmation payload | **inline field error** | "Verifique os valores informados." |
| `#upstream-unavailable` | 503 | required upstream (PAC/HIST/ACS) down | **banner + retry** | "Não foi possível carregar agora. Tente novamente." |
| `#upstream-rejected` | *relayed* | PAC/EVNT rejected a write-through | **toast** (relayed status) | relays owner's message (e.g. calibration conflict) |
| *summary degradedSections* | 200 | EVNT/EDGE section degraded (soft) | **inline degraded chip** | device "—/desconhecido"; latestEvent omitted, patient still shown |

## 3. Toast vs banner vs modal — criterion

- **Toast** — transient, non-blocking, self-dismiss: `#not-found`, `#upstream-rejected` write echoes,
  successful acknowledgements. Never used for a state the caregiver must act on.
- **Banner (inline)** — persistent within the affected surface, blocks that surface only: `#forbidden-scope`,
  `#upstream-unavailable` (with retry). The shell chrome stays intact.
- **Modal** — reserved. DASH raises **no** modal for errors. The only full-screen interrupt in the panel is
  EVNT's `AlarmOverlayComponent` (an emergency, not an error) — DASH never competes with it (FE 02 §5).
- **Redirect** — `#unauthenticated` only (session boundary, ACS-owned login).

## 4. Retry strategy

- **Home summary (`GET /panel/summary`)** — manual retry via StatusCard button on `503`/network; no silent
  auto-retry loop (avoids hammering a degraded upstream). One-shot on user action.
- **SSE stream** — bounded auto-reconnect with jittered backoff on drop/heartbeat-timeout (transport, not a
  domain error); after N attempts, fall back to `/panel/notifications/poll`.
- **Write-throughs (calibration/confirm/acknowledge)** — no auto-retry; the caregiver re-submits. Acknowledge
  is idempotent on `dispatchId`, so a re-submit is safe.
- **Degraded sections** — never retried automatically; the next Home entry re-fetches fresh (FE 05 §3).

## 5. Safety invariant (calm honesty)

Device offline or a degraded EDGE/EVNT section is **not** an alarm — Home stays calm. Conversely, DASH never
suppresses an emergency because of an error: `calm` derives from EVNT's live `activeEvent` (fail-safe toward
**showing** an alarm, FE 05 §5). An error in the summary aggregate must never render a false "está bem".

## Critérios de aceite (EARS)

- IF the backend responds `#unauthenticated` (401) on any `/panel/**` call, THEN THE SYSTEM SHALL redirect
  the caregiver to `/entrada` to re-authenticate.
- IF the backend responds `#forbidden-scope` (403), THEN THE SYSTEM SHALL show an inline banner "Você não
  tem acesso a este paciente." and SHALL NOT reveal whether the patient exists.
- IF the backend responds `#upstream-unavailable` (503) for the Home summary, THEN THE SYSTEM SHALL show a
  banner with a retry affordance and keep the shell chrome intact.
- IF the backend responds `#validation` (422) on a calibration or confirmation write, THEN THE SYSTEM SHALL
  show an inline field error and SHALL NOT submit again automatically.
- IF a write-through is relayed as `#upstream-rejected`, THEN THE SYSTEM SHALL show a toast with the owner
  service's relayed message and SHALL NOT store the value locally.
- WHEN the summary response marks a section degraded, THE SYSTEM SHALL render the degraded chip
  ("—/desconhecido") and SHALL keep the Home calm unless EVNT reports a positive emergency.
- IF the SSE stream drops, THEN THE SYSTEM SHALL reconnect with bounded backoff and fall back to long-poll
  after exhausting retries.
