## [PAC-FE-010] PacPacienteFacade — patient signal + loadCurrent/save

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/data-access/src/lib/facades/pac-paciente.facade.ts` expõe os signals `patient: Signal<PacienteVM | null>` e `saveState: Signal<PatientSaveState>` com os métodos `loadCurrent()`, `save(dto)` e `reset()`, implementando idempotência em create e preservando o draft em caso de erro (SPEC-04 §3/§4).

**Context / Problem statement:**
- O `PacPacienteFacade` é usado pelo `PatientRegisterComponent` (PAC-FE-016) e pelo `pac.bootstrap.ts` (PAC-FE-005) para priming do guard.
- `loadCurrent()` chama `PacPacienteClient.list(size=1)` e guarda o primeiro resultado em `patient` signal.
- `save(dto)` gera um `Idempotency-Key` (UUID) por chamada do usuário e o reutiliza em retry (armazenado localmente até que o save seja bem-sucedido ou cancelado — SPEC-04 §4).
- Em erro, o draft é preservado e `saveState` vai para `'error'` (SPEC-04 §4 / SPEC-07).
- `reset()` é chamado pelo `pac.bootstrap.ts` destroy — limpa `patient=null`, `saveState='idle'`.

**Approach (execution path):**
1. Criar `libs/pac/data-access/src/lib/facades/pac-paciente.facade.ts` com `@Injectable({ providedIn: 'root' })`.
2. `private _patient = signal<PacienteVM | null>(null); patient = this._patient.asReadonly()`.
3. `private _saveState = signal<PatientSaveState>('idle'); saveState = this._saveState.asReadonly()`.
4. `loadCurrent()`: `_saveState('idle')` → `PacPacienteClient.list(1)` → map `Paciente → PacienteVM` → `_patient(vm)`.
5. `save(dto)`: gerar ou reutilizar `_idemKey`; `_saveState('saving')` → `client.create(dto, key)` → on success: `_patient(mapToVM(resp.body))`, `_saveState('saved')`, `_idemKey = null`; on error: `_saveState('error')`, draft preservado (não tocar no signal `patient`).
6. `reset()`: `_patient(null)`, `_saveState('idle')`, `_idemKey = null`.
7. Unit tests: mock `PacPacienteClient`, assert signal transitions.

**Non-goals:**
- Sem paginação exposta (PAC usa apenas o primeiro paciente por empresa — `size=1`).
- Sem cache além do signal em memória.

**Layer(s):**
frontend / libs/pac/data-access

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-04-data-access.md` (§3)
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-03-view-models.md` (§3 PacienteVM)

**Architecture alignment:**
- Signals-first; nenhum `BehaviorSubject`
- Tag Nx: `scope:pac`, `type:data-access`

**Implementation checklist (by file):**

**Lib: libs/pac/data-access**
- [ ] `libs/pac/data-access/src/lib/facades/pac-paciente.facade.ts`
- [ ] `libs/pac/data-access/src/lib/facades/pac-paciente.facade.spec.ts`

**Tests**
- [ ] `npx nx test pac-data-access` PASS
- [ ] `npx nx lint pac-data-access` PASS

**Milestone:**
Wave 2 / P2 Data Access

**Labels:**
type:feature
layer:frontend
scope:pac
priority:P2

**Acceptance criteria:**
- `npx nx test pac-data-access` PASS: `loadCurrent()` → `patient()` contém `PacienteVM`.
- `npx nx test pac-data-access` PASS: `save(dto)` em retry usa mesmo `Idempotency-Key`.
- `npx nx test pac-data-access` PASS: erro em `save` → `saveState() === 'error'`; `patient()` permanece `null`.
- `npx nx test pac-data-access` PASS: `reset()` → `patient() === null`, `saveState() === 'idle'`.

**Dependencies:**
- PAC-FE-007 (PacPacienteClient)
- PAC-FE-013 (PacienteVM interface)

**Data impact:**
Empresa-context: `patient` signal é escopado à sessão da empresa ativa (reset em destroy).

**API / Contract impact:**
- `save` → `POST /api/pac/v1/pacientes` (201) com `Idempotency-Key`.

**Testing / Evidence required:**
- Signal transitions: `idle → saving → saved` (success path); `idle → saving → error` (error path).

**Risks / Notes:**
- Race condition: se `loadCurrent()` é chamado duas vezes em paralelo (guard + manual), a segunda resposta pode sobrescrever a primeira. Usar `switchMap` ou `takeUntil` para cancelar o em-flight.
