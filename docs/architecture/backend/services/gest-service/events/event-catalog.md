# GEST — Event Catalog (`gest-service`)

> Module: **GEST — Detecção de Gesto (Edge)** · Service: `gest-service`
> Messaging: **disabled** in MVP (`stack.messaging.enabled = false`).
> Events below are **in-process** (Spring `ApplicationEventPublisher`).
> Future topic names are reserved for no-rename broker extraction.

## Event catalog

| Event | Kind | Direction / Transport | Payload link | Known consumers |
|---|---|---|---|---|
| `PrimitiveRegistered` | in-process domain event | internal / Spring app-events | `event-contracts.md` §2.1 | Audit log, DASH cache hint |
| `PrimitivePromoted` | in-process domain event | internal / Spring app-events | `event-contracts.md` §2.1 | Audit append, DASH cache hint |
| `PrimitiveDeprecated` | in-process domain event | internal / Spring app-events | `event-contracts.md` §2.1 | Audit append, eligibility cache invalidation |
| `DetectionProfilePublished` | in-process domain event | internal / Spring app-events | `event-contracts.md` §2.1 | Prior-profile deactivation, profile cache warm |
| `RecognizedCommandReport` | detection contract (schema) | edge → EVNT (validate at GEST) | `event-contracts.md` §3.1 | EVNT (persists), GEST (validates) |
| `EdgeRecognitionSignal` | detection contract (schema) | edge agent → browser / localhost WS/SSE | `event-contracts.md` §3.2 | FE calibration panel (GestureLiveTestComponent) |
| Detection-contract descriptor | schema publication | GEST → edge/EVNT/FE / REST | `api-contract.md` §5.1 | Edge `GestureDetectionEngine`, EVNT, FE |

## Future topic names (reserved, broker deferred)

| Topic | Future event |
|---|---|
| `gest.primitive.registered.v1` | `PrimitiveRegistered` |
| `gest.primitive.promoted.v1` | `PrimitivePromoted` |
| `gest.primitive.deprecated.v1` | `PrimitiveDeprecated` |
| `gest.profile.published.v1` | `DetectionProfilePublished` |

These names are reserved so that when a broker is introduced, no rename is needed (SPEC-09 §5).

## Contract version

**Detection contract version:** `1.0.0` (semantic, carried in `X-Detection-Contract-Version` header
and in the `contractVersion` payload field of every `RecognizedCommandReport` and
`EdgeRecognitionSignal`).

See `api-contract.md` §5.1 for the descriptor endpoint.
