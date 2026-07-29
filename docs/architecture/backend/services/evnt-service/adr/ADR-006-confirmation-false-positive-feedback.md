<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-006 — Caregiver Confirmation & False-Positive Feedback (Terminal, Audited)

## Status
aceita

## Contexto
The `Evento` entity carries a `feedback` enum (`nao_avaliado`, `real`, `falso_positivo`) and a
`confirmadoPor` reference (data-model, BA draft). For a safety product driven by an imperfect computer-vision
signal, the caregiver's judgement on whether an alarm was genuine is not a nicety — it is the only ground
truth the system has about detection quality, and the raw material for tuning `sensibilidade` (PAC) and
primitive thresholds (GEST). The design question is whether feedback is a mutable field (editable, silently
overwritable) or a controlled, attributed, auditable transition. A safety/audit context (LGPD, immutable
history — HIST) rules out silent overwrites.

## Decisão
Feedback is a **first-class, terminal, audited transition** on the Evento: `nao_avaliado → real` or
`nao_avaliado → falso_positivo`, set **once**. Every transition **requires a resolvable `confirmadoPor`**
(a Cuidador identity from ACS) and records `confirmadoAt`. EVNT raises `EventoConfirmado` /
`FalsoPositivoMarcado` and **appends the fact to HIST** (audit trail). A later correction is modeled as a
**new audited action**, never an in-place mutation of the prior verdict. The `falso_positivo` signal is
exposed (queryable per patient/command) so downstream tuning can consume the false-positive rate.

## Consequências
**Positivas:** ground-truth feedback is captured with attribution and an audit trail (defensible for a
safety/LGPD context); false positives become an improvement signal instead of a hidden error state;
terminal verdicts prevent silent rewriting of what a caregiver decided.
**Negativas / trade-offs:** correcting a mistaken verdict requires an explicit new action rather than an
edit (accepted — auditability over convenience); EVNT depends on ACS to resolve `confirmadoPor` on the
confirmation path; the *consumer* of the false-positive rate (PAC sensitivity tuning vs. GEST thresholds)
is not yet contracted (gap, doc 06) — EVNT only guarantees the signal is recorded and queryable.
