## [INT-FE-EVNT-007] Integração com evnt-service — GET /events + GET /events/{id} (histórico + detalhe)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`EvntClient.query(params)` e `EvntClient.get(id)` integram corretamente com os endpoints de histórico e detalhe do `evnt-service`, com paginação básica e mapeamento de `EventoDto` → `EventoVm`.

**Context / Problem statement:**
- `GET /events` (histórico paginado) e `GET /events/{eventoId}` (detalhe) são usados pelo `EventListComponent` e `EventDetailPageComponent`.
- O mapper `EventoDto → EventoVm` (FE-EVNT-009) deve ser validado contra o contrato real do BE.
- `query(params)` com filtros: pelo menos `pacienteId`, `from`, `to` (mínimo para MVP).

**Approach (execution path):**
1) Validar `EvntClient.get(id)` retorna `EventoDto` e mapeia para `EventoVm` (via Facade).
2) Validar `EvntClient.query(params)` serializa os params corretamente em query string.
3) Testar com fixtures do api-contract (não depender do servidor real).
4) Garantir que `confirmedAt` e `confirmedBy` nos campos do `EventoDto` são mapeados para `confirmadoAt` e `confirmadoPor` no `EventoVm` (nomes pt-BR no VM).

**Non-goals:**
- Endpoints de confirmação (INT-FE-EVNT-003).
- SSE (INT-FE-EVNT-002).

**Layer(s):**
frontend / libs/evnt/data-access → evnt-service

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-04-data-access.md` (§1, §2)

**Architecture alignment:**
- Tag Nx: `scope:evnt`, `type:data-access`

**Implementation checklist (by file):**

**Lib data-access**
- [ ] `EvntClient.get(id)` → `GET /api/v1/events/{eventoId}` → `EventoDto`
- [ ] `EvntClient.query(params)` → `GET /api/v1/events?{params}` serializado
- [ ] Mapper `EventoDto → EventoVm`: `confirmadoAt/Por` mapeados corretamente (pt-BR naming)
- [ ] Teste com fixture do api-contract: `EventoDto` completo → `EventoVm` sem campos undefined

**Tests**
- [ ] `nx test evnt-data-access` PASS
- [ ] Unit: `get('evt-1')` → `GET /api/v1/events/evt-1`
- [ ] Unit: `query({ pacienteId: 'p-1' })` → `GET /api/v1/events?pacienteId=p-1`
- [ ] `nx lint evnt-data-access` PASS

**Milestone:**
Wave P4 / Hub integrations

**Labels:**
type:integration
layer:frontend
scope:evnt
priority:P4

**Acceptance criteria:**
- `npx nx test evnt-data-access` PASS
- `EventoDto` fixture completa → `EventoVm` com todos os campos (sem `undefined`)

**Dependencies:**
- FE-EVNT-007 (EvntClient)
- FE-EVNT-009 (DTO + mapper)

**Data impact:**
- Nenhum (read-only)

**API / Contract impact:**
- `GET /api/v1/events` e `GET /api/v1/events/{eventoId}`

**Testing / Evidence required:**
- Unit: fixture de `EventoDto` com `confirmadoEm`/`confirmadoPor` → `EventoVm.confirmadoAt`/`confirmadoPor` corretos

**Risks / Notes:**
- Verificar naming: BE usa `confirmadoEm` ou `confirmadoAt`? Reconciliar com `api-contract.md` antes de implementar o mapper.
