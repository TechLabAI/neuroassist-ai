# SPEC-02 — Primitive Catalog Orchestration (`gest-service`)

> **Module:** GEST — Detecção de Gesto (Edge)
> **ADR refs:** ADR-006 (TipoGesto detectability lifecycle)
> **EARS guide:** `docs/architecture/backend/standards/ears-notation.md`

## 1. Objective

Specify the domain logic for the TipoGesto primitive catalog: registration, detectability lifecycle
state machine, transition enforcement, and the append-only audit trail. This is the core orchestration
of `gest-service` — the cloud-side detection authority that owns the primitive vocabulary.

## 2. Scope

**In:**
- TipoGesto aggregate: registration, read, lifecycle transition
- Detectability state machine (ADR-006): `registered → pending_training → supported → deprecated`
- Invariant enforcement: `ck_tipo_gesto_supported_needs_ref`, duplicate prevention, no silent regression
- In-process domain events: `PrimitiveRegistered`, `PrimitivePromoted`, `PrimitiveDeprecated`
- Append-only audit trail: `tipo_gesto_transition`

**Out:**
- Detection profile assembly (SPEC-03)
- Eligibility check (SPEC-03)
- Authentication and authorization (SPEC-06)
- UI / FE live-test (FE SPEC scope)

## 3. Technical decisions

| Decision | ADR/Ref |
|---|---|
| Entry state is always `registered`; no auto-promotion | ADR-006 §"Detectability Lifecycle" |
| `engineFeatureRef` required before `supported` (invariant #5) | ADR-006; `ck_tipo_gesto_supported_needs_ref` in DDL |
| Transition surface is `POST /{id}/transitions` — no direct PATCH of state | api-contract §2.4 |
| Audit (`tipo_gesto_transition`) is append-only (no UPDATE/DELETE) | data-model §2.2 |
| Domain events raised post-commit (Spring `ApplicationEventPublisher`) | event-contracts §2 |

## 4. Implementation

### 4.1 Components (hexagonal)

| Layer | Class / Port | Responsibility |
|---|---|---|
| `api` | `TipoGestoController` | REST routing; delegates to use cases |
| `application` | `RegisterPrimitiveUseCase` | Registration invariants, emit `PrimitiveRegistered` |
| `application` | `TransitionDetectabilityUseCase` | State machine, emit `Primitive*` events |
| `domain` | `TipoGesto` | Aggregate; state machine logic; event factory |
| `domain` | `DetectabilityState` | Enum: `REGISTERED`, `PENDING_TRAINING`, `SUPPORTED`, `DEPRECATED` |
| `infrastructure` | `TipoGestoRepository` | JPA adapter; schema-aware (`SchemaRoutingDataSource`) |
| `infrastructure` | `TransitionRepository` | Append-only audit write |

### 4.2 State machine

```
REGISTERED ──▶ PENDING_TRAINING ──▶ SUPPORTED ──▶ DEPRECATED
REGISTERED ──────────────────────▶ SUPPORTED   (seed-only, admin)
PENDING_TRAINING ────────────────▶ DEPRECATED
SUPPORTED ──────────────────────▶ DEPRECATED   (requires reason)
```

No other transitions are permitted. `TipoGesto.transitionTo(newState, actor, reason, engineFeatureRef)`
throws `IllegalTransitionException` for disallowed arcs.

### 4.3 Registration flow

```
POST /tipos-gesto
  -> validate nome length/blank
  -> check duplicate (case-insensitive, active only) -> 409 if exists
  -> create TipoGesto(state=REGISTERED, engineFeatureRef=null)
  -> persist (tenant schema)
  -> raise PrimitiveRegistered (post-commit)
  -> 201 + Location
```

### 4.4 Transition flow

```
POST /{id}/transitions
  -> resolve TipoGesto by id in tenant schema -> 404 if absent
  -> validate toState is reachable -> 409 if not
  -> IF toState=SUPPORTED AND engineFeatureRef null -> 422
  -> IF regression from SUPPORTED AND reason null -> 400
  -> apply transition (mutate state, set engineFeatureRef if provided)
  -> append tipo_gesto_transition row (no update/delete)
  -> raise Primitive* event (post-commit)
  -> 200 + updated TipoGesto
```

## 5. Edge cases

| Scenario | Behavior |
|---|---|
| Transition to current state (no-op) | `409 /errors/illegal-transition` (same-state not allowed) |
| `engineFeatureRef` present when transitioning to `deprecated` | Ignored; retained on the row |
| Concurrent transitions on the same `TipoGesto` | Optimistic lock (`@Version`); losing TX → retry |
| Tenant has 0 TipoGesto rows | `GET /tipos-gesto` → `200` with empty `items[]` |
| `reason` provided but not required | Stored in transition row; not an error |

## 6. Invariants (EARS)

- THE SYSTEM SHALL create every new primitive with `detectabilityState = 'registered'` regardless
  of request content.
- IF `detectabilityState = 'supported'` AND `engine_feature_ref IS NULL`, THEN THE SYSTEM SHALL
  reject the write — invariant #5.
- IF a transition would regress a `supported` primitive without an explicit `reason`, THEN THE
  SYSTEM SHALL respond `400 Bad Request` (`type: /errors/regression-reason-required`) — invariant #4.
- WHEN any lifecycle transition commits, THE SYSTEM SHALL append exactly one record to
  `tipo_gesto_transition` capturing `fromState`, `toState`, `actor`, `reason`, `at`.
- IF the DB transaction rolls back, THEN THE SYSTEM SHALL NOT raise the domain event.
- THE SYSTEM SHALL never hard-delete a `tipo_gesto` row; retirement is a transition to `deprecated`.

## 7. Acceptance criteria

1. A new primitive is always created in state `registered` with `engineFeatureRef = null`.
2. Transitioning to `supported` without `engineFeatureRef` → `422`.
3. Transitioning from `supported` → `deprecated` without `reason` → `400`.
4. Every successful transition appends exactly one audit row.
5. Duplicate `nome` (case-insensitive) in same tenant with non-deprecated state → `409`.
6. Deprecated `nome` can be re-registered (unique index partial: `WHERE state <> 'deprecated'`).
7. Domain events are raised after DB commit (no event on rollback).
8. Transitions from disallowed arcs → `409 /errors/illegal-transition`; state unchanged.

## 8. BDD coverage

`tests/cucumber/tipo-gesto-lifecycle.feature` — all scenarios.
