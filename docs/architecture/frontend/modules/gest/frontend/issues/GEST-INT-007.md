# GEST-INT-007 -- project-context.json registry fix — GEST module: add frontendLib, hasUi:false, isInfrastructure:false

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens
**GitHub:** [#TBD]

## Outcome
`project-context.json` `modules.registry` GEST entry has `frontendLib: "gest"`, `hasUi: false`, `isInfrastructure: false` populated, enabling FE scaffold automation and checklist tracking to work correctly.

## Context / Problem statement
- The GEST registry entry was created without `frontendLib`, `hasUi`, and `isInfrastructure` fields (known gap since M1, carried through M4-fe handoff warnings).
- Without `frontendLib`, `fe-nx-register-module.py` and `sync-architecture.py` cannot track GEST frontend phase status correctly.
- `hasUi: false` is correct — GEST is an embedded feature, not a standalone UI module. `isInfrastructure: false` is correct — it is a domain module.
- Source: M4/M4-fe handoff warnings.

## Approach (execution path)
1. Open `project-context.json` (at neuroassist-ai root).
2. Find the GEST entry in `modules.registry`.
3. Add `"frontendLib": "gest"`, `"hasUi": false`, `"isInfrastructure": false`.
4. Run `python scripts/context.py set --client neuroassist-ai --key modules.registry[GEST].frontendLib --value gest` (or direct edit).
5. Verify `lastUpdatedAt` is updated.

## Non-goals
- Not responsible for other registry fields (backendService already set).

## Layer(s)
frontend / integration (workspace config)

## Bounded context
gest

## Service execution spec(s)
- M4-fe handoff warnings; M1 handoff warning "Registry ainda incompleto"

## Architecture alignment
- R3: atomic write via `context.py`; `lastUpdatedAt` updated

## Implementation checklist (by layer/file)

**Workspace config**
- [ ] `project-context.json` (neuroassist-ai root)

**Tests**
- [ ] `python scripts/sync-architecture.py pull --client neuroassist-ai --module GEST` exits 0
- [ ] `python scripts/checklist.py --client neuroassist-ai` shows GEST FE phases trackable

## Acceptance criteria
- `modules.registry` GEST entry has `frontendLib: "gest"`, `hasUi: false`, `isInfrastructure: false`
- `project-context.json lastUpdatedAt` updated
- `sync-architecture pull` exits 0 for GEST

## Dependencies
- None (pure config fix)

## Empresa-context impact
Nenhum.

## Milestone
Wave 1 / Foundation (P1 — blocks FE scaffold tracking)

## Labels
type:integration
layer:frontend
scope:gest
priority:P1

## Risks / Notes
- This should be done FIRST (P1) before scaffold phase — without it, the scaffold automation cannot correctly track GEST module FE status

## Prioridade
P1
