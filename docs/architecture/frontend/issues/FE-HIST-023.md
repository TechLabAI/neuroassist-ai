## [FE-HIST-023] hist observability: telemetry events via @hub/observability

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Eventos de telemetria para as interações chave do módulo HIST emitidos via `@hub/observability`: navegação para `/historico`, troca de período, carregamento de mais itens, marcação de feedback (sucesso e erro).

**Context / Problem statement:**
- Plataforma de saúde: rastreabilidade de uso é necessária para auditoria e melhoria contínua.
- Eventos a rastrear: `hist.timeline.viewed`, `hist.period.changed`, `hist.load_more.triggered`, `hist.feedback.marked`, `hist.feedback.error`.
- Integração via `@hub/observability` (injeção de `TelemetryService`); sem lógica de tracking no template.

**Approach (execution path):**
1. Injetar `TelemetryService` de `@neuroassist/shared` na `HistoryFacade` ou no smart component.
2. `load(period)` sucesso → `telemetry.track('hist.timeline.viewed', { period, count })`.
3. `setPeriod(p)` → `telemetry.track('hist.period.changed', { from, to: p })`.
4. `loadMore()` sucesso → `telemetry.track('hist.load_more.triggered', { page })`.
5. `markFeedback` sucesso → `telemetry.track('hist.feedback.marked', { verdict })`.
6. `markFeedback` error → `telemetry.track('hist.feedback.error', { errorType })`.
7. Eventos não devem bloquear fluxo principal (fire-and-forget com `catchError`).

**Non-goals:**
- Não implementar analytics dashboard.
- Não logar PII (sem `subjectId` ou `actorId` nos eventos de telemetria).

**Layer(s):**
frontend / data-access

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-08-performance.md` (§5)

**Architecture alignment:**
- Tag Nx: `scope:hist`, `type:data-access`

**Implementation checklist (by file):**
- 🔲 Atualizar `history.facade.ts` — injetar TelemetryService e emitir eventos
- 🔲 Atualizar `history.facade.spec.ts` — mock TelemetryService
- 🔲 `nx test hist-data-access` PASS
- 🔲 `nx lint hist-data-access` PASS

**Milestone:** Wave P7 / Observability

**Labels:**
type:observability · layer:frontend · scope:hist · priority:P7

**Acceptance criteria:**
- Cada evento key é emitido na ação correta (5 eventos unit-testados).
- Nenhum PII nos payloads de telemetria.
- Falha do TelemetryService não afeta o fluxo principal.

**Dependencies:**
- FE-HIST-008
