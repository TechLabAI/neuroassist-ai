## [PAC-FE-011] PacComandoFacade — catalog signals + load/mutations + syncState derivation

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/data-access/src/lib/facades/pac-comando.facade.ts` expõe signals de catálogo (`catalog`, `loadState`, `kpis`) e de editor (`editorState`, `draft`, `saveError`) com operações completas de lifecycle (load, save, validate, publish, recalibrate, retire) e derivação honesta de `syncState` que nunca transita para `'synced'` sem EDGE device-ack (ADR-005).

**Context / Problem statement:**
- O facade é o hub central do módulo PAC — todas as leituras de componentes vêm de seus signals.
- `syncState` é um signal DERIVADO (SPEC-04 §3): `catalog ⋈ EDGE device-ack`; enquanto o contrato EDGE não existir (INT-FE-PAC-001), todas as linhas ficam em `'pending'` honest state.
- Idempotência em create: gerar UUID por intenção do usuário e reutilizar em retry (SPEC-04 §4).
- Em erro de save: rollback do otimismo e preservação do draft (SPEC-04 §4 — "on error it rolls back").
- KPIs derivados: `total`, `emergencias`, `pendentesSync` calculados por `computed()` do catálogo (SPEC-03 §1).
- Bloqueia: PAC-FE-014, PAC-FE-015

**Approach (execution path):**
1. Criar facade com 7 signals: `_catalog`, `_loadState`, `_editor` (includes draft, editorState, saveError, syncState), mais computed `kpis` e `canSave`/`canValidate`/`canPublish`/`canRecalibrate`/`canRetire`.
2. `load(pid)`: `_loadState('loading')` → `client.list(pid)` → map DTOs → `_catalog(rows)`, `_loadState('loaded')`. On error → `_loadState('error')`.
3. `save()`: generate/reuse `_idemKey`; `_editor.state('saving')` → `client.create(pid, draft, key)` → `_editor.syncState('pending')` (nunca 'synced'), update catalog row otimisticamente; on error → rollback + `_editor.state('error')`.
4. `validate(cid)`: `client.validate(pid, cid, etag)` → reload catalog → `_editor.estado('validated')`.
5. `publish(cid)`: `client.publish(pid, cid, etag)` → on success: `_editor.estado('published')`, `syncState('pending')`; on 422 emergency-not-detectable: `_editor.state('error')`, `_editor.saveError(appError)`; on 503: retryable error.
6. `recalibrate(cid, tuning)`: `client.recalibrate(pid, cid, tuning, etag)` → `_editor.calibracaoVersao++`, `syncState('pending')`.
7. `retire(cid)`: `client.retire(pid, cid, etag)` → reload.
8. `reset()` e `abortInFlightLoad()` para PAC-FE-005.
9. Unit tests para load, save (success + retry idempotência + error rollback), syncState never-synced, cada lifecycle.

**Non-goals:**
- Sem lógica de UI (routing, toasts) — facade apenas atualiza signals; componentes reagem.
- Sem WebSocket ou polling para EDGE ack — apenas o stub da INT-FE-PAC-001 é consumido.

**Layer(s):**
frontend / libs/pac/data-access

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-04-data-access.md` (§3/§4)
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-03-view-models.md` (§1/§2)
- `docs/architecture/frontend/modules/pac/frontend/adr/ADR-005-command-edit-not-live-until-synced.md`

**Architecture alignment:**
- Signals-first; `computed()` para KPIs e derived booleans
- Tag Nx: `scope:pac`, `type:data-access`

**Implementation checklist (by file):**

**Lib: libs/pac/data-access**
- [ ] `libs/pac/data-access/src/lib/facades/pac-comando.facade.ts`
- [ ] `libs/pac/data-access/src/lib/facades/pac-comando.facade.spec.ts`

**Tests**
- [ ] `npx nx test pac-data-access` PASS (≥12 test cases)
- [ ] `npx nx lint pac-data-access` PASS

**Milestone:**
Wave 2 / P2 Data Access

**Labels:**
type:feature
layer:frontend
scope:pac
priority:P2

**Acceptance criteria:**
- `npx nx test pac-data-access` PASS: `syncState` após `save()` bem-sucedido é `'pending'` (NUNCA `'synced'`).
- `npx nx test pac-data-access` PASS: `save()` retry usa mesmo UUID idempotência.
- `npx nx test pac-data-access` PASS: erro em `save` → catalog rollback, draft preservado.
- `npx nx test pac-data-access` PASS: `kpis().pendentesSync` é contagem dos rows com `syncState === 'pending'`.
- `npx nx test pac-data-access` PASS: `publish()` com 422 emergency-not-detectable → `editorState() === 'error'`, `estado()` permanece `'validated'`.
- `npx nx test pac-data-access` PASS: `canPublish` derivado é `true` apenas quando `estado === 'validated'`.

**Dependencies:**
- PAC-FE-008 (PacComandoClient)
- PAC-FE-013 (ComandoRowVM, EditorVM interfaces)
- INT-FE-PAC-001 (EdgeDeviceAckPort stub — sincState derivation)

**Data impact:**
Empresa-context: `_catalog` e `_editor` são escopados à sessão da empresa ativa; reset em destroy.

**API / Contract impact:**
- Consome todos os 8 métodos do `PacComandoClient`.

**Testing / Evidence required:**
- Signal transition diagrams verificados: idle→loading→loaded, editing→saving→saved+pending, editing→saving→error.

**Risks / Notes:**
- ADR-005 critical: `syncState` deve NUNCA ser `'synced'` enquanto INT-FE-PAC-001 não implementar o device-ack real. O flag `pac.deferredEdgeSync` controla se o badge é exibido (SPEC-05); mas internamente o signal sempre é `'pending'` post-save.
