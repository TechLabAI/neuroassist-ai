# SPEC-03 — Pairing Lifecycle & Exclusivity (`edge-service`)

> **Module:** EDGE — Integração de Dispositivo Edge (**critical sub-feature**)
> **ADR refs:** ADR-005 (exclusive, operator-driven, audited pairing)
> **EARS guide:** `docs/architecture/backend/standards/ears-notation.md`

## 1. Objective

Specify the device pairing state machine, the exclusivity invariant (one active device per patient), the
audited transitions, and the referential-honesty guarantee PAC depends on.

## 2. Scope

**In:**
- Pairing state machine: `registered → paired → unpaired`, and `registered|unpaired → retired`
- Exclusivity enforcement (DB partial-unique + service check)
- Pairing audit (`dispositivo_edge_transition`, `kind='pairing'`)
- PAC resolution (`GET /patients/{id}/device`) with referential honesty

**Out:**
- Liveness (SPEC-02); credential issuance/rotation (SPEC-06); registration (SPEC-11)

## 3. Technical decisions

| Decision | ADR |
|---|---|
| Pairing is exclusive and explicit (operator-driven), never inferred from telemetry | ADR-005 |
| One active device per patient, one patient per paired device (MVP) | ADR-005 / invariant #1 |
| Device replacement = unpair-old (+ retire) then pair-new; old device `retired`, never deleted | ADR-005 / doc 05 |
| Every pairing transition is audited (actor + reason) — security evidence | ADR-005 / invariant #3 |

## 4. State machine

```
registered ──pair──> paired ──unpair──> unpaired ──pair──> paired
registered ──retire──> retired (terminal)
unpaired   ──retire──> retired (terminal)
paired     ──retire──> retired (terminal; pairing dropped + audited)
```

- THE SYSTEM SHALL permit only the transitions above; any other target is
  `409 /errors/illegal-pairing-transition`.
- THE SYSTEM SHALL treat `retired` as terminal.

## 5. Exclusivity (EARS)

- THE SYSTEM SHALL keep at most one `paired` device per `paciente_id`
  (`ux_dispositivo_edge_active_pairing`) — invariant #1.
- IF a patient already has a `paired` device, THEN THE SYSTEM SHALL respond `409 /errors/pairing-conflict`.
- IF a device is already `paired`, THEN THE SYSTEM SHALL respond `409 /errors/pairing-conflict`.
- WHEN pairing succeeds, THE SYSTEM SHALL set `paciente_id`, move to `paired`, append a `pairing`
  transition, and raise `DispositivoPareado`.
- WHEN unpairing succeeds, THE SYSTEM SHALL clear `paciente_id`, move to `unpaired`, append a `pairing`
  transition (with `reason`), and raise `DispositivoDespareado`.
- THE SYSTEM SHALL NOT validate `pacienteId` existence against PAC at pairing time — PAC owns that write
  path and validates the device against EDGE (cross-service ref, not FK).

## 6. Referential honesty for PAC (EARS)

- WHEN PAC resolves a device for a patient, THE SYSTEM SHALL return `200` with the paired device only if
  it is registered and currently `paired` to that same patient — invariant #5.
- IF no device is currently `paired` to the patient, THEN THE SYSTEM SHALL respond
  `404 /errors/patient-not-paired`.

## 7. Edge cases

| Scenario | Behavior |
|---|---|
| Pair a `retired` device | `409 /errors/device-retired` |
| Unpair a device not in `paired` | `409 /errors/illegal-pairing-transition` |
| Concurrent pair of two devices to one patient | one wins; the other `409` (DB unique + optimistic lock, SPEC-05) |
| Re-pair an `unpaired` device to a new patient | allowed (`unpaired → paired`) |

## 8. Acceptance criteria

- Two devices cannot be `paired` to the same patient at the same time (enforced at DB level).
- Every pair/unpair produces exactly one audit row with actor and (for unpair) reason.
- A retired device is never reused and never deleted.
- PAC's resolve returns paired-or-404, never a stale/unpaired device.

## 9. BDD coverage

`tests/cucumber/device-pairing.feature`.
