# GEST-INT-003 -- shared/contracts — EdgeRecognitionSignal verbatim binding from event-contracts.md §3.2 (v1.0.0)

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens
**GitHub:** [#TBD]

## Outcome
`libs/shared/contracts/src/lib/gest/edge-recognition-signal.ts` declares `EdgeRecognitionSignal` verbatim from BE `event-contracts.md §3.2` (frozen v1.0.0) and `EligibilityResult` from `api-contract.md §3.1`. These types are available at `@neuroassist/shared/contracts`.

## Context / Problem statement
- The FE vision doc draft shape (`recognized`, `matchedPrimitivo`, `step`) is SUPERSEDED. The frozen BE contract must be the single source of truth for the wire type.
- Both `EdgeDetectionClient` and `GestureTestFacade` depend on this type.
- Source: SPEC-04 §1, cross-link.md §1.

## Approach (execution path)
1. Create `libs/shared/contracts/src/lib/gest/edge-recognition-signal.ts` with `EdgeRecognitionSignal` interface matching exactly `event-contracts.md §3.2`.
2. Create `gest/eligibility-result.ts` with `EligibilityResult` from `api-contract.md §3.1`.
3. Add `ComandoDraft` (SPEC-03 §1) to shared/contracts or gest/data-access (decide in scaffold).
4. Export from `libs/shared/contracts/src/index.ts`.

## Non-goals
- Not responsible for `RecognizedCommandReport` (edge → EVNT, never touched by browser).
- Not responsible for TipoGesto catalog types (PAC scope).

## Layer(s)
frontend / integration (shared contracts)

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-04-data-access.md` (§1)
- `docs/architecture/frontend/modules/gest/frontend/contracts/backend-api-cross-link.md` (§1)

## Architecture alignment
- Pure TypeScript interfaces, no Angular dependencies
- Tag Nx: `scope:shared`, `type:domain`

## Implementation checklist (by layer/file)

**Shared contracts lib**
- [ ] `libs/shared/contracts/src/lib/gest/edge-recognition-signal.ts`
- [ ] `libs/shared/contracts/src/lib/gest/eligibility-result.ts`

**Tests**
- [ ] `npx nx test shared-contracts` PASS (interface shape matches event-contracts.md verbatim)
- [ ] `npx nx lint shared-contracts` PASS
- [ ] `npx nx build shared-contracts` PASS

## Acceptance criteria
- `EdgeRecognitionSignal.contractVersion`, `.signalKind`, `.commandDraftRef`, `.primitives[]`, `.timingWindow`, `.degraded`, `.emittedAt` all present
- The old vision-draft shape (`recognized`/`matchedPrimitivo` as wire fields) does NOT appear
- `npx nx build shared-contracts` PASS

## Dependencies
- None (pure types)

## Empresa-context impact
Nenhum.

## Milestone
Wave 4 / Hub integrations

## Labels
type:integration
layer:frontend
scope:gest
priority:P4

## Risks / Notes
- Contract frozen at v1.0.0 — any update requires a BE + FE coordinated version bump; do not modify unilaterally

## Prioridade
P4
