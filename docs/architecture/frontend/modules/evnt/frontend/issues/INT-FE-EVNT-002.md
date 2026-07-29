## [INT-FE-EVNT-002] Integração com evnt-service — GET /events/active + GET /events/stream

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`EvntClient` integra corretamente com o `evnt-service` nos endpoints de hidratação (`GET /events/active`) e canal live (`GET /events/stream` SSE), com headers `X-Empresa-Id` + `Authorization` adicionados por `access-http-base`.

**Context / Problem statement:**
- A hidratação inicial (`GET /events/active`) é crítica: um evento de emergência ativo deve ser re-exibido após reload.
- O SSE (`GET /events/stream`) deve incluir `Authorization` (JWT) — validar que `access-http-base` ou o interceptor de SSE adiciona o header.
- Contrato autoritativo: `docs/architecture/backend/services/evnt-service/contracts/api-contract.md`.

**Approach (execution path):**
1) Validar `access-http-base` interceptor para requests REST (já cobre `HttpClient`).
2) Para SSE (`EventSource`): `EventSource` nativo não suporta headers customizados — usar `fetch()` com `ReadableStream` ou uma biblioteca compatível.
3) Confirmar com o time BE que `Authorization` header é aceito no SSE endpoint.
4) Testar integração com mock do `evnt-service` (contrato fixo do `api-contract.md`).

**Non-goals:**
- Endpoints de confirmação (INT-FE-EVNT-003).
- Lógica de degradação (FE-EVNT-010).

**Layer(s):**
frontend / libs/evnt/data-access → evnt-service

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-04-data-access.md` (§1, §3)

**Architecture alignment:**
- `access-http-base`: `X-Empresa-Id` + `Authorization` em todas as chamadas
- Tag Nx: `scope:evnt`, `type:data-access`

**Implementation checklist (by file):**

**Lib data-access**
- [ ] `EvntClient.listActive()` usa `access-http-base` → `X-Empresa-Id` presente
- [ ] `EvntClient.openStream()` inclui `Authorization` header (via fetch/polyfill, não EventSource nativo)
- [ ] Teste com mock do servidor: `GET /api/v1/events/active` → `ActiveEventsDto` parseado corretamente
- [ ] Teste: SSE frame `evento.created` → Facade recebe e atualiza `activeEvent`

**Tests**
- [ ] `nx test evnt-data-access` PASS
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
- `Authorization` presente em request SSE (verificado via test mock)
- `ActiveEventsDto` com `activeEvent` não-null → `alarmVm.visible=true`

**Dependencies:**
- FE-EVNT-007 (EvntClient)
- FE-EVNT-008 (EventDeliveryFacade)

**Data impact:**
- Nenhum

**API / Contract impact:**
- `GET /api/v1/events/active` e `GET /api/v1/events/stream`

**Testing / Evidence required:**
- Unit: mock `GET /events/active` com `activeEvent` → `alarmVm.visible` torna-se `true`

**Risks / Notes:**
- `EventSource` não suporta headers — decisão técnica: usar `fetch` com chunked encoding ou biblioteca como `eventsource` npm com headers. Confirmar escolha com o time.
