# GEST-FE-009 -- GestEligibilityService + DetectionContractService — REST services over shared/access-http-base

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens
**GitHub:** [#TBD]

## Outcome
`GestEligibilityService` (POST `/api/v1/gest/eligibility:check`) and `DetectionContractService` (GET `/api/v1/gest/detection-contract`) exist in `libs/gest/data-access`, using `@neuroassist/shared/access-http-base`. `DetectionContractService` memoizes the descriptor per session.

## Context / Problem statement
- The live test itself is stream-driven (no REST), but the host page may need the eligibility guard before enabling "save" on an emergency command, and bootstrap needs the contract descriptor.
- Both services are thin wrappers; error mapping is in GEST-FE-010.
- Source: SPEC-04 §4, §5; GEST-FE-005 (bootstrap calls DetectionContractService).

## Approach (execution path)
1. Create `gest-eligibility.service.ts` with `check(req)` → `Observable<EligibilityResult>` via `HttpClient.post`.
2. Create `detection-contract.service.ts` with `getContract()` → `Observable<DetectionContractDescriptor>`, memoizing via `shareReplay(1)`.
3. Both use `@neuroassist/shared/access-http-base` base URL and retry interceptor.
4. Export from `libs/gest/data-access/src/index.ts`.

## Non-goals
- Not responsible for error mapping to `AppError` (GEST-FE-010).
- Not responsible for TipoGesto catalog or detection-profile endpoints (those are PAC/EDGE).

## Layer(s)
frontend / data-access

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-04-data-access.md` (§4, §5)

## Architecture alignment
- `Injectable({ providedIn: 'root' })`
- Tag Nx: `scope:gest`, `type:data-access`

## Implementation checklist (by layer/file)

**Data-access**
- [ ] `libs/gest/data-access/src/lib/gest-eligibility.service.ts`
- [ ] `libs/gest/data-access/src/lib/detection-contract.service.ts`

**Tests**
- [ ] `npx nx test gest-data-access` PASS
- [ ] `npx nx lint gest-data-access` PASS
- [ ] `npx nx build gest-data-access` PASS

## Acceptance criteria
- `DetectionContractService.getContract()` makes exactly one HTTP request per session (memoized)
- `GestEligibilityService.check()` POST to `/api/v1/gest/eligibility:check`
- `npx nx test gest-data-access` PASS

## Dependencies
- GEST-INT-003 (EligibilityResult type from shared/contracts)

## Empresa-context impact
Nenhum — eligibility check is tenant-scoped via auth token, not via explicit param.

## Milestone
Wave 2 / Data access

## Labels
type:feature
layer:frontend
scope:gest
priority:P2

## Risks / Notes
- `DetectionContractService` memoization must be invalidated on logout/session reset

## Prioridade
P2
