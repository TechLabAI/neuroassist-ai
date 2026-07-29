# SPEC-07 — Error Handling — EVNT (Frontend)

> Module: **EVNT — Eventos e Alarmes** · Mapping: BE `ProblemDetail` (RFC 7807) →
> `@neuroassist/shared/error-model` `AppError` → user-facing pt-BR message.
> Source of truth for codes: BE api-contract §6. Acceptance criteria use EARS notation.

## 1. `ProblemDetail` → `AppError` → user message

Only caregiver-facing endpoints appear here (stream, active, get, confirmation, query). The ingress-only
codes (`/errors/gest-rejected`, `/errors/forbidden-biometric-payload`) are edge/device-facing and never
reach this UI.

| BE `type` | HTTP | AppError kind | Surface | User message (pt-BR) |
|---|---|---|---|---|
| `/errors/feedback-terminal` | 409 | `Conflict` | inline (overlay/detail) | "Já confirmado por {nome} às {hora}." (success-equivalent — overlay clears) |
| `/errors/event-not-found` | 404 | `NotFound` | route redirect | (redirect to `/eventos`; toast "Evento não encontrado.") |
| `/errors/tenant-mismatch` | 403 | `Forbidden` | route redirect | (redirect to Home; no existence leak) |
| `/errors/forbidden` | 403 | `Forbidden` | inline/banner | "Você não é responsável por este paciente." |
| `/errors/unauthenticated` | 401 | `Unauthenticated` | shell | (shared interceptor → re-auth; session expired) |
| `/errors/validation` | 400 | `Validation` | inline | "Não foi possível registrar. Verifique e tente de novo." |
| `/errors/upstream-unavailable` | 502/504 | `Upstream` | inline retry (alarm) / banner | "Não foi possível registrar agora. Tentar de novo." |
| (network / timeout, no body) | — | `Network` | inline retry (alarm) / degraded banner | "Sem conexão. Tentando novamente…" |

## 2. Confirmation-specific rules (safety)

- **Confirm failure keeps the alarm active.** On any error from `POST /events/{id}/confirmation`, the
  `activeEvent` stays non-null; the overlay shows an **inline** message + retry. Never a toast that can
  be missed, never a silent clear.
- **409 feedback-terminal is success-equivalent.** Another caregiver already confirmed; show
  "Já confirmado por {nome} às {hora}" and clear the overlay (the safety goal — someone answered — is met).

## 3. Live-channel errors

- Stream error / drop → `deliveryMode='degraded'`, start polling, show a **banner**
  (`@neuroassist/shared/ui-feedback`) "Modo degradado — verificando a cada {n}s". Recover to `live`
  silently on reconnect.
- Routine delivery error → **silent** retry on next poll (routine is best-effort; never alarm-level noise).

## 4. Retry strategy

| Operation | Retry | Notes |
|---|---|---|
| `confirm` | manual (user-driven inline retry) | never auto-retried silently — the caregiver must know it is unconfirmed |
| live stream | auto reconnect with backoff + `Last-Event-ID` resume | resumes via `evento.seq` |
| `active` poll | fixed interval `pollIntervalMs` while degraded | stops on stream recovery |
| `query` (history) | manual reload | non-critical read |

## 5. Toast vs banner vs modal (criteria)

| Surface | Use for | Examples |
|---|---|---|
| **Modal / overlay (interrupt)** | Emergency alarm + its confirm errors | AlarmOverlay + inline confirm retry |
| **Banner (persistent, non-blocking)** | Ongoing degraded state | "Modo degradado — verificando a cada {n}s" |
| **Toast (transient)** | Low-stakes confirmations / routine dismissal | "Notificação dispensada." |
| **Inline (in-context text)** | Field/action-level failures | confirm failure, not-found on detail |

Rule: anything on the **safety path (emergency confirmation)** is inline/interrupt — **never** a toast.

## Criterios de aceite (EARS)

- IF the backend responds `409 /errors/feedback-terminal` to a confirmation, THEN THE SYSTEM SHALL show
  "Já confirmado por {nome} às {hora}" inline and clear the overlay.
- IF the backend responds `404 /errors/event-not-found`, THEN THE SYSTEM SHALL redirect to `/eventos` and
  show a toast "Evento não encontrado".
- IF the backend responds `403 /errors/tenant-mismatch`, THEN THE SYSTEM SHALL redirect away without
  revealing whether the event exists.
- IF the backend responds `403 /errors/forbidden`, THEN THE SYSTEM SHALL show "Você não é responsável por
  este paciente" and SHALL NOT clear an active alarm.
- IF a confirmation fails with `502`/`504` or a network error, THEN THE SYSTEM SHALL keep the alarm active
  and show an inline retry, never a toast and never a silent clear.
- IF the live stream drops, THEN THE SYSTEM SHALL show a degraded banner and poll until it reconnects.
- IF a routine delivery fails, THEN THE SYSTEM SHALL retry silently on the next poll without raising an
  alarm-level surface.
