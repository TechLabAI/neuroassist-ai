# ADR-007 (TREI-local) — Dual-authorization proposal lifecycle: TREI-FE writes only the caregiver side

- **Status:** proposed (2026-08-31)
- **Scope:** module-local (TREI frontend)
- **Decisor:** FE Architect (vision phase); ratifies ADR-016 (dual authorization / async
  UI handoff) and ADR-018 (independent per-locale approval).

> Numbered ADR-007 (not 006) so no TREI-local ADR shares a number with the materialized
> shared ADR-006 — same convention CONV uses for its second local ADR.

## Context

A proposed speech-tree phrase reaches the patient's runtime only after **two humans
approve it, per language**: the **caregiver** (`confirmadoCuidadora`) and the **patient**
(`confirmadoPaciente`), each `conteudo.<locale>` independent (ADR-016, ADR-018). The two
approvals happen on **different devices, in different shells, asynchronously**: the
caregiver authors and approves her side in TREI/Tela 08 (caregiver shell); the patient
confirms his side later in CONV/Tela 09 (restricted patient shell), with no synchronous UI
handoff between the two (ADR-016 §"handoff de UI"). Only when both sides are `true` for a
locale does `trei-service` index (ES) and synthesize (ElevenLabs) that phrase. TREI-FE must
model this without ever being able to complete the loop on its own.

## Decision

**TREI-FE owns and writes exactly one side of the approval: the caregiver's.**

- The proposal facade exposes only a caregiver-side write — `POST /trei/nodes/{id}/approval`
  with `{ locale, side: 'cuidadora' }`. There is **no method, anywhere in `libs/trei/*`,
  that sets `confirmadoPaciente`** (INV-1); the patient side is CONV's exclusively.
- Each `ProposedNodeCard` renders the **dual, per-locale** confirmation indicator
  (`confirmadoCuidadora` / `confirmadoPaciente`) as read-modelled state, but only the
  caregiver toggle is actionable in TREI.
- The FE drives a proposal at most to **caregiver-approved (awaiting patient)**; it
  **observes** `ready-both-sides` and `indexed+synthesized` but never actuates them
  (those are, respectively, CONV's write and `trei-service`'s side effect).
- Approval is **per locale**: approving PT writes only `conteudo.PT.confirmadoCuidadora`;
  EN is untouched (INV-3, ADR-018).
- The handoff is **asynchronous**: ending the session leaves caregiver-approved (and
  still-pending) proposals as drafts for the patient to confirm later; nothing is discarded
  (INV-4).

## Alternatives considered

1. **TREI can set both approval sides (caregiver "on behalf of" the patient).** Rejected:
   destroys the two-key consent model (ADR-016) — the patient's own confirmation is the
   safeguard that unreviewed content never reaches his runtime; a caregiver override would
   auto-publish to the patient.
2. **Synchronous handoff — caregiver hands the device/screen to the patient to confirm
   in-session.** Rejected: ADR-016 explicitly chooses an async handoff; the patient
   confirms at his own pace in his own restricted shell (CONV/Tela 09), possibly with
   reduced motor control — a synchronous handoff is hostile to that persona.
3. **A single merged bilingual approval (approve the node, both languages at once).**
   Rejected: violates ADR-018's independent per-locale authoring — a node can be right in
   PT and wrong/absent in EN; approval must be per `conteudo.<locale>`.

## Consequences

- **Positive:** the two-key consent invariant is structural — TREI simply *cannot* publish
  to the patient; per-locale correctness is preserved; the async model fits both personas
  (caregiver on a full browser, patient at his own pace); e2e can assert "zero
  patient-side writes from TREI" as a hard property.
- **Negative:** the full lifecycle is split across two modules (TREI writes caregiver side,
  CONV writes patient side), so end-to-end "phrase goes live" cannot be tested within TREI
  alone — it needs a cross-module (E2E) scenario; the caregiver gets no in-TREI signal of
  the moment a phrase actually goes live (that happens after the patient confirms in CONV).

## References

- ADR-016 (dual authorization; async UI handoff) — `project-context.json` modules[TREI]
- ADR-018 (independent per-locale authored content + approval)
- BA wireframe Tela 08 §5.2/§5.3 (dual `cuidadora`/`paciente` indicator; `ProposedNodeCard`)
  and Tela 09 §5.3 (patient-side confirmation lives in CONV) —
  `techlab-ba/projects/neuro-assist-ai/design/wireframe.md`
- CONV FE docs 02 §3 (Confirmation path, Tela 09) — the patient side of this same lifecycle
- TREI docs 03 §4–5 (state transitions, INV-1/INV-3/INV-4), 05 §2/§4
