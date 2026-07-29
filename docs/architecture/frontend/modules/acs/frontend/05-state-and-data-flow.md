# 05 — State and Data Flow — ACS (Frontend)

## 1. State topology

| State | Where | Kind |
|---|---|---|
| Caregiver roster (`CuidadorRowVM[]`) | `libs/acs/data-access` → `AcsCuidadorFacade` | signal, module-local |
| Editor state + draft (`EditorState`, `CuidadorFormVM`) | `AcsCuidadorFacade` (or `CaregiverEditorFacade`) | signal, module-local |
| Linkage set (`Cuidador ↔ Paciente`) | `AcsLinkFacade` | signal, module-local |
| Patient coverage (`PatientCoverageVM`) | `CoverageFacade` (derived: linkage ⋈ caregiver.ativo) | computed |
| Patient names (for linkage panel) | read model from PAC via HTTP | signal, module-local (read model) |
| Active tenant (`TenantVM`) | **written** by ACS `AcsTenantFacade` → `@neuroassist/shared/empresa-context` | shared, global (ACS is writer) |
| Authenticated principal | `@neuroassist/shared/auth-session` | shared, global (read) |

ACS is unusual: it is the **writer** of the shared active-tenant context that the rest of
the app reads. Roster/editor/linkage state is otherwise local to ACS facades; coverage is
derived, never duplicated.

## 2. Data access pattern

```
AcsTenantClient (data-access)
   ├── create(dto): Observable<Tenant>               // POST /tenants
   └── current(): Observable<Tenant | null>          // GET /tenants/active

AcsCuidadorClient (data-access)
   ├── list(): Observable<Cuidador[]>                // GET /cuidadores (X-Empresa-Id)
   ├── invite(draft): Observable<Cuidador>          // POST /cuidadores
   ├── update(id, draft): Observable<Cuidador>      // PUT /cuidadores/{id}
   └── deactivate(id): Observable<void>             // PATCH /cuidadores/{id}:deactivate

AcsLinkClient (data-access)
   ├── listForPatient(pid): Observable<Vinculo[]>   // GET /vinculos?paciente={pid}
   ├── link(cid, pid): Observable<Vinculo>          // POST /vinculos
   └── unlink(cid, pid): Observable<void>           // DELETE /vinculos

PacientesReadClient (data-access)
   └── list(): Observable<Paciente[]>               // GET (PAC-owned) via GWY, for linkage names

AcsCuidadorFacade / AcsLinkFacade / CoverageFacade (data-access)
   - loadRoster(): list() -> signal(roster)
   - saveCaregiver(draft): invite|update -> reload
   - deactivate(id): coverage-guard -> deactivate() -> reload
   - link/unlink(cid, pid): coverage-guard -> mutate -> recompute coverage
   - CoverageFacade.coverageFor(pid): derives coberto|sem-cobertura + isLastCovering

CaregiverRosterComponent / PatientLinkageComponent (feature)
   - read facade signals; dispatch invite/edit/deactivate/link/unlink
```

- Roster/linkage are fetched via **REST on navigation** (no push/stream).
- Any mutation goes through `HttpClient` inside a client → facade emits a signal; no
  `BehaviorSubject` in components.
- Patient names for the linkage panel are a **read model from PAC**; ACS never authors the
  patient record.

## 3. Cache strategy

- **Default = fetch on navigation.** The roster and per-patient linkage are reloaded on
  entering the ACS surface; after any invite/deactivate/link/unlink the facade reloads to
  stay authoritative.
- No client-side caching of the roster across sessions in MVP (small list; access data
  must be fresh — a stale roster could mislead coverage).
- The active tenant, once set, is held in the shared `empresa-context` for the session.

## 4. Optimistic updates

- **Not for coverage.** Coverage (`coberto | sem-cobertura`) is always **recomputed** from
  the authoritative linkage + caregiver-active state after a mutation resolves — never
  optimistically flipped. A patient must never briefly *appear* covered before the link is
  confirmed, nor uncovered before an unlink is confirmed (ADR-005 local).
- **Roster edits** may optimistically insert/update a row for responsiveness, reconciled
  against the server response and **rolled back on error** (draft preserved, error toast).
  The coverage projection is never part of any optimistic step.
- The **coverage guard runs client-side before the request** (fast, honest UX) **and is
  re-enforced by the backend** — the FE block is a courtesy, not the security boundary.

## 5. Error handling

| Failure | Detection | UI |
|---|---|---|
| Roster load error | GET rejects (`ProblemDetail`) | error card + retry; roster not shown stale-as-fresh |
| Invite/save error | `ProblemDetail` | `@neuroassist/shared/error-model` → `AppError` → toast; draft kept |
| Deactivate/unlink blocked (last covering) | client guard + BE 409/422 | inline block with reason "paciente ficaria sem cobertura"; nothing mutated (ADR-005) |
| Link/unlink error | `ProblemDetail` | toast; coverage re-fetched to stay truthful |
| Tenant create error | `ProblemDetail` | wizard step 1 stays; no active tenant set until success |
| Validation (nome empty, papel unset) | synchronous | disable "Salvar"/"Convidar" + inline hint (doc 03 §5) |

Errors never leave a patient's coverage falsely represented; on any linkage/deactivation
error the coverage projection is re-derived from a fresh fetch.

## 6. Breadcrumbs and navigation state

ACS **is** a primary nav destination (Cuidadores / Acesso, coordinator-gated).
`libs/acs/navigation` contributes `BreadcrumbItem[]`: `Cuidadores` (roster) → `Cuidadores
› Convidar` or `Cuidadores › Editar "<nome>"` (editor) → `Cuidadores › Vínculos de
"<paciente>"` (linkage). When ACS's tenant-setup and caregiver-link steps are composed in
the **ACS-owned onboarding wizard** at the `scope:web` page level, the **onboarding shell
page owns the wizard breadcrumb**; the individual step features contribute none, to avoid
duplicate trails (same rule PAC/GEST follow for embedded features).
