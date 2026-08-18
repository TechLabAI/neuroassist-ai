## [FE-DASH-006] DashHomeFacade — signals summary/state/calm + mapeamento DTO→VM

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/dash/data-access/src/lib/dash-home.facade.ts` expõe sinais readonly (`summary`, `state`, `calm`) e métodos de UI (`reloadSummary()`, `viewHistory(patientId)`), mapeando `SummaryDto → HomeSummaryVm`. O sinal `calm` é derivado (`computed`) de `activeEvent` injetado no scope:web.

**Context / Problem statement:**
- `HomeStatusPageComponent` consome apenas signals; sem BehaviorSubject, sem NgRx.
- `calm` deve derivar de EVNT `activeEvent` (não do aggregate) — invariante de segurança: um "está bem" falso é o pior defeito (SPEC-03, ADR-005).
- `state='error'` degrada apenas o StatusCard; o shell chrome permanece intacto.
- Fonte: SPEC-03 §1; SPEC-04 §1.
- Bloqueia: FE-DASH-011, FE-DASH-012, FE-DASH-022

**Approach (execution path):**
1. Criar `DashHomeFacade` com `@Injectable({ providedIn: 'root' })`.
2. Signals internos: `_summary = signal<HomeSummaryVm | null>(null)`, `_state = signal<LoadState>('loading')`.
3. Sinal `activeEvent` recebido via token de injeção (`InjectionToken<Signal<ActiveEventVm | null>>`) — wirings no scope:web (INT-FE-DASH-001).
4. `calm = computed(() => ...)` usando `activeEvent()` — false iff há emergência não confirmada.
5. Método `reloadSummary()`: chama `DashApiService.getSummary()`, mapeia `SummaryDto → HomeSummaryVm`, atualiza `_summary` e `_state`.
6. Método `viewHistory(patientId)`: emite sinal/evento para o shell router navegar para HIST (cross-module link; não importa scope:hist).
7. Expor signals como `.asReadonly()`.

**Non-goals:**
- Não persistir state em localStorage.
- Não implementar cache de summary (FE 05 §3: sempre fresh no Home entry).

**Layer(s):**
frontend / libs/dash/data-access (type:data-access)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-03-view-models.md` (§1)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-04-data-access.md` (§1)

**Architecture alignment:**
- Signals-first; sem NgRx; sem BehaviorSubject em componentes
- Tag Nx: `scope:dash`, `type:data-access`

**Implementation checklist (by file):**

**Lib: libs/dash/data-access**
- 🔲 `libs/dash/data-access/src/lib/dash-home.facade.ts`
- 🔲 `libs/dash/data-access/src/lib/dash-home.mapper.ts` (SummaryDto → HomeSummaryVm)
- 🔲 `libs/dash/data-access/src/lib/tokens/active-event.token.ts` (InjectionToken<Signal<ActiveEventVm | null>>)
- 🔲 `libs/dash/data-access/src/index.ts` (barrel)

**Tests**
- 🔲 `npx nx test dash-data-access` PASS (testar: reloadSummary atualiza state; calm=false quando emergência ativa; calm=true quando sem emergência)
- 🔲 `npx nx lint dash-data-access` PASS

**Milestone:**
Wave 2 / Data Access

**Labels:**
type:data-access
layer:frontend
scope:dash
priority:P2

**Acceptance criteria:**
- `npx nx test dash-data-access` PASS
- `npx nx lint dash-data-access` PASS
- `npx nx build dash-data-access` PASS
- `calm` é `false` quando `activeEvent().urgencia === 'emergencia' && activeEvent().confirmadoEm === null`
- `state` vai de `'loading'` → `'ok'` após sucesso; → `'error'` após falha de rede
- `reloadSummary()` dispara nova chamada HTTP (não usa cache)

**Dependencies:**
- FE-DASH-005 (DashApiService)
- INT-FE-DASH-001 (wiring do activeEvent no scope:web — token precisa existir)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Consome `GET /panel/summary` via DashApiService

**Testing / Evidence required:**
- Test unitário com Signal mock do `activeEvent`
- Test verifica que `viewHistory` não importa `scope:hist`

**Risks / Notes:**
- O token `ACTIVE_EVENT_TOKEN` precisa ser providenciado no scope:web (INT-FE-DASH-001); em testes unitários de DASH, mockar com `signal(null)`.
