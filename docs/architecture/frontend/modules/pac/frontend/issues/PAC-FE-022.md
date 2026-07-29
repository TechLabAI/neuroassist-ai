## [PAC-FE-022] Telemetria via @neuroassist/shared/observability

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/data-access/src/lib/telemetry/pac-telemetry.service.ts` emite eventos de telemetria rastreáveis para as ações relevantes do módulo PAC: carregamento do catálogo, cada transição de lifecycle (save/validate/publish/recalibrate/retire) e erros críticos, via `@neuroassist/shared/observability`.

**Context / Problem statement:**
- Observabilidade FE permite correlacionar ações do usuário com eventos BE (via `X-Correlation-Id` propagado — INT-FE-PAC-004).
- Eventos mínimos a rastrear: `pac.catalog.loaded`, `pac.comando.saved`, `pac.comando.validated`, `pac.comando.published`, `pac.comando.recalibrated`, `pac.comando.retired`, `pac.error.emergency_gate_blocked`, `pac.error.conflict_precondition`.
- O telemetry service é chamado pelas facades (PAC-FE-011) e/ou pelo error handler (PAC-FE-021) após cada operação relevante.

**Approach (execution path):**
1. Criar `libs/pac/data-access/src/lib/telemetry/pac-telemetry.service.ts` com `@Injectable({ providedIn: 'root' })`.
2. Injetar `ObservabilityService` de `@neuroassist/shared/observability`.
3. Métodos: `trackCatalogLoaded(count: number)`, `trackComandoSaved(urgencia)`, `trackLifecycleTransition(action, comandoId)`, `trackEmergencyGateBlocked(comandoId)`, `trackConflictPrecondition(comandoId)`.
4. Integrar nas facades (PAC-FE-011) após sucesso/erro das operações.
5. Unit test: mock `ObservabilityService`, assert emit chamado.

**Non-goals:**
- Sem implementação do `ObservabilityService` (em `@neuroassist/shared/observability`).
- Sem PII nos eventos (apenas IDs opacos e tipos de ação).

**Layer(s):**
frontend / libs/pac/data-access

**Bounded context:**
pac

**Service execution spec(s):**
- Telemetria mínima — sem spec específico de observabilidade FE ainda.

**Architecture alignment:**
- Tag Nx: `scope:pac`, `type:data-access`

**Implementation checklist (by file):**

**Lib: libs/pac/data-access**
- [ ] `libs/pac/data-access/src/lib/telemetry/pac-telemetry.service.ts`
- [ ] `libs/pac/data-access/src/lib/telemetry/pac-telemetry.service.spec.ts`

**Tests**
- [ ] `npx nx test pac-data-access` PASS
- [ ] `npx nx lint pac-data-access` PASS

**Milestone:**
Wave 8 / P8 Observability

**Labels:**
type:feature
layer:frontend
scope:pac
priority:P8

**Acceptance criteria:**
- `npx nx test pac-data-access` PASS: `trackCatalogLoaded(5)` → `ObservabilityService.emit` chamado com `event: 'pac.catalog.loaded', count: 5`.
- `npx nx test pac-data-access` PASS: `trackEmergencyGateBlocked(id)` → emit chamado.
- `npx nx lint pac-data-access` PASS.

**Dependencies:**
- PAC-FE-011 (PacComandoFacade — ponto de integração)
- `@neuroassist/shared/observability`

**Data impact:**
Nenhum.

**API / Contract impact:**
Nenhum (side-channel de telemetria, não afeta fluxo).

**Testing / Evidence required:**
- Unit: spy em ObservabilityService.

**Risks / Notes:**
- Se `@neuroassist/shared/observability` não existir no MVP, criar stub no-op e substituir quando o serviço for implementado.
