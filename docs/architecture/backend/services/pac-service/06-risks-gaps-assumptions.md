<!-- OUTPUT-SCHEMA
- Riscos
- Gaps Conhecidos
- Suposições a Validar
- Dependências Críticas
-->

# 06 — Risks, Gaps & Assumptions

## Riscos
1. **Emergency mapped to an undetectable gesture** — the highest-severity risk: a family publishes an
   `emergencia` command backed by a primitive the engine cannot recognize. Mitigated by the fail-closed
   eligibility gate against GEST (ADR-005), but depends on GEST's `supported` state being accurate.
2. **GEST unavailable at publish time** — synchronous eligibility gate + no broker means GEST downtime
   blocks publishing emergency commands. Fail-closed is safe (no bad publish) but degrades authoring;
   needs a clear operator-facing error and retry, not a silent hang.
3. **Calibration drift vs. detection reality** — the family loosens sensitivity in PAC, but the actual
   false-positive/false-negative outcome only surfaces in EVNT feedback. Without a feedback loop from
   EVNT back to PAC, tuning is blind trial-and-error.
4. **Cross-service referential integrity** — primitive/caregiver/device ids are references, not FKs; a
   `TipoGesto`, `Cuidador` or `DispositivoEdge` could be deprecated/removed in its owning service,
   leaving a dangling reference in a `Comando`/`Paciente`.
5. **History corruption on edit** — an in-place edit of a command referenced by past events could change
   the meaning of historical events if snapshotting/versioning (ADR-006) is not honored end to end.

## Gaps Conhecidos
- **Feedback loop from EVNT → PAC** is out of MVP scope: the false-positive rate that should inform
  recalibration is not yet piped back into PAC. Recorded as a post-MVP tuning gap.
- **Duplicate-mapping policy** — whether two active commands may share the same gesture, or the same
  need, is not yet decided (uniqueness guard is a Specs decision).
- **Composite-gesture semantics** — how a two-primitive composite is combined (simultaneous vs.
  sequential) is defined by GEST detection; PAC only references the pair. The authoring UX for composites
  is a DASH/Specs concern.
- **Emergency vs. routine gate policy** — the exact advisory behavior for routine commands with a
  non-`supported` primitive (warn severity, whether it blocks publish) needs Specs confirmation.

## Suposições a Validar
- PAC is the **sole authoring source** of per-command calibration params (sensitivity, timing) and GEST
  **snapshots** them into an immutable profile — confirmed against GEST framing (§ ClassificationParams
  "sourced from PAC `Comando`"); to be re-confirmed in the GEST↔PAC contract during Specs.
- `TipoGesto` is owned by **GEST**, not PAC; PAC only references primitive ids — confirmed against GEST
  domain framing (§ "Not owned here: Comando (PAC), Paciente (PAC)").
- `Cuidador` and `Tenant` are owned by **ACS**, `DispositivoEdge` by **EDGE**; PAC stores references only.
- Messaging stays disabled for the MVP; the pull/synchronous propagation model is acceptable at pilot
  volume (1 patient).
- Free-text labels need no controlled vocabulary or moderation in the MVP.

## Dependências Críticas
- **GEST (`gest-service`)** — mandatory for the emergency-eligibility gate; PAC cannot safely publish
  emergency commands without it. Tightest coupling in the module.
- **ACS (`acs-service`)** — tenant validity and responsible-caregiver resolution; multi-tenancy scoping
  depends on it.
- **EDGE (`edge-service`)** — device resolution for a patient.
- **MIG (`neuroassist-ai-migrations`)** — centralized schema migrations across all empresa schemas.
- **GWY / REG** — ingress routing and service discovery for all synchronous calls.
- **Downstream readers (GEST profiles, EVNT events, DASH panel)** depend on PAC's catalog being correct
  and stable — PAC's correctness is a precondition for the whole detection→event→notify chain.
