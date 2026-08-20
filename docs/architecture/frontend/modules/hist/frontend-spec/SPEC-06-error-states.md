# SPEC-06 — Error States — HIST (`@neuroassist/hist`)

> Backend errors are RFC 7807 `application/problem+json` (api-contract §4). Mapped to `AppError` via
> `@neuroassist/shared/error-model`. Rendering surfaces: `@neuroassist/shared/ui-feedback`. Traces vision doc 05 §5.

## 1. ProblemDetail → AppError → user-facing message

| BE `type` | HTTP | Surface | User-facing message (pt-BR) | Retry |
|---|---|---|---|---|
| `/errors/validation` | 400 | inline card (list) | "Não foi possível carregar o histórico. Tentar de novo." | yes |
| `/errors/incomplete-subject-filter` | 400 | inline card | (prevented client-side; if seen) "Filtro inválido — recarregando." | auto |
| `/errors/invalid-time-window` | 400 | inline card | "Período inválido." | reset period |
| `/errors/unauthorized` | 401 | route redirect | (redirect to `/login`) | n/a |
| `/errors/forbidden` | 403 | access message | "Você não tem acesso a este histórico." | no |
| `/errors/tenant-mismatch` | 403 | access message | "Sessão inválida para esta empresa." | re-auth |
| `/errors/not-found` | 404 | inline (detail) | "Registro não encontrado." | back |
| `/errors/scope-unavailable` | 503 | inline card (fail-closed) | "Não foi possível confirmar seu acesso agora. Tentar de novo." | yes |
| (feedback) `409` already-terminal | 409 | item reconcile | "Já marcado por {nome} às {hora}." | no (reconcile) |
| network / timeout | — | inline card | "Não foi possível carregar o histórico. Tentar de novo." | yes |
| 5xx | 500–504 | inline card | "Tivemos um problema. Tentar de novo." | yes (backoff) |

Feedback-specific write failures (`/errors/validation`, network, 5xx on the POST) surface as the **item** `mark-error` state, not the whole-screen error: "Não foi possível registrar. Tentar de novo."; the item remains `nao_avaliado`.

## 2. Surface selection criterion (toast vs banner vs modal vs inline)

| Situation | Surface |
|---|---|
| List load failure | **inline card** in the list region (with retry) — never blank/spinner |
| Per-item feedback failure | **inline** under the item (marking `mark-error`) |
| Access denied / vínculo lost | **banner/redirect** (guard) or access message if mid-session |
| Chain-verification `broken` | **badge + banner** for coordinators (not a modal; informational, actionable via audit escalation) |
| Transient success confirmation | **toast** (feedback recorded) — optional, non-blocking |

No modal dialogs in the MVP error flows: the timeline stays navigable at all times.

## 3. Retry strategy

- **List load**: manual retry via the inline card's action; re-runs the last `load(period)`. No silent auto-retry loop (avoids stampede on a degraded backend).
- **503 scope-unavailable** (fail-closed, api-contract §7): manual retry; the FE never widens scope it cannot verify.
- **Feedback write**: idempotent on the backend; retry re-POSTs the same verdict safely. Per-item, manual.
- **5xx**: single manual retry with visible state; no exponential background retry in MVP.

## 4. Non-optimistic guarantee

The feedback verdict is safety-relevant and auditable. On **any** feedback write failure the item is **never** shown as marked — it stays `nao_avaliado` (doc 05 §4). This is the deliberate anti-pattern-avoidance: an optimistic "marked real" that later fails would corrupt trust in the audit trail.

## Criterios de aceite (EARS)

- IF the backend responds `/errors/validation` (400) or a network error to a timeline query, THEN THE SYSTEM SHALL render an inline error card with a retry action and SHALL NOT show a blank screen or an infinite spinner.
- IF the backend responds `/errors/forbidden` (403) or `/errors/tenant-mismatch`, THEN THE SYSTEM SHALL show an access message rather than an empty list.
- IF the backend responds `/errors/scope-unavailable` (503), THEN THE SYSTEM SHALL fail closed, show a retryable message, and SHALL NOT widen the caregiver's scope.
- IF a feedback write fails with a network or `5xx` error, THEN THE SYSTEM SHALL set the item to `mark-error`, keep it `nao_avaliado`, and offer a per-item retry.
- IF a feedback write returns `409` already-terminal, THEN THE SYSTEM SHALL reconcile the item to the backend verdict and show "Já marcado por {nome} às {hora}" without a client override.
- IF the backend responds `/errors/unauthorized` (401), THEN THE SYSTEM SHALL redirect to `/login` rather than render the timeline.
- THE SYSTEM SHALL surface the response `correlationId` on every error card for support traceability.
