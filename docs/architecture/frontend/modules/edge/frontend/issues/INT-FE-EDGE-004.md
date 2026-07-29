## [INT-FE-EDGE-004] tool-descriptors.ts static fallback — EDGE entry with composition slots, showInNav:false

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
The `scope:web` shell's `tool-descriptors.ts` static fallback includes EDGE's descriptor entry so the
build succeeds even if dynamic discovery is unavailable. The fallback mirrors `EDGE_MODULE_DESCRIPTOR`
with `showInNav:false`, `connectivityReadModel` factory, and `pairingComponent` lazy factory.

**Context / Problem statement:**
- SPEC-06 §2: the shell keeps a static fallback descriptor. For EDGE, the critical fields are the
  composition slots (`connectivityReadModel` + `pairingComponent`) — without them, the topbar chip and
  onboarding step 3 have no EDGE entry point.
- The fallback must never hard-import `scope:edge` internals — it uses the `@neuroassist/edge` barrel
  factories (same as `EDGE_MODULE_DESCRIPTOR` itself).
- This issue is decoupled from dynamic registration so the shell build is stable before EDGE is
  fully registered at runtime.

**Approach (execution path):**
1. In `apps/web/src/app/config/tool-descriptors.ts`:
   - Add EDGE entry: `{ ...EDGE_MODULE_DESCRIPTOR }` (spread or direct reference from barrel).
   - Alternatively: inline the minimal fallback if dynamic imports are not resolved at build time.
2. The entry key is `'edge'`; `showInNav:false` is set.
3. `nx build web` must complete without errors.

**Non-goals:**
- Does not implement dynamic module discovery (Shell concern).
- Does not add any new test beyond build + lint verification.

**Layer(s):**
frontend / apps/web (scope:web config)

**Bounded context:**
edge + web

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-06-shell-integration.md` (§2)

**Architecture alignment:**
- Tag Nx: `scope:web`, `type:app`
- nx-boundary: import only via `@neuroassist/edge` barrel

**Implementation checklist (by file):**

**App path**
- 🔲 `apps/web/src/app/config/tool-descriptors.ts` (add EDGE entry)

**Tests**
- 🔲 `nx build web --configuration=development` PASS (no missing descriptor errors)
- 🔲 `nx lint web` PASS

**Milestone:**
Wave P4 / Hub Integrations

**Labels:**
type:integration
layer:frontend
scope:edge
priority:P4

**Acceptance criteria:**
- `npx nx build web` PASS
- `npx nx lint web` PASS
- `tool-descriptors.ts` contains EDGE entry with `key:'edge'` and `showInNav:false`

**Dependencies:**
- FE-EDGE-003 (EDGE_MODULE_DESCRIPTOR — imported from barrel)

**Data impact:**
- Nenhum

**API / Contract impact:**
- None (static config)

**Testing / Evidence required:**
- `nx build web` output: no "cannot find module" or missing descriptor error
- `tool-descriptors.ts` diff: EDGE entry added

**Risks / Notes:**
- If `@neuroassist/edge` barrel is not yet published/linked, use a local tsconfig path alias to point
  to `libs/edge/shell-entry/src/index.ts` during development.
