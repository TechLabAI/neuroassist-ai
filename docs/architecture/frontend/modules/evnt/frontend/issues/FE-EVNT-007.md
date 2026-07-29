## [FE-EVNT-007] EvntClient — REST + SSE live channel com Last-Event-ID

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/evnt/data-access/src/lib/evnt.client.ts` implementa `EvntClient` com todos os métodos da tabela SPEC-04 §1: `openStream()`, `listActive()`, `get(id)`, `confirm(id, verdict)`, `ack(id, dispatchId)`, `query(params)`. O `openStream()` retorna frames SSE e envia `Last-Event-ID` em reconexão.

**Context / Problem statement:**
- SPEC-04 §1 define 6 recursos com endpoint, operação e cliente mapeados.
- SSE-01 gap (M3.5): ao reconectar o stream, o cliente DEVE enviar `Last-Event-ID` (via `evento.seq`) para não perder eventos entre desconexão e reconexão.
- `access-http-base` adiciona `X-Empresa-Id` e `Authorization` automaticamente — o cliente não hardcoda headers de autenticação.
- Bloqueia: FE-EVNT-008 (EventDeliveryFacade que usa este cliente).

**Approach (execution path):**
1) Criar `libs/evnt/data-access/src/lib/evnt.client.ts` injetável (`@Injectable`).
2) Injetar `HttpClient` via `@neuroassist/shared/access-http-base` (que acrescenta `X-Empresa-Id` + `Authorization`).
3) Implementar `openStream(): Observable<SseFrame>` usando `EventSource` ou `fetch()` com `text/event-stream`; guardar o último `id:` recebido.
4) Ao reconectar (erro + retry), passar `Last-Event-ID: <ultimo-seq>` no header da nova requisição (SSE-01).
5) Implementar `listActive(since?)`, `get(id)`, `confirm(id, verdict)`, `ack(id, dispatchId)`, `query(params)` mapeando para os endpoints de SPEC-04 §1.
6) Exportar via `libs/evnt/data-access/src/index.ts`.

**Non-goals:**
- Mapeamento DTO → ViewModel (responsabilidade do Facade FE-EVNT-008).
- Lógica de degradação/polling (FE-EVNT-010).
- Error mapping `ProblemDetail` → `AppError` (shared interceptor, INT-FE-EVNT-006).

**Layer(s):**
frontend / libs/evnt/data-access

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-04-data-access.md` (§1, §3)

**Architecture alignment:**
- Tag Nx: `scope:evnt`, `type:data-access`
- Zero lógica de UI; expõe Observables/Promises
- `openStream()` usa `EventSource`-style com backoff de reconexão

**Implementation checklist (by file):**

**Lib data-access**
- [ ] `libs/evnt/data-access/src/lib/evnt.client.ts` — `EvntClient` com 6 métodos
- [ ] `openStream()`: SSE com `Last-Event-ID` em reconexão (SSE-01)
- [ ] `openStream()`: guarda último `id:` de cada frame para uso em reconexão
- [ ] `listActive(since?)`: `GET /api/v1/events/active?since={since}`
- [ ] `get(id)`: `GET /api/v1/events/{eventoId}`
- [ ] `confirm(id, verdict)`: `POST /api/v1/events/{eventoId}/confirmation`
- [ ] `ack(id, dispatchId)`: `POST /api/v1/events/{eventoId}/dispatches/{dispatchId}/ack`
- [ ] `query(params)`: `GET /api/v1/events` com params
- [ ] `libs/evnt/data-access/src/index.ts` re-exporta `EvntClient`

**Tests**
- [ ] `nx test evnt-data-access` PASS
- [ ] Unit: `confirm()` usa path `/api/v1/events/{id}/confirmation` (não `/confirm`)
- [ ] Unit: `openStream()` em reconexão → `Last-Event-ID` header presente
- [ ] `nx lint evnt-data-access` PASS

**Milestone:**
Wave P2 / Data access

**Labels:**
type:feature
layer:frontend
scope:evnt
priority:P2

**Acceptance criteria:**
- `npx nx test evnt-data-access` PASS
- `npx nx lint evnt-data-access` PASS
- `npx nx build evnt-data-access` PASS
- Teste: mock de `EventSource` com drop → reconexão → header `Last-Event-ID` verificado

**Dependencies:**
- FE-EVNT-001 (EVNT_FEATURE_FLAGS — para o cliente saber o path de base)
- INT-FE-EVNT-001 (evnt-service SSE endpoint contract)

**Data impact:**
- Nenhum — cliente não persiste estado

**API / Contract impact:**
- `POST /api/v1/events/{eventoId}/confirmation` (path reconciliado com BE api-contract — NÃO `/confirm`)
- `GET /api/v1/events/stream` (SSE, `text/event-stream`)

**Testing / Evidence required:**
- Unit: `HttpClientTestingModule` + mock SSE → todos os 6 métodos exercitados

**Risks / Notes:**
- SSE-01 gap (M3.5): o `Last-Event-ID` deve ser enviado via header customizado na reconexão — verificar se `access-http-base` intercepta SSE ou se é necessário handler dedicado.
- FLAG-01 gap: se `liveChannel=false`, `openStream()` nunca é chamado (responsabilidade do Facade/FE-EVNT-010 — o cliente apenas expõe o método).
