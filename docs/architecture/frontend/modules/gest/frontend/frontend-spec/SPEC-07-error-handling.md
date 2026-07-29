# SPEC-07 — Error Handling — GEST (Frontend)

> Two error surfaces: (a) **local device / bridge** failures (camera, edge stream)
> handled as VM state, and (b) **backend REST** failures (`ProblemDetail`) mapped
> through `@neuroassist/shared/error-model` → `AppError` → user-facing message.
> No raw confidence, video, image or landmark data ever appears in an error (BE
> api-contract §7, privacy invariant doc 02).

## 1. `ProblemDetail` → `AppError` → user message (BE REST)

Applies to the one REST resource GEST-FE consumes (`eligibility:check`,
SPEC-04 §4) and to the contract-descriptor fetch. `ProblemDetail.type` is the
discriminator.

| BE `ProblemDetail.type` | HTTP | `AppError` kind | User-facing message (pt-BR) | Surface |
|---|---|---|---|---|
| `/errors/validation` | 400 | `validation` | "Verifique os dados do comando antes de testar." | inline/banner |
| `/errors/tenant-mismatch` | 403 | `forbidden` | "Sessão inválida para esta empresa. Entre novamente." | modal |
| `/errors/insufficient-scope` | 403 | `forbidden` | "Você não tem permissão para esta ação." | toast |
| `/errors/unknown-primitive` | 422 | `unprocessable` | "Um dos gestos deste comando não existe mais. Reconfigure o comando." | banner |
| `/errors/no-active-profile` | 404 | `notFound` | "Nenhum perfil de detecção ativo para o dispositivo. Fale com o suporte." | banner |
| `/errors/device-not-paired` | 403 | `forbidden` | "Dispositivo não pareado a esta empresa." | banner |
| *(network / 5xx / timeout)* | — | `transient` | "Falha temporária de conexão. Tentando novamente…" | toast (with retry) |

Mapping is centralized in `shared/error-model`; GEST supplies only the pt-BR copy
table. The `correlationId` from `ProblemDetail` is attached to the `AppError` for
observability (no PII).

## 2. Local device / bridge failures (VM state, not ProblemDetail)

These are **not** HTTP errors; they map to `GestureTestState` / degraded mode.

| Failure | Detection | VM effect | User-facing (pt-BR) | Surface |
|---|---|---|---|---|
| Camera permission denied / no device | `getUserMedia` rejects | `state = 'camera-error'` | "Não foi possível acessar a webcam. Verifique a permissão do navegador." + "Tentar de novo" | first-class error panel |
| Edge agent unreachable / device offline | stream open fails / `gestDeviceOnlineGuard` | `degraded = true`, reason `device-offline`/`bridge-unreachable` | "Dispositivo offline — teste sem reconhecimento real." | degraded banner |
| Contract version mismatch | bridge negotiation | `degraded = true`, reason `version-mismatch` | "Não foi possível confirmar a versão do detector — teste em modo limitado." | degraded banner |
| Recognition timeout | no signal within `tempoMaximoGestoMs` | `state = 'not-recognized'` | "Não reconhecido — repita o gesto ou aumente a sensibilidade." | inline verdict |

**Honest-degraded invariant:** in every degraded branch the surface may show a
simulated verdict but MUST NOT present it as a real "recognized" (ADR-005). The
banner is always visible while `degraded === true`.

## 3. Retry strategy

- **Transient REST (network/5xx):** automatic retry with backoff (2 attempts, via
  `shared/access-http-base` retry policy), then a toast with a manual retry.
- **Camera error:** no automatic retry (would re-prompt aggressively on a
  non-technical persona, R3 doc 06); explicit user "Tentar de novo" only.
- **Edge bridge unreachable:** no blocking retry loop; enter degraded immediately,
  offer manual "Tentar reconectar".
- **Validation/forbidden (4xx):** never auto-retried; user must correct input or
  re-authenticate.

## 4. Toast vs. banner vs. modal — criteria

| Surface | Use when | Examples |
|---|---|---|
| **Toast** | transient, non-blocking, self-resolving or informational | network retry, insufficient-scope on a background action |
| **Banner** | persistent condition affecting the whole surface until resolved | degraded mode, unknown-primitive, no-active-profile |
| **Modal** | blocking, requires an explicit decision / re-auth | tenant-mismatch (session invalid) |
| **First-class error panel** | the primary task cannot proceed and has a recovery action | `camera-error` (replaces the preview with recovery + retry) |

## Critérios de aceite (EARS)

- IF the backend responds `/errors/validation`, THEN THE SYSTEM SHALL display an inline banner asking the caregiver to check the command data.
- IF the backend responds `/errors/tenant-mismatch`, THEN THE SYSTEM SHALL display a blocking modal prompting re-authentication.
- IF the backend responds `/errors/insufficient-scope`, THEN THE SYSTEM SHALL display a toast stating the action is not permitted.
- IF the backend responds `/errors/unknown-primitive`, THEN THE SYSTEM SHALL display a banner asking the caregiver to reconfigure the command.
- IF `getUserMedia` is denied, THEN THE SYSTEM SHALL enter the `camera-error` state and display a recovery message with a "Tentar de novo" action.
- IF the edge bridge is unreachable, THEN THE SYSTEM SHALL enter degraded mode with a visible banner and SHALL NOT present a real recognition verdict.
- WHEN a transient network failure occurs on a backend call, THE SYSTEM SHALL retry with backoff before surfacing a toast with a manual retry.
- THE SYSTEM SHALL NOT include any video, image, landmark, or raw confidence data in an error message or log.
