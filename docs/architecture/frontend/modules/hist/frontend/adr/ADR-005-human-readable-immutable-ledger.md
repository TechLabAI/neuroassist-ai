# ADR-005 — Human-readable, immutable history with a non-optimistic feedback write

- **Status:** proposed (2026-08-16)
- **Scope:** module-local (HIST — Histórico e Auditoria)
- **Deciders:** FE architecture (via `/fe-architect-vision`)

## Context

HIST-FE renders the afterlife of a safety-critical event. Two forces collide:

1. **Legibility vs. fidelity.** The caregiver must read the history in the family's
   own language ("água", "Sapo") — a technical log like `event_type=HELP_REQUESTED`
   destroys trust and is unusable by a non-technical caregiver (wireframe §5.5, LBC
   §12 Emenda 1). Yet the underlying record is an *immutable audit ledger* that must
   not be edited or fabricated from the client.
2. **Correction vs. integrity.** The one write HIST offers — "Foi real?"
   (`real`/`falso_positivo`) — feeds the false-positive signal used for tuning and,
   in a clinic, for auditing. A verdict shown as recorded but not actually persisted
   would silently corrupt that trail.

HIST also does not own the `Evento` (EVNT is the system of record) and the
`hist-service` read model does not exist yet (M1), so the client must isolate the
one place where DTO shape and write target can change.

## Decision

HIST-FE is a **read-first view over an immutable ledger, with a single
non-optimistic, attributed feedback write**:

1. **Render human, store immutable.** Every item shows `Evento.comando.rotulo` +
   urgency badge + timestamp + attributed confirmation line. The UI **never** mutates
   or deletes a historical record and **never** surfaces internal event codes. A
   missing label degrades to a safe human fallback ("comando sem rótulo"), never a code.
2. **Non-optimistic feedback.** "Foi real?" shows the new verdict **only after** the
   backend confirms it; a failed write leaves the item `nao_avaliado` with an inline
   retry. The action is terminal-once-set and idempotent; a `409` reconciles the item
   to the backend verdict (no client override).
3. **Single adaptation point.** DTO→VM mapping and the feedback **write target** live
   only in `HistoryFacade`/`HistClient`; the target is selected by a `feedbackTarget`
   feature-flag (`evnt` | `hist`) until the cross-module contract is resolved (doc 06 G1).

## Alternatives considered

1. **Optimistic feedback** (mark instantly, roll back on failure) — rejected: a
   momentarily-wrong "marked real" corrupts the caregiver's perception of an auditable
   record; the write volume is low, so the latency cost of non-optimistic is negligible.
2. **Render raw event type / code with a label tooltip** — rejected: violates the
   product's core legibility principle; the label must be primary, not secondary.
3. **Let HIST own an editable copy of events** — rejected: breaks the immutable-ledger
   invariant and duplicates EVNT's system of record.

## Consequences

- **Positive:** trust-preserving UI; audit integrity guaranteed client-side; a single,
  swappable seam for the still-open BE contract; consistent urgency treatment reused
  from `@neuroassist/shared` (no hist↔evnt coupling).
- **Negative:** feedback feels slightly slower on poor connections (mitigated by a
  clear per-item marking state); the `feedbackTarget` flag is temporary debt until G1
  is decided in Specs.

## References

- `../06-risks-gaps-assumptions.md` (G1 feedback ownership, R1/R5)
- `../03-domain-framing-frontend.md` (invariants: immutability, non-optimistic, label fidelity)
- Wireframe §5 (Tela 03 — Histórico), fluxo 4.4 / 4.7
- `docs/architecture/frontend/adr/ADR-006-module-entry-as-library.md` (shared)
