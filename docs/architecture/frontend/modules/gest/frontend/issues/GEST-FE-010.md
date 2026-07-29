# GEST-FE-010 -- ProblemDetail → AppError — pt-BR copy table for GEST error types (SPEC-07 §1)

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens
**GitHub:** [#TBD]

## Outcome
`libs/gest/data-access/src/lib/gest-error-map.ts` provides the pt-BR copy table for all 7 GEST `ProblemDetail` error types, mapping to `AppError` kinds and user-facing messages. No raw biometric data appears in any error message.

## Context / Problem statement
- `ProblemDetail.type` is the discriminator. GEST has 7 known error types from `api-contract.md §7`.
- Mapping is centralized in `shared/error-model`; GEST supplies only the pt-BR copy table and surface hints (inline/banner/modal/toast).
- Source: SPEC-07 §1, §4.

## Approach (execution path)
1. Create `gest-error-map.ts` with a `Record<string, { kind: AppErrorKind; message: string; surface: ErrorSurface }>` map.
2. Cover all 7 entries from SPEC-07 §1: `/errors/validation`, `/errors/tenant-mismatch`, `/errors/insufficient-scope`, `/errors/unknown-primitive`, `/errors/no-active-profile`, `/errors/device-not-paired`, `transient`.
3. Register the map with `shared/error-model`'s `registerDomainErrors('gest', map)` (or equivalent shared API).
4. Attach `correlationId` from `ProblemDetail` to `AppError` for observability (no PII).

## Non-goals
- Local device / bridge failures (camera, edge stream) are VM state, not ProblemDetail — handled in GEST-FE-008.

## Layer(s)
frontend / data-access

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-07-error-handling.md` (§1)

## Architecture alignment
- Pure data map, no Angular class
- Tag Nx: `scope:gest`, `type:data-access`

## Implementation checklist (by layer/file)

**Data-access**
- [ ] `libs/gest/data-access/src/lib/gest-error-map.ts`

**Tests**
- [ ] `npx nx test gest-data-access` PASS (all 7 error types map correctly; no raw data in messages)
- [ ] `npx nx lint gest-data-access` PASS
- [ ] `npx nx build gest-data-access` PASS

## Acceptance criteria
- All 7 SPEC-07 error types covered
- `tenant-mismatch` maps to `surface: 'modal'`
- `insufficient-scope` maps to `surface: 'toast'`
- No video/image/landmark/confidence text in any message
- `npx nx test gest-data-access` PASS

## Dependencies
- None (pure data)

## Empresa-context impact
Nenhum.

## Milestone
Wave 2 / Data access

## Labels
type:feature
layer:frontend
scope:gest
priority:P2

## Risks / Notes
- Must align `AppErrorKind` values with whatever `shared/error-model` exports

## Prioridade
P2
