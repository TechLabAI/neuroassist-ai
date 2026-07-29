## [FE-ACS-012] CoverageFacade (derived computed signals — coverageFor/coverageByPatient, ADR-005)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`CoverageFacade` exposes two computed signals: `coverageFor(pid)` (per-patient coverage) and
`coverageByPatient()` (map over all pacientes). Coverage is derived from `active links ⋈ active caregivers` —
a caregiver with `status=inactive` does NOT count as coverage even if linked (ADR-005). Coverage is never stored
as a mutable flag; it is a pure computed projection.

**Context / Problem statement:**
- SPEC-04 §3 defines `CoverageFacade`; SPEC-03 §3 defines `PatientCoverageVM`.
- ADR-005 (coverage invariant): a paciente sem coverage is a safety defect — the UI must derive coverage
  from `active` links ⋈ `active` caregivers, never guess or optimistically flip it.
- `isLastCovering(cid, pid)` is a key derived boolean used by both `AcsCuidadorFacade.deactivate` and
  `AcsLinkFacade.unlink` to enforce the coverage guard.
- `coverageByPatient()` feeds `CoverageAlertBarComponent` (SPEC-02 §1) and `RosterKpiVM.pacientesSemCobertura`.
- Bloqueia: FE-ACS-010, FE-ACS-011, FE-ACS-013, FE-ACS-018 (all read coverage)

**Approach (execution path):**
1. Create `libs/acs/data-access/src/lib/coverage/coverage.facade.ts`.
2. Inject `AcsCuidadorFacade` (roster signal), `AcsLinkFacade` (links signal), `PacientesReadFacade` (names).
3. Declare `private _pacientes = signal<Paciente[]>([])` (loaded from `PacientesReadClient`).
4. `coverageFor(pid): Signal<PatientCoverageVM>` — `computed()` reading roster + links for pid.
   - Filter: `links.filter(l => l.cuidadorStatus === 'active')` → `covering` count.
   - `state = covering > 0 ? 'coberto' : 'sem-cobertura'`.
5. `coverageByPatient(): Signal<Map<string, PatientCoverageVM>>` — `computed()` over all pacientes.
6. `isLastCovering(cid, pid): boolean` — `coverageFor(pid)().covering === 1 && linkIncludes(cid, pid)`.
7. `recompute()`: triggers a fresh fetch of links/caregivers (called by facades after mutations).
8. `reset()`: clears `_pacientes`, cancels subscriptions.
9. Unit tests: inactive caregiver excluded from coverage count; sem-cobertura triggers; isLastCovering.

**Non-goals:**
- No mutation logic (AcsLinkFacade, AcsCuidadorFacade own mutations).
- No optimistic coverage (always from fresh data post-mutation).

**Layer(s):**
frontend / libs/acs/data-access

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-04-data-access.md` (§3 CoverageFacade)
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-03-view-models.md` (§3 PatientCoverageVM)
- `docs/architecture/frontend/modules/acs/frontend/adr/ADR-005-no-patient-without-coverage.md`

**Architecture alignment:**
- `computed()` signals — never a stored mutable flag; re-evaluated on signal changes
- Tags Nx: `scope:acs`, `type:data-access`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/data-access/`
- 🔲 `src/lib/coverage/coverage.facade.ts` — CoverageFacade with computed signals
- 🔲 `src/lib/coverage/coverage.vm.ts` — PatientCoverageVM
- 🔲 `src/index.ts` — re-exports

**Tests**
- 🔲 `src/lib/coverage/coverage.facade.spec.ts`
  - Inactive linked caregiver excluded from coverage count
  - Patient with 0 active links → sem-cobertura
  - isLastCovering(cid, pid) with covering=1
  - coverageByPatient() maps all pacientes
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
- Cuidador `status=inactive` vinculado → `covering` não incrementado → paciente sem-cobertura se for o único
- `isLastCovering(cid, pid)` retorna `true` apenas se `covering === 1` e `cid` está no link
- `coverageByPatient()` inclui todos os pacientes (inclusive os sem-cobertura)
- E2E: inactive caregiver excluded from coverage (caregiver-roster.feature coverage derivation)

**Dependencies:**
- FE-ACS-008 (cuidador DTOs e client)
- FE-ACS-009 (link DTOs e client)
- INT-FE-ACS-005 (PacientesReadClient/Facade para nomes)

**Data impact:**
- Nenhum (leitura pura derivada)

**API / Contract impact:**
- Leitura indireta via roster e links signals (nenhuma chamada direta de cobertura)

**Testing / Evidence required:**
- Testes de unit: exclusão de inativo; sem-cobertura trigger; isLastCovering
- E2E: paciente com cuidador inativo marcado sem-cobertura

**Risks / Notes:**
- **Gap de alinhamento (contracts/backend-api-cross-link.md §5):** a definição exata de "alarm-eligible"
  (coverage) entre FE, BE (ACS), e EVNT ainda não está congelada. No MVP: `active link ⋈ active caregiver`.
  Este gap deve ser rastreado e o CoverageFacade ajustado quando o contrato for congelado com EVNT.
