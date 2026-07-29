## [FE-ACS-011] AcsLinkFacade (links/candidates loading + link/unlink mutation + coverage guard)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`AcsLinkFacade` manages the patient linkage panel state: `linksForPatient` (active links for a given paciente),
`candidates` (unlinked caregivers), and `mutateState`. `link(cid, pid)` is safe to add (no coverage risk).
`unlink(cid, pid)` checks `isLastCovering` before calling the client. After any mutation, coverage is
re-derived from a fresh fetch (never optimistic, ADR-005).

**Context / Problem statement:**
- SPEC-04 §3 defines the facade contract; SPEC-03 §3 defines `VinculoRowVM` and `LinkageMutateState`.
- Coverage is re-derived after every link/unlink (fresh fetch + `CoverageFacade.recompute`), never optimistically
  flipped (ADR-005 §4).
- `loadForPatient(pid)` joins `listForPatient` result with roster diff to produce `_links` + `_candidates`.
- `unlink` checks `isLastCovering` (coverage guard) before making the DELETE request.
- Bloqueia: FE-ACS-018 (PatientLinkageComponent reads this facade)

**Approach (execution path):**
1. Create `libs/acs/data-access/src/lib/link/acs-link.facade.ts`.
2. Private signals: `_links`, `_candidates`, `_mutateState`.
3. Inject `AcsLinkClient`, `AcsCuidadorFacade` (roster for diff), `CoverageFacade`.
4. `loadForPatient(pid)`: call `client.listForPatient(pid)` + roster diff → populate `_links`/`_candidates`.
5. `link(cid, pid)`: call `client.link(cid, pid)` → reload `_links`/`_candidates` → `CoverageFacade.recompute()`.
6. `unlink(cid, pid)`: check `isLastCovering(cid, pid)` from coverage; if true → emit blocking error (no client
   call); else call `client.revoke(cid, linkId)` → reload → `CoverageFacade.recompute()`.
7. `reset()`: clears signals for bootstrap destroy.
8. Unit tests: link (safe to add), unlink coverage guard block, unlink success + recompute, error + re-fetch.

**Non-goals:**
- No coverage derivation logic (CoverageFacade, FE-ACS-012).
- No roster management (AcsCuidadorFacade, FE-ACS-010).

**Layer(s):**
frontend / libs/acs/data-access

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-04-data-access.md` (§3 AcsLinkFacade, §4)
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-03-view-models.md` (§3)

**Architecture alignment:**
- Signals-first; coverage re-derived (never optimistic); `takeUntilDestroyed` for cleanup
- Tags Nx: `scope:acs`, `type:data-access`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/data-access/`
- 🔲 `src/lib/link/acs-link.facade.ts` — AcsLinkFacade
- 🔲 `src/lib/link/link.vm.ts` — VinculoRowVM, LinkageMutateState
- 🔲 `src/index.ts` — re-exports

**Tests**
- 🔲 `src/lib/link/acs-link.facade.spec.ts`
  - link: calls client.link, reloads, recomputes coverage
  - unlink last-covering: guard blocks, no client call, vinculo unchanged
  - unlink non-last: revoke called, coverage recomputed from fresh fetch
  - error: coverage re-fetched truthfully
- 🔲 `nx test acs-data-access` PASS
- 🔲 `nx lint acs-data-access` PASS

**Milestone:**
Wave 2 / P2 Data Access

**Labels:**
type:feature
layer:frontend
scope:acs
priority:P2

**Acceptance criteria:**
- `npx nx test acs-data-access` PASS
- `npx nx lint acs-data-access` PASS
- `unlink(lastCoveringCid, pid)` → não chama cliente; vinculo inalterado; coverage re-fetched
- Após `link(cid, pid)` sucesso → `CoverageFacade.recompute()` chamado (não otimismo)
- E2E scenarios patient-linkage.feature 4-9 suportados por esta facade

**Dependencies:**
- FE-ACS-009 (AcsLinkClient)
- FE-ACS-010 (AcsCuidadorFacade, para roster diff em loadForPatient)
- FE-ACS-012 (CoverageFacade, injetado para isLastCovering + recompute)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Consume APIs de FE-ACS-009 via client

**Testing / Evidence required:**
- Testes de unit passando
- E2E: unlink last-covering blocked + vinculo unchanged (patient-linkage.feature 5-6)

**Risks / Notes:**
- Recomputing coverage após cada mutação via fresh fetch pode ser lento. No MVP é aceitável.
  Sinalizar como área para otimização (event-driven no futuro).
