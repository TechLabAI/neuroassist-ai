# SPEC-07 — Error Handling — EDGE (Frontend)

> Two error surfaces: (a) **backend REST** failures (`ProblemDetail`) mapped through
> `@neuroassist/shared/error-model` → `AppError` → user-facing pt-BR message, and (b) **connectivity poll**
> failures handled as **derived state** (`unknown`, degraded) rather than an error dialog. No credential
> secret, video, image, landmark, or raw confidence ever appears in an error or log (api-contract §8,
> privacy invariant doc 02).

## 1. `ProblemDetail` → `AppError` → user message (BE REST)

`ProblemDetail.type` is the discriminator (BE api-contract §8). EDGE-FE supplies the pt-BR copy table; the
mapping is centralized in `shared/error-model`, and the `correlationId` is attached to the `AppError` for
observability (no PII).

| BE `ProblemDetail.type` | HTTP | `AppError` kind | User-facing message (pt-BR) | Surface |
|---|---|---|---|---|
| `/errors/validation` | 400 | `validation` | "Verifique os dados do dispositivo antes de continuar." | inline/banner |
| `/errors/enrollment-invalid` | 401 | `unauthorized` | "Código de pareamento inválido ou expirado. Gere um novo código." | banner (pairing) |
| `/errors/insufficient-scope` | 403 | `forbidden` | "Você não tem permissão para gerenciar dispositivos." | toast |
| `/errors/identity-mismatch` | 403 | `forbidden` | "Sessão inválida para esta ação. Entre novamente." | modal |
| `/errors/device-not-found` | 404 | `notFound` | "Dispositivo não encontrado para esta empresa." | banner (detail) |
| `/errors/patient-not-paired` | 404 | `notFound` | "Nenhum dispositivo pareado a este paciente." | *(not an error — chip shows `not-paired`, see §2)* |
| `/errors/already-enrolled` | 409 | `conflict` | "Este dispositivo já foi ativado. Use outro código." | banner (pairing) |
| `/errors/pairing-conflict` | 409 | `conflict` | "Este paciente já tem um dispositivo ativo. Despareie o atual antes de parear um novo." | banner (pairing) |
| `/errors/illegal-pairing-transition` | 409 | `conflict` | "O dispositivo não está pareado. Recarregando o estado atual." | toast + refresh |
| `/errors/device-retired` | 409 | `conflict` | "Este dispositivo foi aposentado e não pode ser usado. Registre um novo." | banner |
| `/errors/optimistic-lock` | 409 | `conflict` | "O estado do dispositivo mudou. Recarregando…" | toast + refresh read-model |
| *(network / 5xx / timeout on an action)* | — | `transient` | "Falha temporária de conexão. Tentando novamente…" | toast (with retry) |

- `/errors/patient-not-paired` on the liveness/resolve path is **not** surfaced as an error dialog: it is the
  normal "no device yet" condition → `derivedState = 'not-paired'`, chip quiet (§2). Only on an explicit
  device-detail open does it render the "nenhum dispositivo pareado" empty state.

## 2. Connectivity poll failures (derived state, not ProblemDetail)

These are **not** rendered as error toasts on every tick — they degrade the derived connectivity honestly
(ADR-005). The always-visible chip must never lie by omission.

| Failure | Detection | Derived effect | User-facing (pt-BR) | Surface |
|---|---|---|---|---|
| Poll network error / 5xx / timeout | liveness poll rejects | `derivedState = 'unknown'` (degraded); silent retry next tick | "Sem confirmação de conexão" (chip subtitle) | chip `muted/warn`, no toast spam |
| Heartbeat stale (age ≥ staleness threshold) | server-relative age crosses threshold | `derivedState = 'stale'` | "Dispositivo sem sinal recente" | chip `warn` |
| Heartbeat offline (age ≥ offline window) | server-relative age crosses window | `derivedState = 'offline'` (loud) | "Dispositivo offline" | chip `er` + optional banner on detail |
| No device paired | `/errors/patient-not-paired` / `status='n/a'` | `derivedState = 'not-paired'` | "Nenhum dispositivo pareado" | chip `muted` |
| Enrollment token TTL elapsed | pairing countdown reaches 0 | `phase = 'expired'` | "Código expirado — gere um novo" | pairing banner + re-register CTA |

**Honest-connectivity invariant:** in every degraded branch the surface may show `unknown`/`stale`/`offline`
but MUST NOT present `online` without a fresh heartbeat and a successful poll (ADR-005). `unknown` is never
optimistically upgraded to `online`.

## 3. Retry strategy

- **Connectivity poll (transient):** no toast; silent retry on the next poll tick. The state stays `unknown`
  until a poll succeeds — degraded is honest, not an error to dismiss.
- **Action network/5xx (register/pair/unpair/retire):** automatic retry with backoff (2 attempts via
  `shared/access-http-base`), then a toast with a manual retry.
- **Conflict (409 pairing-conflict / illegal-transition / optimistic-lock):** never blind-retried — refresh
  the device read-model and re-present the current server state rather than forcing a stale write (doc 05 §5).
- **Enrollment invalid / expired (401 / TTL):** no auto-retry; the operator must re-register to mint a fresh
  token (code TTL respected, ADR-005).
- **Forbidden (403):** never retried; the action is hidden by RBAC (SPEC-05) so this is a defense-in-depth
  fallback, surfaced then dismissed.

## 4. Toast vs. banner vs. modal — criteria

| Surface | Use when | Examples |
|---|---|---|
| **Toast** | transient, non-blocking, self-resolving | action network retry, insufficient-scope on a management action |
| **Banner** | persistent condition affecting the surface until resolved | pairing-conflict, already-enrolled, device-retired, expired code, device-not-found on detail |
| **Modal** | blocking, requires an explicit decision / re-auth | identity-mismatch (session invalid) |
| **Chip state (no dialog)** | ambient connectivity truth | `unknown` / `stale` / `offline` / `not-paired` — never a dialog, always the honest chip |

## Critérios de aceite (EARS)

- IF the backend responds `/errors/validation`, THEN THE SYSTEM SHALL display an inline banner asking the operator to check the device data.
- IF the backend responds `/errors/enrollment-invalid`, THEN THE SYSTEM SHALL display a pairing banner prompting the operator to generate a new code.
- IF the backend responds `/errors/insufficient-scope`, THEN THE SYSTEM SHALL display a toast stating the device action is not permitted.
- IF the backend responds `/errors/pairing-conflict`, THEN THE SYSTEM SHALL display a banner explaining the patient already has an active device and SHALL NOT retry the pairing automatically.
- IF the backend responds `/errors/optimistic-lock`, THEN THE SYSTEM SHALL refresh the device read-model and re-present the current server state.
- IF a connectivity poll fails, THEN THE SYSTEM SHALL set the derived state to `unknown` and SHALL NOT display a toast on every failed tick.
- IF the backend responds `/errors/patient-not-paired` on the liveness path, THEN THE SYSTEM SHALL render the connectivity chip as `not-paired` and SHALL NOT raise an error dialog.
- WHEN a management action fails with a transient network error, THE SYSTEM SHALL retry with backoff before surfacing a toast with a manual retry.
- THE SYSTEM SHALL NOT include any credential secret, video, image, or landmark data in an error message or log.
