## [FE-ACS-010] AcsCuidadorFacade (roster + editor signals + idempotency + optimistic rollback)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`AcsCuidadorFacade` (injectable, root-scoped within ACS) manages roster and editor state as readonly signals.
`loadRoster()` fetches and maps caregivers to `CuidadorRowVM[]`. `saveCaregiver(draft)` carries an
idempotency-key and optimistically inserts a row, rolling back on error (draft preserved). `deactivate(id)`
runs coverage-guard (isLastCovering?) before calling the client.

**Context / Problem statement:**
- SPEC-04 §3 and SPEC-03 §1-2 define the facade contract and view model shapes.
- SPEC-04 §4 defines idempotency: the facade generates one UUID per user-initiated create; retries reuse it.
- Optimistic update: roster MAY optimistically insert/update for responsiveness — rolled back on error.
- Coverage is NEVER optimistic (delegated to CoverageFacade, FE-ACS-012); `isLastCovering` comes from
  the derived coverage join.
- Bloqueia: FE-ACS-013 (CaregiverRosterComponent reads this facade), FE-ACS-016 (editor reads editorState)

**Approach (execution path):**
1. Create `libs/acs/data-access/src/lib/cuidador/acs-cuidador.facade.ts`.
2. Private signals: `_roster`, `_loadState`, `_editor (CaregiverEditorVM)`, `_saveError`.
3. Inject `AcsCuidadorClient`, `AcsLinkClient` (for roster join), `CoverageFacade`.
4. `loadRoster()`: call `client.list()`, map `Cuidador → CuidadorRowVM` (join with link signal + coverageFacade).
5. `saveCaregiver(draft)`: generate/reuse `Idempotency-Key`; call client.create; on success reload roster +
   set `EditorState = 'saved'`; on error rollback optimistic row + preserve draft + set `'error'`.
6. `deactivate(id)`: check `isLastCovering` from coverage signal; if true → throw blocking (do not call client);
   else call client.deactivate → reload roster → recompute coverage.
7. Expose `reset()` for bootstrap destroy (clears signals, cancels in-flight subscriptions).
8. Unit tests: all SPEC-04 EARS clauses (idempotency-key reuse, optimistic rollback, coverage guard block).

**Non-goals:**
- No coverage derivation (CoverageFacade, FE-ACS-012).
- No link mutations (AcsLinkFacade, FE-ACS-011).

**Layer(s):**
frontend / libs/acs/data-access

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-04-data-access.md` (§3 AcsCuidadorFacade, §4)
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-03-view-models.md` (§1-2)

**Architecture alignment:**
- Signals-first: `signal<CuidadorRowVM[]>`, `signal<RosterLoadState>`, `signal<CaregiverEditorVM>`
- `toSignal()` wrapping observable chains; `takeUntilDestroyed` for cleanup
- Tags Nx: `scope:acs`, `type:data-access`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/data-access/`
- 🔲 `src/lib/cuidador/acs-cuidador.facade.ts` — full facade implementation
- 🔲 `src/lib/cuidador/cuidador.vm.ts` — CuidadorRowVM, CaregiverEditorVM, RosterKpiVM (derived)

**Tests**
- 🔲 `src/lib/cuidador/acs-cuidador.facade.spec.ts`
  - loadRoster: maps Cuidador→CuidadorRowVM
  - saveCaregiver: idempotency-key reused on retry; optimistic rollback on error
  - deactivate: coverage guard blocks last-covering; proceeds for non-last
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
- Retry de save com mesmo draft → mesmo Idempotency-Key
- Optimistic insert rejeitado pelo BE → rollback + draft preservado
- `deactivate(lastCoveringId)` → lança sem chamar o cliente; cuidador permanece ativo
- `reset()` limpa todos os signals e aborta chamadas in-flight

**Dependencies:**
- FE-ACS-008 (AcsCuidadorClient)
- FE-ACS-009 (AcsLinkClient para roster join)
- FE-ACS-012 (CoverageFacade — injetado para isLastCovering)

**Data impact:**
- Nenhum (FE não persiste)

**API / Contract impact:**
- Consume as APIs de FE-ACS-008 via client

**Testing / Evidence required:**
- Testes de unit (facade spec) passando para todos os cenários SPEC-04 §4
- E2E cenários: invite com idempotency (caregiver-roster.feature 7), deactivate guard (11-12)

**Risks / Notes:**
- O `isLastCovering` é um `computed()` que depende de `CoverageFacade` — a ordem de injeção e o timing de
  inicialização do coverage signal precisam ser verificados (facade pode ser instanciado antes do coverage).
- Rollback optimista requer manter uma snapshot do roster antes da mutação.
