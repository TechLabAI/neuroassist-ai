# SPEC-08 — BDD Test Scenarios (`edge-service`)

> **Module:** EDGE — Integração de Dispositivo Edge
> **ADR refs:** ADR-005, ADR-006
> **EARS guide:** `docs/architecture/backend/standards/ears-notation.md`

## 1. Objective

Index the executable Gherkin features, the reusable step vocabulary, and the example data, so
`scripts/be-bdd-generator.py` produces the Cucumber test skeleton and the BDD gate has a clear map from
invariant → scenario.

## 2. Feature inventory

| Feature file | Covers | Key invariants |
|---|---|---|
| `tests/cucumber/device-registration-enrollment.feature` | SPEC-11, SPEC-06 | one-time enrollment, credential issued once, operator-only register |
| `tests/cucumber/device-pairing.feature` | SPEC-03 | exclusivity #1, audited transitions #3/#4, referential honesty #5 |
| `tests/cucumber/heartbeat-liveness.feature` | SPEC-02 | identity-from-token #2, derived liveness #3, server-clock stamp, ETag/304 |
| `tests/cucumber/profile-pointer-drift.feature` | SPEC-02, SPEC-10 | pointer delivery, drift, GEST-degraded fallback, device→tenant |

## 3. Reusable step vocabulary

These steps are shared across features; implement once in the Cucumber glue (`*StepDefs`):

- `Given the tenant "<t>" is active` — provisions/points to `t_<t>`.
- `Given the caller has role "<scope>"` — mints a JWT with the scope (`edge.read`/`edge.manage`).
- `Given the device credential for "<deviceId>" is valid` — mints a device token (`edge.device`,
  `deviceId`+`tenant` claims).
- `Given the caller presents a (valid )?service credential( for tenant "<t>")?` — `edge.internal` token.
- `Given device "<id>" is (in state|paired to patient) …` — seeds a `dispositivo_edge` row.
- `Given the last accepted heartbeat for device "<id>" was <n> seconds ago` — sets `ultima_sincronizacao`.
- `When the edge device "<id>" sends a heartbeat( with …)?` — POST `/heartbeat` with device token.
- `When DASH polls the liveness of device "<id>"` — GET `/devices/{id}/liveness`.
- `When PAC requests the device for patient "<id>"` — GET `/patients/{id}/device`.
- `When GEST requests the tenant of device "<id>"` — GET `/devices/{id}/tenant` (service token).
- `Then the response status is <code>` / `Then the error type is "<type>"` — HTTP + ProblemDetail asserts.
- `Then a (pairing|liveness) transition "<from> -> <to>" is appended …` — audit-table assert.

## 4. Example data

| Entity | Example |
|---|---|
| Tenant | `empresa-test`, `empresa-other` |
| Device ids | `d7b1`, `e8c2`, `z9z9` |
| Patient ids | `9c2a`, `1f4b` |
| Agent versions | `1.4.2`, `1.5.0` |
| Profile versions | `6` (running), `7` (active) |
| Staleness threshold | `90` s (heartbeat `30` s × factor `3`) |

## 5. Coverage matrix (invariant → scenario)

| Invariant | Scenario(s) |
|---|---|
| #1 exclusive pairing | pairing: "second device to a patient", "already paired", concurrency |
| #2 identity from token | heartbeat: "identity comes from the token, never the body" |
| #3 deterministic liveness | heartbeat: `Scenario Outline` age→status; "never sent a heartbeat" |
| #4 no silent change | heartbeat: crossing appends `liveness` transition; pairing audits |
| #5 referential honesty | pairing: "PAC resolves"/"404 when no paired device" |
| #6 no media | heartbeat: "heartbeat carrying media is rejected" |
| G1 device credential | registration-enrollment: "exchanges token for credential" |
| GEST degradation | profile-pointer-drift: "liveness never depends on GEST" |

## 6. Gate note

The BDD gate runs post-scaffold (Testcontainers + Cucumber). At specs time the features are authored with
`--generate-only`; the executable gate is replayed after `/be-architect-scaffold` via
`python scripts/be-gates-replay.py --run --update --service edge-service --gate bddGate`.
