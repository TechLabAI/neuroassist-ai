## [FE-EVNT-025] Telemetria — eventos de observabilidade via @neuroassist/shared/observability

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`EventDeliveryFacade` e os componentes EVNT emitem eventos de telemetria para `@neuroassist/shared/observability` nos momentos-chave: alarme recebido, confirmação submetida, confirmação concluída, modo degradado ativado.

**Context / Problem statement:**
- A plataforma de tecnologia assistiva requer rastreabilidade de alarmes para fins de auditoria e melhoria contínua.
- Os eventos de telemetria permitem medir `time-to-confirm` (meta: ≤ 1s para receber alarme, tempo realista para confirmação).
- `@neuroassist/shared/observability` é o ponto de injeção — o EVNT não implementa telemetria própria.

**Approach (execution path):**
1) Injetar `ObservabilityService` no `EventDeliveryFacade` (ou nos componentes smart).
2) Emitir `evnt.alarm_received` quando `activeEvent` muda para não-null (SSE `evento.created` emergência).
3) Emitir `evnt.confirm_submitted` quando `confirm()` é chamado (com `eventoId`).
4) Emitir `evnt.confirm_completed` quando o POST retorna 200 (com `eventoId`, `verdict`, latência).
5) Emitir `evnt.delivery_degraded` quando `deliveryMode` muda para `'degraded'`.
6) Todos os eventos com `{ eventoId, timestamp, userId }` no payload (sem dados clínicos — LGPD).

**Non-goals:**
- Implementação do `ObservabilityService` (shared).
- Análise dos dados (dashboards — módulo DASH).

**Layer(s):**
frontend / libs/evnt/data-access (EventDeliveryFacade)

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-04-data-access.md` (§5 — safety invariants)

**Architecture alignment:**
- Sem dados clínicos/biométricos nos eventos de telemetria (LGPD)
- Tag Nx: `scope:evnt`, `type:data-access`

**Implementation checklist (by file):**

**Lib data-access (EventDeliveryFacade)**
- [ ] Injetar `ObservabilityService` (se disponível no shared)
- [ ] `evnt.alarm_received` ao setar `_activeEvent` com emergência
- [ ] `evnt.confirm_submitted` no início de `confirm()`
- [ ] `evnt.confirm_completed` após 200 (com latência ms)
- [ ] `evnt.delivery_degraded` ao mudar `_deliveryMode` para `'degraded'`
- [ ] Payloads sem campos clínicos: apenas `{ eventoId, timestamp, userId }`

**Tests**
- [ ] `nx test evnt-data-access` PASS
- [ ] Unit: `activeEvent` setado → `ObservabilityService.track('evnt.alarm_received', ...)` chamado
- [ ] `nx lint evnt-data-access` PASS

**Milestone:**
Wave P8 / Observability

**Labels:**
type:feature
layer:frontend
scope:evnt
priority:P8

**Acceptance criteria:**
- `npx nx test evnt-data-access` PASS
- `npx nx lint evnt-data-access` PASS
- Telemetria não bloqueia o caminho principal (erros no ObservabilityService são silenciosos)

**Dependencies:**
- FE-EVNT-008 (EventDeliveryFacade)
- INT-FE-EVNT-004 (shared ObservabilityService — se integrado via AccessDecisionService ou separado)

**Data impact:**
- Eventos de telemetria emitidos — sem PII ou dados clínicos (LGPD)

**API / Contract impact:**
- Nenhum direto (ObservabilityService pode emitir para endpoint interno)

**Testing / Evidence required:**
- Unit: spy em `ObservabilityService.track` — 4 eventos emitidos nos 4 momentos-chave

**Risks / Notes:**
- Se `@neuroassist/shared/observability` não existe ainda, usar um stub/noop; a issue pode ser aberta em P8 para implementar depois sem bloquear P1-P7.
