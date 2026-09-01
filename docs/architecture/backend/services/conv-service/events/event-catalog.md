# CONV — Event Catalog (`conv-service`)

> `messaging.enabled = false` — these are **synchronous audit facts to HIST**, not bus messages
> (see [`../contracts/event-contracts.md`](../contracts/event-contracts.md)). CONV consumes **no**
> events (no broker, `ai.hasAi = false`).

| Fact | Type | Payload | Known consumers |
|---|---|---|---|
| `SpeechNodeSelected` (anchor) | published (→ HIST, sync) | [event-contracts §2.1](../contracts/event-contracts.md) | HIST (audit trail); DASH (history view, indirectly via HIST) |
| `PhraseMatched` | published (→ HIST, sync) | [event-contracts §2.2](../contracts/event-contracts.md) | HIST |
| `TreeNavigated` | published (→ HIST, sync; sampleable) | [event-contracts §2.3](../contracts/event-contracts.md) | HIST |
| `ConvServedDegraded` | published (→ HIST, sync; operational) | [event-contracts §2.4](../contracts/event-contracts.md) | HIST; ops/alerting |
| — | consumed | none | — |

**Notes**
- Delivery is best-effort/non-blocking; failed sends buffer in `conv_audit_outbox` (data-model §2.4).
- Actor is always the **patient session** (`ADR-003`), never the device token or the caregiver.
- TREI content-lifecycle events (node approved, audio synthesized, embedding indexed) are **out of CONV
  scope** — CONV observes their effect on read.
