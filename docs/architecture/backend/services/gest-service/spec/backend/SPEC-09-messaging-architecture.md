# SPEC-09 — Messaging Architecture (`gest-service`)

> **Module:** GEST — Detecção de Gesto (Edge)
> **ADR refs:** ADR-005 BE (edge/cloud split); `stack.messaging.enabled = false`
> **EARS guide:** `docs/architecture/backend/standards/ears-notation.md`

## 1. Objective

Document the messaging posture of `gest-service` in the MVP and define the extraction path
for future async communication. Although messaging is disabled in the MVP, this spec records
the in-process domain events and the named future topics so the broker introduction requires
no rename.

## 2. Scope

**In:**
- In-process domain events (Spring `ApplicationEventPublisher`) and their post-commit semantics
- Future broker extraction path (deferred, out of MVP scope)
- EARS invariants on event raising and delivery guarantees

**Out:**
- Broker configuration, queue definitions (MVP: no broker)
- EVNT module's persistence of recognized commands (owned by EVNT)

## 3. MVP posture: messaging disabled

- THE SYSTEM SHALL NOT publish to or consume from any message broker in the MVP.
- THE SYSTEM SHALL NOT declare any Spring `@RabbitListener` or `@KafkaListener` beans.
- Cross-service coordination uses synchronous REST (`api-contract.md`): PAC → GEST eligibility,
  EDGE → GEST profile pull, EVNT → GEST report validation.

## 4. In-process domain events

| Event | Trigger | In-process consumers | Future topic |
|---|---|---|---|
| `PrimitiveRegistered` | New TipoGesto created | Audit log, DASH cache hint | `gest.primitive.registered.v1` |
| `PrimitivePromoted` | Transition toward `supported` | Audit append, DASH cache hint | `gest.primitive.promoted.v1` |
| `PrimitiveDeprecated` | Transition to `deprecated` | Audit append, eligibility cache invalidation | `gest.primitive.deprecated.v1` |
| `DetectionProfilePublished` | New profile becomes active | Prior-profile deactivation, profile cache warm | `gest.profile.published.v1` |

### 4.1 Post-commit semantics (EARS)

- WHEN a lifecycle transition commits, THE SYSTEM SHALL raise the corresponding `Primitive*` event
  **after** the DB transaction commits (post-commit), so no consumer observes an uncommitted state.
- WHEN a profile version becomes active, THE SYSTEM SHALL raise `DetectionProfilePublished`
  post-commit.
- IF the transaction rolls back, THEN THE SYSTEM SHALL NOT raise the corresponding event.

### 4.2 Implementation

Use `@TransactionalEventListener(phase = AFTER_COMMIT)` for all handlers that produce side effects
(cache invalidation, audit supplementary writes). For fire-and-forget logging, `AFTER_COMMIT` is
sufficient. No `@Async` in MVP (simplicity; volume is low).

## 5. Future broker extraction path

WHERE a message broker is introduced (RabbitMQ, Kafka), the migration is non-breaking:

1. Add a Spring Cloud Stream binding for each topic in §4.
2. Publish the domain event payload to the topic **in addition to** the in-process listener
   (dual-write during migration, then remove in-process consumers).
3. No rename required: event class names match the future topic names.

Events already designed for extraction: `DetectionProfilePublished` and `RecognizedCommandReport`
(currently REST-validated by GEST, later potentially consumed from a topic by EVNT + GEST).

## 6. Acceptance criteria

- `gest-service` starts cleanly with no broker configuration (no connection refused on startup).
- Domain events are raised after DB commit (verified by asserting audit trail after transaction).
- No domain event is raised when a transaction rolls back (verified by rollback scenario).
- Spring context contains no broker listener beans (`@RabbitListener`, `@KafkaListener`).
