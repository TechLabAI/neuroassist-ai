# SPEC-07 — Error Handling — CONV (Frontend)

> `ProblemDetail` (BE, RFC 7807) → `AppError` (`@neuroassist/shared/error-model`) →
> user-facing surface (`@neuroassist/shared/ui-feedback`). **Degradation is first-class,
> not an error** (OPS-003/004). Patient-facing copy is calm, short, jargon-free, sized for
> low motor control and possible cognitive load (mirrors GEST-FE human-language principle).
> **Cross-locale substitution is a safety violation and never happens** (api-contract §5).

## 1. `ProblemDetail → AppError` mapping (BE failure codes)

Each BE `type` suffix (api-contract §3) maps to one FE surface. Patient-facing messages
are i18n keys (EN/PT, ADR-018).

| BE `type` suffix | HTTP | FE surface | i18n message key | Copy (pt / en) |
|---|---|---|---|---|
| `content-pending-locale` | 409 | disabled node (in-tree) + discreet inline note | `conv.err.pendingLocale` | "Ainda não disponível neste idioma." / "Not available yet in this language." |
| `node-not-selectable` | 409 | no surface (guard prevents it) — log only | `conv.err.notSelectable` | (internal) |
| `node-not-found` | 404 | toast, reload level | `conv.err.nodeGone` | "Essa frase saiu da lista." / "That phrase is no longer here." |
| `unsupported-locale` | 400 | banner, revert to previous locale | `conv.err.locale` | "Idioma indisponível." / "Language unavailable." |
| `invalid-request` | 400 | toast (assist input only) | `conv.err.invalidRequest` | "Não entendi. Tente de novo." / "Didn't catch that. Try again." |
| `no-biometric-ingress` | 400 | no surface — internal invariant breach, log + block | `conv.err.internal` | (internal) |
| `unauthenticated` | 401 | shell exit → session-ended screen | `conv.err.sessionEnded` | "Sessão encerrada." / "Session ended." |
| `session-revoked` | 401 | shell exit → session-ended screen (SPEC-06 destroy) | `conv.err.sessionEnded` | "Sessão encerrada." / "Session ended." |
| `forbidden-scope` | 403 | shell exit (invalid patient session) | `conv.err.sessionEnded` | "Sessão encerrada." / "Session ended." |
| `retrieval-unavailable` | 200 (soft) | **no surface** — silent fallback to manual nav | — | — |

## 2. Degradation (NOT errors — OPS-003/004)

- **Semantic search down** (`degraded:true` / `retrieval:static` / empty `matches`, or 5xx on `/search`) → silently keep manual tree navigation; **no toast, no banner** (OPS-003). Optionally reflect `ConvServedDegraded` in observability only.
- **Cached audio absent / audio source down** (`playbackMode:web-speech` or `audioRef.present:false`, or blob fetch fails) → Web Speech fallback; if Web Speech is also unavailable → **prominent full-bleed text**. Never a blocking error (OPS-004 / ADR-007).
- **Gesture channel unavailable** (camera/permission/Wake Lock denied) → touch remains a full peer; the `InputChannelIndicator` shows gesture off honestly. No error dialog.

## 3. Locale-pending handling (safety-critical, api-contract §5)

A node whose active-locale content is `pending_locale` (`texto:null`, `selecionavel:false`):
- rendered **disabled / esmaecido** with `conv.err.pendingLocale` inline;
- **never** speaks the other locale's text/audio;
- a `POST /select` that the FE nonetheless issues returns `409 content-pending-locale` → the FE keeps the node disabled and shows the same inline note (no crash, no wrong-locale speech).

## 4. Toast vs banner vs modal — criteria

| Surface | When | Examples |
|---|---|---|
| **inline (in-tree note)** | node-scoped, non-blocking, expected | pending-locale, disabled selecionar |
| **toast** | transient, recoverable by retry, single action | `node-not-found` (reload), assist `invalid-request` |
| **banner** | session/locale-level state the patient should notice but that doesn't block | `unsupported-locale` revert |
| **full-screen (shell exit)** | session terminal state | `unauthenticated` / `session-revoked` / `forbidden-scope` → session-ended screen |
| **modal** | **not used** on the patient surface — modals demand fine interaction; avoided for motor-limited patients |

## 5. Retry strategy

- **Reads** (`/tree`, `/search`, `/bindings/resolve`): 1 silent retry with short backoff on network/5xx; `/search` failure then degrades silently (no error). `/tree` failure after retry → level `error` state with a large "tentar de novo" affordance.
- **`POST /select`**: retried with the **same `clientEventId`** (idempotent, SPEC-05) so a retry never double-emits the audit fact. On persistent failure the FE still speaks locally (Web Speech from the node text already in the VM) — voice-never-silent takes priority over the audit round-trip.
- **Tela 09 confirm** (optimistic, doc 05 §4): apply optimistically, reconcile on ack; on failure re-insert the node into `pending` and show a non-blocking toast; the node stays pending until a successful write.

## 6. Criterios de aceite (EARS)

- THE SYSTEM SHALL map every backend `ProblemDetail` to an `AppError` and SHALL surface calm, jargon-free patient copy, never a raw status code.
- IF the backend responds `content-pending-locale`, THEN THE SYSTEM SHALL keep the node disabled with an inline pending-locale note and SHALL NOT speak another locale's text.
- IF the backend responds `session-revoked` or `unauthenticated` or `forbidden-scope`, THEN THE SYSTEM SHALL exit the patient shell to the session-ended screen.
- IF the backend responds `node-not-found`, THEN THE SYSTEM SHALL show a toast and reload the current level.
- IF the backend responds `unsupported-locale`, THEN THE SYSTEM SHALL show a banner and revert to the previous locale.
- WHEN semantic search is unavailable, THE SYSTEM SHALL fall back to manual navigation silently and SHALL NOT show any error surface.
- WHEN cached audio is absent or fails, THE SYSTEM SHALL speak the phrase text via Web Speech.
- IF Web Speech is unavailable, THEN THE SYSTEM SHALL render the phrase text prominently and SHALL NOT leave the selection silent.
- WHEN a `POST /select` is retried, THE SYSTEM SHALL reuse the original `clientEventId` so the audit fact is emitted at most once, and IF the call keeps failing, THEN THE SYSTEM SHALL still speak the phrase locally.
- WHEN a Tela 09 confirmation write fails after an optimistic removal, THE SYSTEM SHALL re-insert the node into the pending list and SHALL show a non-blocking message.
- THE SYSTEM SHALL NOT render a modal dialog on the patient surface.
