## [FE-DASH-023] Telemetria — eventos de observabilidade via @neuroassist/shared/observability

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/dash/data-access` e feature-libs emitem eventos de telemetria para `@neuroassist/shared/observability`: `panel_viewed`, `sse_connected`, `sse_reconnected`, `sse_fallback_activated`, `notification_acknowledged`, erro de summary, erro 403.

**Context / Problem statement:**
- Observabilidade é necessária para monitorar a saúde do SSE, taxa de erros e engajamento do cuidador.
- Os eventos devem incluir `tenantId`, `caregiverId`, `correlationId` (do `AppError`) quando disponível.
- Fonte: SPEC-04 §1 (correlationId em AppError); FE 06 observability layer.

**Approach (execution path):**
1. Injetar `ObservabilityService` de `@neuroassist/shared/observability` nas facades e no DashStreamClient.
2. Emitir `panel_viewed` no `HomeStatusPageComponent.ngOnInit()`.
3. Emitir `sse_connected` no `DashStreamClient.open()` após primeiro heartbeat.
4. Emitir `sse_reconnected` após cada reconexão bem-sucedida.
5. Emitir `sse_fallback_activated` quando cair em long-poll.
6. Emitir `notification_acknowledged` no `NotificationsFacade.acknowledge()`.
7. Emitir erro de summary (status + correlationId) no `DashHomeFacade` ao capturar AppError.
8. Emitir `forbidden_scope` ao capturar 403 (sem detalhes do paciente).

**Non-goals:**
- Não implementar `ObservabilityService` (shared).
- Não enviar PII nos eventos (nome do paciente, etc.).

**Layer(s):**
frontend / libs/dash/data-access (type:data-access) + libs/dash/feature-home (type:feature)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-04-data-access.md` (§3 correlationId)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-07-error-handling.md` (§1 correlationId)

**Architecture alignment:**
- Tag Nx: `scope:dash`, `type:data-access`

**Implementation checklist (by file):**

**Lib: libs/dash/data-access**
- 🔲 `libs/dash/data-access/src/lib/dash-home.facade.ts` (emitir eventos de erro + panel_viewed)
- 🔲 `libs/dash/data-access/src/lib/notifications.facade.ts` (emitir notification_acknowledged)
- 🔲 `libs/dash/data-access/src/lib/dash-stream.client.ts` (emitir sse_connected / sse_reconnected / sse_fallback_activated)

**Tests**
- 🔲 `npx nx test dash-data-access` PASS (verificar que ObservabilityService.emit é chamado nos cenários)
- 🔲 `npx nx lint dash-data-access` PASS

**Milestone:**
Wave 8 / Observability

**Labels:**
type:data-access
layer:frontend
scope:dash
priority:P8

**Acceptance criteria:**
- `npx nx test dash-data-access` PASS
- `npx nx lint dash-data-access` PASS
- `npx nx build dash-data-access` PASS
- Nenhum evento de telemetria inclui PII (nome do paciente, email)
- `correlationId` incluído em eventos de erro quando disponível
- `sse_fallback_activated` emitido após N falhas de reconexão

**Dependencies:**
- FE-DASH-006 (DashHomeFacade)
- FE-DASH-007 (NotificationsFacade)
- FE-DASH-008 (DashStreamClient)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Test com ObservabilityService mockado; verificar calls nos cenários-chave

**Risks / Notes:**
- Verificar que `@neuroassist/shared/observability` está implementado antes desta issue; se não, criar stub.
