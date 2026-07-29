# ADR-005 — No patient without an alarm-eligible caregiver (coverage invariant on unlink / deactivate)

- **Status:** proposed (2026-07-29)
- **Scope:** local (module ACS)

## Context

ACS's frontend surface authors **access**: who is a `Cuidador` in a tenant, what `papel`
they hold, and which `Paciente`(es) they are linked to. A caregiver who is **active** and
**linked** to a patient is the patient's **alarm-eligible responder** — the set EVNT routes
an emergency to. NeuroAssist exists so that a patient whose only channel to the world is a
residual facial movement can summon help; that help only arrives if **at least one real
person is on the hook** when an emergency fires.

There is therefore a state that is not merely inconvenient but **unsafe**: a patient with
**zero active alarm-eligible caregivers**. ACS is the only module that can create this state
through a deliberate user action — unlinking the last covering caregiver, or deactivating
them. If the UI treats unlink/deactivate as ordinary CRUD, a coordinator ending a shift, or
a family caregiver "cleaning up" the roster, can silently leave Márcio with no one covering
an emergency, and nothing on screen would warn them.

We must decide how the access surface treats the transition that would drop a patient's
coverage to zero. This mirrors the honest-state discipline PAC (ADR-005: edit ≠ live) and
GEST (honest degraded mode) already adopt: the surface must never let a safety property
lapse silently.

Options:

1. **Coverage-agnostic CRUD** — unlink/deactivate always succeed; coverage is a passive,
   after-the-fact indicator. Simplest; one code path.
2. **Coverage-honest with a blocking invariant** — the UI **refuses** any unlink/deactivate
   that would remove a patient's *last* active alarm-eligible caregiver, with an explicit
   reason, and marks any already-uncovered patient `sem cobertura` (danger). Coverage is a
   derived signal joining linkage with caregiver-active state; the block is re-enforced by
   the backend.
3. **Warn-but-allow** — show a confirmation dialog when the action would uncover a patient,
   but let the user proceed. Honest-ish, but still permits the unsafe end state.

## Decision

**ACS adopts a coverage-honest, blocking invariant (option 2).**

- Coverage is a **derived signal** (`CoverageFacade`): a patient is `coberto` iff it has
  ≥ 1 caregiver that is both `ativo` and linked to it; otherwise `sem cobertura` (rendered
  with `--color-danger`, never hidden).
- The UI **blocks** `unlinkCaregiver` and `deactivateCaregiver` when the target is the
  patient's **last** active alarm-eligible caregiver, showing the explicit reason *"Este
  paciente ficaria sem cuidador responsável — vincule ou ative outro antes."* The user must
  first link/activate a replacement.
- Changing a caregiver's **`papel`** is *not* blocked by coverage — role is an
  admin-capability concern, distinct from alarm eligibility. Losing the last
  `coordenador_clinica` is allowed; losing the last *covering* caregiver is not.
- The FE guard runs client-side (fast, honest UX) **and** is re-enforced by the backend
  (409/422) — the FE block is a courtesy, not the security boundary.
- A patient that is uncovered by a path ACS cannot block (e.g. created in PAC before any
  linkage) is surfaced as `sem cobertura`, not silently assumed safe.

## Alternatives considered

1. **Coverage-agnostic CRUD (option 1)** — rejected: allows the last responder to be
   removed silently on a safety-critical channel; a patient could be left uncovered with no
   signal. Unacceptable for this domain.
2. **Warn-but-allow (option 3)** — rejected: a confirmation dialog is routinely click-through
   and still permits the unsafe end state. For "a patient can summon help", the correct
   default is to **refuse** and require the user to establish alternative coverage first.

## Consequences

- **Positive:** the roster is an honest mirror of who can actually catch an emergency; a
  patient is never silently left uncovered by a deliberate action; coverage is visible and
  auditable (HIST); the model matches the project's honest-state discipline (PAC/GEST).
- **Negative:** introduces a **coverage-eligibility contract** — what counts as
  "alarm-eligible" must be identical on FE (derived signal) and BE (re-enforcement) and must
  match what EVNT routes to (doc 06 gap; Assumption A4). Requires a derived per-patient
  coverage signal and a clear `sem cobertura` visual treatment. A coordinator off-boarding a
  caregiver mid-shift may be forced to link a replacement first (intended friction).
- **Follow-up:** freeze the alarm-eligibility definition jointly with EVNT and the BE; decide
  whether `deactivate` is reversible and how re-activation restores coverage (doc 06 gaps).

## References

- `docs/architecture/frontend/modules/acs/frontend/01-architectural-vision.md` (§1 intent, §7 success)
- `docs/architecture/frontend/modules/acs/frontend/03-domain-framing-frontend.md` (§4 transitions, §5 invariants)
- `docs/architecture/frontend/modules/acs/frontend/05-state-and-data-flow.md` (§1 topology, §4 optimistic updates)
- `docs/architecture/frontend/modules/acs/frontend/06-risks-gaps-assumptions.md` (R1, R2, R5; A1, A4)
- `docs/architecture/frontend/modules/pac/frontend/adr/ADR-005-command-edit-not-live-until-synced.md` (honest-state discipline)
- project-context `_extensions.data_model` (`Cuidador.papel`, `Tenant.tipo`, `Evento.feedback`)
