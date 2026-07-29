## [PAC-FE-012] TiposGestoReadFacade — tipos signal + load/addNew

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/data-access/src/lib/facades/tipos-gesto-read.facade.ts` expõe `tipos: Signal<TipoGestoVM[]>` com os métodos `load()` e `addNew(nome)`, onde `addNew` delega ao GEST via `TiposGestoReadClient.requestNew` e seleciona **apenas o id retornado** para o editor (SPEC-04 §3).

**Context / Problem statement:**
- O `GesturePickerComponent` (PAC-FE-015) lê `TiposGestoReadFacade.tipos()` para popular o dropdown de primitivos.
- `addNew(nome)` é chamado pelo affordance "+ novo tipo" (feature flag `pac.inlineNewTipoGesto=on`); o GEST cria o tipo e retorna o `TipoGesto`; o facade appende à lista e retorna o `id` para o editor selecionar.
- A degradação graciosamente: se o GEST endpoint estiver fora do ar, `tipos()` retorna lista vazia e o editor mostra apenas os tipos já carregados (SPEC-09 TiposGesto fallback).

**Approach (execution path):**
1. Criar `libs/pac/data-access/src/lib/facades/tipos-gesto-read.facade.ts`.
2. `private _tipos = signal<TipoGestoVM[]>([]); tipos = this._tipos.asReadonly()`.
3. `load()`: `client.list()` → map DTO→VM → `_tipos(vms)`. On error → `_tipos([])` (degradação silenciosa + log).
4. `addNew(nome: string): Observable<string>` (retorna `id`): `client.requestNew(nome)` → `map(t => t.id)` → `tap(id => _tipos.update(list => [...list, mapToVM(t)]))`.
5. `reset()`: `_tipos([])`.
6. Unit tests: load + addNew (sucesso e falha).

**Non-goals:**
- Sem gerenciamento do lifecycle do TipoGesto (supported/pending-training) — GEST owns it; o facade apenas exibe o estado retornado.

**Layer(s):**
frontend / libs/pac/data-access

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-04-data-access.md` (§3 TiposGestoReadFacade)

**Architecture alignment:**
- Signals-first
- Tag Nx: `scope:pac`, `type:data-access`
- Sem import de `scope:gest`

**Implementation checklist (by file):**

**Lib: libs/pac/data-access**
- [ ] `libs/pac/data-access/src/lib/facades/tipos-gesto-read.facade.ts`
- [ ] `libs/pac/data-access/src/lib/facades/tipos-gesto-read.facade.spec.ts`

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
- `npx nx test pac-data-access` PASS: `addNew('Piscar')` → `_tipos` contém o novo tipo; retorna seu `id`.
- `npx nx test pac-data-access` PASS: erro em `load()` → `tipos()` retorna `[]` (sem throw).
- `npx nx lint pac-data-access` PASS: sem import `scope:gest`.

**Dependencies:**
- PAC-FE-009 (TiposGestoReadClient)
- PAC-FE-013 (TipoGestoVM)
- INT-FE-PAC-006 (wiring GEST via GWY)

**Data impact:**
Nenhum — leitura e requisição de criação delegada ao GEST.

**API / Contract impact:**
- Consome `GET /api/gest/v1/tipos-gesto` e `POST /api/gest/v1/tipos-gesto`.

**Testing / Evidence required:**
- Signal: `load()` → `tipos()` não vazio; `addNew()` → list append; erro → `[]`.

**Risks / Notes:**
- `requestNew` pode demorar (GEST training pipeline); a UI deve mostrar loading state no `GesturePicker` durante a chamada.
