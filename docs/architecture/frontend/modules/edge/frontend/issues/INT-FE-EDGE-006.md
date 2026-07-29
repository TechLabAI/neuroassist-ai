## [INT-FE-EDGE-006] Shared error-model — EDGE pt-BR error copy table registered in @neuroassist/shared/error-model

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
The 12 EDGE `ProblemDetail.type` → `AppError` → pt-BR user-facing message mappings from SPEC-07 §1 are
registered in `@neuroassist/shared/error-model` (or in a EDGE-scoped `error-messages.ts` injected at
module bootstrap). Error mapping is centralised — no component defines its own error strings.

**Context / Problem statement:**
- SPEC-07 §1: 12 BE error types must map to distinct `AppError` kinds + pt-BR messages + surfaces
  (banner/toast/modal). This mapping must live in the shared error-model (or EDGE DI injection), not
  scattered across components.
- The `correlationId` from `AppError` is surfaced in error displays for support (no PII in the display).
- EDGE-specific copy must be added to the shared model without breaking other modules' error mappings.
- `/errors/patient-not-paired` on liveness path is NOT an error dialog — it becomes `derivedState='not-paired'`.
  The error model must suppress the dialog for this code (SPEC-07 §2 / §1).

**Approach (execution path):**
1. Create `libs/edge/data-access/src/lib/edge-error-messages.ts` with the complete SPEC-07 §1 table as
   a `Map<string, EdgeErrorConfig>` (type → { kind, message, surface }).
2. In `edgeBootstrap.init()`: register these messages with `SharedErrorModelService.register('edge', edgeErrorMessages)`.
3. Verify: the shared error interceptor (in `shared/access-http-base`) calls `SharedErrorModelService.resolve(type)`
   and maps to the correct `AppError` + message for EDGE calls.
4. Special case: `/errors/patient-not-paired` on liveness poll → return `null` error (suppressed; handled
   as `not-paired` state by facade, not as `AppError` to display).

**Non-goals:**
- Does not implement the shared error interceptor (lives in `shared/access-http-base`).
- Does not implement the toast/banner/modal rendering components.

**Layer(s):**
frontend / libs/edge/data-access + libs/shared/error-model (registration)

**Bounded context:**
edge (registration) + shared

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-07-error-handling.md` (§1)

**Architecture alignment:**
- Tag Nx: `scope:edge`, `type:data-access`

**Implementation checklist (by file):**

**Lib path**
- 🔲 `libs/edge/data-access/src/lib/edge-error-messages.ts` (12-entry Map)
- 🔲 `libs/edge/shell-entry/src/lib/edge.bootstrap.ts` (register edge errors on init)

**Tests**
- 🔲 Unit: for each of the 12 ProblemDetail types, assert correct `AppError` kind + pt-BR message
- 🔲 Unit: `/errors/patient-not-paired` → null (suppressed; no dialog)
- 🔲 Unit: `/errors/device-retired` → `conflict` kind + banner surface (closes SPEC-07 amendment from FE-EDGE-027)
- 🔲 `nx test edge-data-access` PASS
- 🔲 `nx lint edge-data-access` PASS

**Milestone:**
Wave P4 / Hub Integrations

**Labels:**
type:integration
layer:frontend
scope:edge
priority:P4

**Acceptance criteria:**
- `npx nx test edge-data-access` PASS (12 error type mappings asserted)
- `npx nx lint edge-data-access` PASS
- `patient-not-paired` → null (no dialog — suppressed)
- `device-retired` → `conflict` + banner (SPEC-07 amendment applied)
- All 12 pt-BR messages match SPEC-07 §1 table exactly (copy freeze)

**Dependencies:**
- FE-EDGE-004 (edge.bootstrap.ts — registration point for init)
- FE-EDGE-027 (SPEC-07 amendment — adds device-retired EARS clause + mapping)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Matches `api-contract.md §8` ProblemDetail.type values (12 types)

**Testing / Evidence required:**
- 12-entry unit test (one per error type) all green
- Copy freeze: test assertions use the exact pt-BR strings from SPEC-07 §1 table (copy-paste + lock)

**Risks / Notes:**
- `SharedErrorModelService.register()` interface may not exist yet — confirm with the shared team.
  If unavailable, implement a module-level error resolver pattern (DI provider in EDGE providers array).
- The 12 pt-BR strings must be signed off by product before this issue ships — copy review is a
  prerequisite to avoid a late correction sprint.
