# ADR-005 — A command edit is not live until the device acknowledges it (draft-first, deferred edge sync)

- **Status:** proposed (2026-07-28)
- **Scope:** local (module PAC)

## Context

PAC's frontend surface is the **command catalog authoring** experience: a caregiver
adds, edits and removes `Comando` entries that map a gesture to a family need with
an urgency and sensitivity. Detection, however, runs on the **local Edge Agent**,
which pulls the catalog and configures itself (project stack: edge→backend sync over
REST/outbox, no broker in the MVP — see project-context `stack.messaging`). There is
therefore an unavoidable **window** between "the caregiver saved a change in the
panel" and "the device is actually detecting with that change".

For a **safety-critical** channel this window is dangerous. If the UI implies a saved
edit is immediately active, a caregiver may believe a just-fixed *emergency* command
is armed while the device is still running the old (or no) configuration — a silent
false sense of safety. The prototype (`calibracao.component.ts`) makes this explicit
with a `TODO`: *"persistir catálogo e sincronizar com o dispositivo edge no próximo
heartbeat"* — persistence and device sync are two separate moments, and the prototype
does neither honestly.

We must decide how the command catalog surfaces the truth about *what is actually
active on the device*.

Options:

1. **Optimistic-live** — treat a successful save as "active"; assume the device will
   catch up. Simplest UI, one state.
2. **Draft-first with deferred, acknowledged sync** — model three moments explicitly:
   `draft` (unsaved), `saved (pendente de sincronização)` (persisted, not yet on the
   device), and `ativo no dispositivo` (device acknowledged the catalog version). The
   catalog never claims `active` without an EDGE device-ack.
3. **Block save until synced** — keep the editor open/spinning until the device
   confirms. Honest but couples the authoring UX to device availability (offline
   device blocks all editing).

## Decision

**PAC adopts draft-first authoring with deferred, acknowledged edge sync (option 2).**

- A live test can run on an **unsaved draft** (test-before-save; the GEST live-test is
  composed at the shell page — GEST ADR-005).
- "Salvar comando" persists via `pac-service` and moves the command to
  **`saved (pendente de sincronização)`** — never directly to active.
- A command is shown **`ativo no dispositivo`** only when EDGE reports the device has
  pulled the catalog version that includes the edit (a derived signal joining the
  saved catalog with EDGE device-ack; doc 05 §1/§4).
- If the device is offline or has not yet synced, the command **stays** visibly
  `pendente de sincronização`. This is an honest state, not an error.

This mirrors GEST's honest-degraded-mode principle: the surface never asserts a
safety property (recognized / active) it cannot substantiate.

## Alternatives considered

1. **Optimistic-live (option 1)** — rejected: gives false confidence on a
   safety-critical channel. A saved emergency command that the device has not yet
   received would appear armed. Unacceptable.
2. **Block save until synced (option 3)** — rejected: couples authoring to device
   availability; an offline edge device would block the caregiver from editing the
   catalog at all, and edits are often made *precisely* while tuning a flaky device.
   Deferred-with-honest-state keeps authoring always available while staying truthful.

## Consequences

- **Positive:** the catalog is an honest mirror of device reality; a caregiver can
  always tell armed commands from pending ones; authoring works offline; the model
  matches the actual edge→backend sync topology (no broker).
- **Negative:** introduces a **device-ack contract** with EDGE (which catalog version
  is active on the device) that must be frozen in specs — currently a known gap
  (doc 06 §2, Assumption A1). Requires a per-command derived sync signal and a clear
  visual treatment for `pendente de sincronização`.
- **Follow-up:** composite commands (two primitives) are higher-risk for false
  negatives; combined with deferred sync, the guidance is *test-before-save* and
  *do not rely on a command until it reads `ativo no dispositivo`*.

## References

- `docs/architecture/frontend/modules/pac/frontend/02-architecture-blueprint.md` (§3 flows)
- `docs/architecture/frontend/modules/pac/frontend/03-domain-framing-frontend.md` (§4 transitions, §5 invariants)
- `docs/architecture/frontend/modules/pac/frontend/05-state-and-data-flow.md` (§1 topology, §4 optimistic updates)
- `docs/architecture/frontend/modules/pac/frontend/06-risks-gaps-assumptions.md` (R1, R3; A1)
- `docs/architecture/frontend/modules/gest/frontend/adr/ADR-005-live-test-via-edge-agent-stream.md` (honest-state / live-test)
- LBC §12 Amendment 1 (composite-gesture false-negative risk)
- project-context `stack.messaging` (edge→backend REST/outbox sync, no broker MVP)
