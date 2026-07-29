# 05 — State and Data Flow — PAC (Frontend)

## 1. State topology

| State | Where | Kind |
|---|---|---|
| Command catalog (`ComandoRowVM[]`) | `libs/pac/data-access` → `PacComandoFacade` | signal, module-local |
| Editor state + draft (`EditorState`, `ComandoFormVM`) | `PacComandoFacade` (or `CommandEditorFacade`) | signal, module-local |
| `TipoGesto` read catalog | `TiposGestoReadFacade` (reads GEST via HTTP) | signal, module-local (read model) |
| Patient (`PacienteVM`) | `PacPacienteFacade` | signal, module-local |
| Per-command sync status | derived signal: catalog ⋈ EDGE device-ack | computed |
| Active tenant / session | `@neuroassist/shared/empresa-context`, `auth-session` | shared, global |
| Device online / ack status | `@neuroassist/shared/*` (EDGE-sourced) | shared |

There is **no global PAC store** — catalog/editor/patient state is local to PAC
facades. Cross-module facts (tenant, device ack) are read from shared libs, never
duplicated.

## 2. Data access pattern

```
PacComandoClient (data-access)
   ├── list(): Observable<Comando[]>                 // GET /comandos (X-Empresa-Id)
   ├── create(draft): Observable<Comando>            // POST /comandos
   ├── update(id, draft): Observable<Comando>        // PUT /comandos/{id}
   └── remove(id): Observable<void>                  // DELETE /comandos/{id}

PacPacienteClient (data-access)
   ├── current(): Observable<Paciente | null>        // GET /pacientes (tenant-scoped)
   └── create(dto): Observable<Paciente>             // POST /pacientes

TiposGestoReadClient (data-access)
   ├── list(): Observable<TipoGesto[]>               // GET (GEST-owned) via GWY
   └── requestNew(nome): Observable<TipoGesto>       // POST new-type request → GEST

PacComandoFacade (data-access)
   - load(): list() -> signal(catalog)
   - save(draft): create|update -> reload -> editor state 'saved (pending sync)'
   - remove(id): remove() -> reload
   - maps Comando -> ComandoRowVM (resolves tipo names, derives tested/sync flags)

CommandCatalogComponent / CommandEditorComponent (feature)
   - read facade signals; dispatch new/edit/save/remove
   - editor renders preview + hosts GEST live-test slot (composed at shell page)
```

- Catalog is fetched via **REST on navigation** (no push/stream — unlike GEST).
- Any mutation goes through `HttpClient` inside a client → facade emits a signal;
  no `BehaviorSubject` in components.
- The `TipoGesto` new-type request is **delegated to GEST** (PAC does not author the
  primitive catalog); PAC only selects the returned id.

## 3. Cache strategy

- **Default = fetch on navigation.** The catalog is reloaded when entering
  `calibração`; after any save/remove the facade reloads to stay authoritative.
- No client-side caching of the catalog across sessions in MVP (small list, safety
  data must be fresh). A short in-memory hold during an editing session is fine, but
  the source of truth is the last successful GET.
- The `TipoGesto` read model may be held for the editing session (rarely changes),
  refreshed after an inline add.

## 4. Optimistic updates

- **Not for sync state.** A saved command is shown `pendente de sincronização`
  until the device acknowledges it — never optimistically `ativo` (ADR-005 local).
- **Editor save** may optimistically insert/update the row in the local catalog
  signal for responsiveness, but the row is reconciled against the server response
  and **rolled back on error** (draft preserved, error toast). The *sync* flag is
  never part of any optimistic step.

## 5. Error handling

| Failure | Detection | UI |
|---|---|---|
| Catalog load error | GET rejects (`ProblemDetail`) | error card + retry; catalog not shown stale-as-fresh |
| Save error (create/update) | `ProblemDetail` | `@neuroassist/shared/error-model` → `AppError` → toast; draft kept, nothing marked saved |
| Remove error | `ProblemDetail` | toast; row restored |
| Tipo-gesto add error | request rejects | inline error; selection unchanged |
| Validation (rótulo empty, tempo min≥max, dup composite) | synchronous | disable "Salvar" + inline hint (doc 03 §5) |
| Device never acks | EDGE status stays pending | command remains `pendente de sincronização` (honest, not an error) |

Errors never leave a command falsely marked as saved or active.

## 6. Breadcrumbs and navigation state

PAC **is** a primary nav destination (Calibração / Catálogo). `libs/pac/navigation`
contributes `BreadcrumbItem[]`: `Calibração` (list) → `Calibração › Novo comando`
or `Calibração › Editar "<rótulo>"` (editor). When the patient-register feature is
composed inside the ACS-owned onboarding wizard at the `scope:web` page level, the
**onboarding shell page** owns the wizard breadcrumb; PAC's patient step contributes
none, to avoid duplicate trails (same rule GEST follows for its embedded feature).
