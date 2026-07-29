## [FE-ACS-023] acsCaregiverWriteGuard + acsLinkWriteGuard (libs/acs/access)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Two additional functional guards in `libs/acs/access`: `acsCaregiverWriteGuard` (checks `acs.cuidador.write`)
and `acsLinkWriteGuard` (checks `acs.vinculo.write`). These enforce defense-in-depth: the BE is authoritative,
but the FE blocks the route early. Both check the permission map against the session's authority set.

**Context / Problem statement:**
- SPEC-01 §3 defines both guards; they are layered on top of `acsCoordinatorGuard`.
- `acsCaregiverWriteGuard` is applied to both caregiver editor routes (novo + editar).
- `acsLinkWriteGuard` is applied to the patient linkage route.
- SPEC-05 §2 maps `acs.cuidador.write` → BE authority `acs.cuidador.criar`; the UI guard uses the UI key.

**Approach (execution path):**
1. Create `libs/acs/access/src/lib/acs-caregiver-write.guard.ts` — functional guard checking
   `accessDecisionService.hasPermission('acs.cuidador.write')`.
2. Create `libs/acs/access/src/lib/acs-link-write.guard.ts` — functional guard checking
   `accessDecisionService.hasPermission('acs.vinculo.write')`.
3. Both: return `true` if permission present; else return `UrlTree(['/acs'])` (back to roster, not login).
4. Re-export both from `libs/acs/access/src/index.ts`.
5. Unit tests: permission present → true; absent → redirect to /acs.

**Non-goals:**
- No auth logic.
- No server-side enforcement (BE handles the actual authority check).

**Layer(s):**
frontend / libs/acs/access

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-01-route-map.md` (§3)
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-05-tool-contract.md` (§2 permission map)

**Architecture alignment:**
- Functional guards; inject(); returns `boolean | UrlTree`
- Tags Nx: `scope:acs`, `type:domain`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/access/`
- 🔲 `src/lib/acs-caregiver-write.guard.ts` — acs.cuidador.write check
- 🔲 `src/lib/acs-link-write.guard.ts` — acs.vinculo.write check
- 🔲 `src/index.ts` — re-exports both guards

**Tests**
- 🔲 `src/lib/acs-caregiver-write.guard.spec.ts` — has/lacks write permission
- 🔲 `src/lib/acs-link-write.guard.spec.ts` — has/lacks link write permission
- 🔲 `nx test acs-access` PASS
- 🔲 `nx lint acs-access` PASS

**Milestone:**
Wave 5 / P5 Access guards

**Labels:**
type:feature
layer:frontend
scope:acs
priority:P5

**Acceptance criteria:**
- `npx nx test acs-access` PASS
- `npx nx lint acs-access` PASS
- `acsCaregiverWriteGuard` sem write permission → redirect para `/acs`
- `acsLinkWriteGuard` sem vinculo write → route bloqueada
- E2E: "convidar hidden without write"; "vincular disabled without vinculo.write"

**Dependencies:**
- FE-ACS-022 (coordinator guard already in lib)
- FE-ACS-002 (guards referenciados nas rotas)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Unit tests passando
- E2E: convidar entry point hidden sem write permission (caregiver-roster.feature 13)

**Risks / Notes:**
- O redirect de volta para `/acs` (roster) é mais amigável do que redirecionar para `/`; um coordinator sem
  caregiver.write pode ainda ver o roster. Confirmar o UX com o product owner.
