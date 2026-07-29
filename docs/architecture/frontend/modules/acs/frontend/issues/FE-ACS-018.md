## [FE-ACS-018] PatientLinkageComponent (smart — links/candidates + coverage re-derivation)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`PatientLinkageComponent` (standalone, OnPush) manages the `/acs/pacientes/:pacienteId/vinculos` route.
It reads four signals from `AcsLinkFacade` and `CoverageFacade`, dispatches load/link/unlink/retry, and
ensures coverage is re-derived from a fresh fetch after every mutation (never optimistic, ADR-005).

**Context / Problem statement:**
- SPEC-02 §3 defines the component tree; four signals: `linksForPatient`, `candidates`, `coverageFor`, `mutateState`.
- `PatientCoverageHeaderComponent` always shows coverage state (danger if sem-cobertura).
- `LinkedCaregiverRowComponent.unlink` is disabled when `row.isLastCovering` (ADR-005).
- The paciente ID comes from the route param `:pacienteId`.
- Bloqueia: FE-ACS-019 (presentational sub-components rendered here)

**Approach (execution path):**
1. Create `libs/acs/feature-patient-linkage/src/lib/patient-linkage.component.ts` (standalone, OnPush).
2. Inject `AcsLinkFacade`, `CoverageFacade`, `ActivatedRoute`.
3. On init: read `:pacienteId` from route → `facade.loadForPatient(pid)`.
4. Bind signals to sub-components; pass `(link)` and `(unlink)` to the list components.
5. `link(cid)`: call `acsLinkFacade.link(cid, pid)` → coverage re-derived automatically by facade.
6. `unlink(cid)`: call `acsLinkFacade.unlink(cid, pid)` → facade checks isLastCovering → coverage re-derived.
7. Error: `LinkageErrorCardComponent` shown with retry.
8. Unit tests: load on init; link/unlink dispatch; unlink guard; error card + retry.

**Non-goals:**
- No caregiver creation (FE-ACS-016).
- No roster management.

**Layer(s):**
frontend / libs/acs/feature-patient-linkage

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§3)
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-03-view-models.md` (§3 VinculoRowVM, LinkageMutateState)

**Architecture alignment:**
- Standalone, OnPush, Signals; coverage re-derived (never optimistic per ADR-005)
- Tags Nx: `scope:acs`, `type:feature`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/feature-patient-linkage/`
- 🔲 `src/lib/patient-linkage.component.ts` — smart, 4 signals, link/unlink dispatch
- 🔲 `src/lib/patient-linkage.component.html` — template: coverage header + linked list + candidate list + error
- 🔲 `src/index.ts` — re-exports component

**Tests**
- 🔲 `src/lib/patient-linkage.component.spec.ts`
  - loadForPatient on init
  - link: facade.link called; coverage re-derived
  - unlink non-last: facade.unlink called
  - unlink last: guard blocks (facade handles, component shows blocking modal)
  - error → retry
- 🔲 `nx test acs-feature-patient-linkage` PASS
- 🔲 `nx lint acs-feature-patient-linkage` PASS

**Milestone:**
Wave 3 / P3 Feature libs

**Labels:**
type:feature
layer:frontend
scope:acs
priority:P3

**Acceptance criteria:**
- `npx nx test acs-feature-patient-linkage` PASS
- `npx nx lint acs-feature-patient-linkage` PASS
- `npx nx build acs-feature-patient-linkage` PASS
- Link: coverage re-derivada do fresh fetch (não flip otimista)
- Unlink last-covering: modal bloqueante (coverage guard); vinculo unchanged
- Cobertura visual: SPEC-02 §3 ASCII tree replicado em DOM

**Dependencies:**
- FE-ACS-011 (AcsLinkFacade)
- FE-ACS-012 (CoverageFacade)
- FE-ACS-019 (presentational sub-components)
- FE-ACS-027 (blocking modal para last-covering + cross-tenant)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Via AcsLinkFacade: `POST /caregivers/{cid}/links` e `DELETE /caregivers/{cid}/links/{linkId}`

**Testing / Evidence required:**
- Screenshot: coverage header "Coberto (n)" e "Sem cobertura"
- Screenshot: unlink último → modal bloqueante

**Risks / Notes:**
- A lista de candidatos (caregivers não vinculados) é derivada do diff roster - links. Se o roster não estiver
  carregado quando a página abre, o AcsLinkFacade deve disparar um fetch.
