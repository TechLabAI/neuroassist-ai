# Backend API Cross-Link — GEST (Frontend ↔ `gest-service`)

> This file **references** the frozen BE contract; it does not redefine it.
> Source of truth: `docs/architecture/backend/services/gest-service/contracts/`
> (`api-contract.md`, `data-model.md`, `event-contracts.md`). Detection contract
> frozen at **v1.0.0**.

## 1. What the FE binds to

| FE artifact | BE source | Binding |
|---|---|---|
| `EdgeRecognitionSignal` (stream payload) | `event-contracts.md` §3.2 | **Verbatim** — `contractVersion`, `signalKind`, `commandDraftRef`, `primitives[]`, `timingWindow`, `degraded`, `emittedAt`. Lives in `@neuroassist/shared/contracts`. |
| `EligibilityResult` | `api-contract.md` §3.1 | `POST /api/v1/gest/eligibility:check` response shape. |
| Detection-contract descriptor + version negotiation | `api-contract.md` §5.1 | `GET /api/v1/gest/detection-contract` (`contractVersion`, schema `$ref`s). |
| `X-Detection-Contract-Version` header | `api-contract.md` §1.1 / §7 (event-contracts §4.2) | The FE negotiates and honors this version; a mismatch → degraded (SPEC-04 §2). |
| `ProblemDetail` error types | `api-contract.md` §7 | Mapped to `AppError` + pt-BR copy in SPEC-07. |

## 2. What the FE deliberately does NOT consume

- **TipoGesto catalog** (`api-contract.md` §2), **transitions** (§2.4),
  **detection-profile publish** (§4.1), **edge pull** (§4.4),
  **recognized-command validate** (§5.2) — these are BE/PAC/EDGE/edge surfaces,
  not the GEST-FE live-test feature. The FE consumes only the stream, the
  eligibility guard, and the contract descriptor (SPEC-04 §5).
- The **production** `RecognizedCommandReport` (`event-contracts.md` §3.1) —
  edge → EVNT telemetry, never touched by the browser.

## 3. Superseded FE draft

The FE vision doc 03 §2 sketched a draft signal (`recognized`,
`matchedPrimitivo`, `step`). That draft is **superseded** by the frozen BE
`EdgeRecognitionSignal` (§1). `libs/gest/data-access` MUST bind the frozen shape
(SPEC-03 / SPEC-04); the mapping from `signalKind` to the UI verdict/step lives in
`GestureTestFacade` (SPEC-04 §3).

## 4. Open cross-boundary item (carried to backlog)

- **Bridge handshake / transport auth** (`event-contracts.md` §3.3,
  `api-contract.md` §6.3): the localhost channel open + device-credential format
  (mTLS vs OAuth2 `client_credentials`) is jointly owned by FE / EDGE / edge team,
  still open. FE assumes a **JWT-shaped device token** provisionally, isolated
  behind `EdgeDetectionClient`'s `bridgeToken` provider (SPEC-04 §2) so the final
  decision does not ripple into the facade. Freeze in `/fe-architect-backlog`.
