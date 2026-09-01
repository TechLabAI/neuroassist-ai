# SPEC-07 — Error Handling — TREI (Frontend)

> Maps `trei-service` RFC-7807 `ProblemDetail` (api-contract §3) → `AppError`
> (`@neuroassist/shared/error-model`) → caregiver-facing message via the shared feedback
> layer. LLM/upstream failures are **first-class, retriable** conversation errors — never
> blank states (doc 05 §5). No error path ever exposes an Anthropic / ElevenLabs / ES
> internal to the caregiver (those are `trei-service` details).

## 1. ProblemDetail → AppError → user-facing message

Each error is `application/problem+json` with `type`, `title`, `status`, `detail`,
`instance`, `correlationId` (api-contract §3). The data-access adapter maps `type`
(suffix under `https://neuroassist.ai/problems/`) to an `AppError` code; the feature renders
the message through the shared feedback layer (toast / banner / modal — §3).

## 2. Failure-code table (BE `type` → FE handling)

| BE `type` (suffix) | HTTP | FE surface | Caregiver-facing message (pt-BR; EN via i18n) | Retriable? |
|---|---|---|---|---|
| `llm-unavailable` | 503 | **conversation banner** | "Falha ao falar com o assistente — tente novamente." | yes (retry turn) |
| `invalid-request` | 400 | inline **field** error | "Revise a frase (não pode ficar vazia / muito longa)." | no (fix input) |
| `no-biometric-ingress` | 400 | **modal** (dev/guard) | "Conteúdo inválido para treinamento." (should never happen from FE — TREI sends text only) | no |
| `unauthenticated` | 401 | shared re-auth | (handled by `@neuroassist/shared/access` — redirect to caregiver login) | n/a |
| `session-revoked` | 401 | shared, fail-closed | "Sua sessão foi encerrada. Entre novamente." | no (re-login) |
| `forbidden-scope` | 403 | **toast** | "Você não tem permissão para esta ação." | no |
| `patient-not-found` | 404 | **banner** (start) | "Paciente não encontrado nesta empresa." | no |
| `node-not-found` | 404 | **toast** + refetch | "Esta proposta não está mais disponível." (refetch list) | no |
| `already-published` | 409 | **toast** + refetch | "Esta frase já foi publicada e não pode ser descartada." | no |
| `gate-not-satisfied` | 409 | (not FE-actuated) | — publication gate is BE/patient-side; TREI observes, never triggers | n/a |
| `tts-unavailable` | 503 | (not FE-facing) | — synthesis is a post-approval BE side effect; surfaced only via publication status | n/a |

Notes:
- `gate-not-satisfied` and `tts-unavailable` are **not reachable from a TREI-FE action** —
  TREI never triggers publication (INV-1/ADR-007); they appear only as read-model state in
  `GET /nos/{noId}/publicacao` and are rendered informationally, never as an error the
  caregiver must fix.
- **Approval has no 409** (api-contract §2.4 idempotent) — reconciled in SPEC-04 §6; a
  duplicate caregiver approval is a success, not a conflict.

## 3. Toast vs banner vs modal (criterion)

- **Conversation banner** (inline, above the composer) — for `llm-unavailable`: the failure
  is scoped to the conversation; existing drafted proposals stay visible and approvable
  (doc 02 §3, doc 05 §5). Carries a **Retry** action.
- **Toast** (transient) — for action-scoped, non-blocking failures (`forbidden-scope`,
  `node-not-found`, `already-published`): the surface stays usable.
- **Inline field error** — for `invalid-request` on the edit/compose affordance.
- **Modal** — only for a fail-closed stop (`session-revoked` handled by shared access; the
  dev-guard `no-biometric-ingress`).

## 4. Retry strategy

- **Conversation turn (`llm-unavailable` 503):** manual **Retry** re-issues the last
  `POST /sessoes/{sessaoId}/propostas` (idempotent enough — a new proposal turn); no
  automatic retry loop (avoids hammering a down Anthropic). Existing proposals untouched.
- **Approval optimistic failure:** roll back the optimistic flag, refetch `GET /nos/{noId}`,
  ask the caregiver to re-confirm (SPEC-04 §4). No silent overwrite.
- **Health read (`GET /health`):** best-effort; a failure degrades to "authoring may be
  unavailable" messaging, never a blocked surface (TREI is off CONV's runtime path — its
  outage never stops the patient's communication, api-contract §5).
- **Correlation:** every `AppError` carries the `correlationId` echoed by GWY
  (`X-Correlation-Id`, api-contract §1.1) for support/observability; it is logged, never
  shown as a raw id to the caregiver.

## 5. Isolation guarantee

No mapped message ever contains an Anthropic / ElevenLabs / ES detail: all upstream
failures collapse to a generic "assistant unavailable" / "publication pending" message
(api-contract §5, doc 05 §5). The caregiver sees a product-level message, never an
integration internal.

## 6. Criterios de aceite (EARS)

- IF the backend responds `llm-unavailable` (503), THEN THE SYSTEM SHALL show a retriable conversation banner and SHALL keep existing proposals visible and approvable.
- IF the backend responds `invalid-request` (400) on an edit, THEN THE SYSTEM SHALL show an inline field error and SHALL NOT submit the proposal.
- IF the backend responds `session-revoked` (401), THEN THE SYSTEM SHALL end the session fail-closed and SHALL route the caregiver to re-authentication.
- IF the backend responds `forbidden-scope` (403), THEN THE SYSTEM SHALL show a permission toast and SHALL NOT retry the action automatically.
- IF the backend responds `patient-not-found` (404) when starting a session, THEN THE SYSTEM SHALL show a start-surface banner and SHALL NOT create a session.
- IF the backend responds `node-not-found` (404) or `already-published` (409) on a draft action, THEN THE SYSTEM SHALL show a toast and SHALL refetch the affected node.
- IF a caregiver approval request fails after an optimistic update, THEN THE SYSTEM SHALL roll back the caregiver flag, refetch the node, and ask for re-confirmation.
- THE SYSTEM SHALL map every error to a product-level message and SHALL NOT expose any Anthropic, ElevenLabs, or Elasticsearch internal to the caregiver.
- THE SYSTEM SHALL attach the response `correlationId` to each mapped error for observability and SHALL NOT render it as a raw identifier to the caregiver.
