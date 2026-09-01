## [TREI-INT-005] Integration com CONV — G5 invariant: health embedModelId readiness display

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
O `TrainingSessionFacade.loadHealth()` lê `GET /health` e expõe `embedModelId` no VM para display informacional — confirmando que o modelo de embedding em uso por TREI durante a indexação é o mesmo que CONV usa em query-time (G5 invariant readiness check).

**Context / Problem statement:**
- G5 (invariante de paridade de modelo): `TREI_EMBED_MODEL_ID` (index-time) deve igualar o `model_id` que CONV usa em query-time (ADR-015, _brief.md §6). Uma divergência causaria degradação silenciosa de recall no CONV.
- O FE **não valida** a paridade — o BE (trei-service) controla o modelo e expõe `embedModelId` via `GET /health` para display/observabilidade.
- O FE surface: `TrainingSessionVM.llmModelId` e um campo `embedModelId` no VM de saúde — exibidos no bootstrap readiness check (SPEC-06 §3, cross-link §3 G5).
- Esta issue não corrige uma paridade errada — apenas garante que o FE exibe `embedModelId` para que operadores possam monitorizar (alertar se divergente de CONV).

**Approach (execution path):**
1. Em `TrainingSessionFacade.loadHealth()` (TREI-FE-010 — extensão):
   - Armazenar `health.embedModelId` num signal `embedModelId: Signal<string | null>`.
   - Armazenar `health.degradedCapabilities` num signal `degradedCapabilities: Signal<string[]>`.
2. Em `TrainingSessionPageComponent` ou no banner de saúde (TREI-FE-025 — extensão): exibir `embedModelId` em modo de debug/info (não é uma mensagem de erro; pode ser behind `isDevMode()` ou num painel colapsável).
3. Documentar no `health-degraded-notice.component.ts` que `embedModelId` pode ser exibido para suporte.

**Non-goals:**
- Não validar a paridade com CONV — o FE não tem acesso ao model_id de CONV.
- Não bloquear autoria quando `embedModelId` parece errado — apenas exibir.

**Layer(s):**
frontend / libs/trei/data-access

**Bounded context:**
trei → CONV (invariante partilhado)

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/contracts/backend-api-cross-link.md` (§3 G5)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-06-shell-integration.md` (§3)

**Architecture alignment:**
- Signal<string | null> para embedModelId
- Tag Nx: `scope:trei`, `type:data-access`

**Implementation checklist (by file)**

**libs/trei/data-access**
- [ ] `training-session.facade.ts` — `embedModelId` signal populado por `loadHealth()`
- [ ] `health-degraded-notice.component.ts` — exibe `embedModelId` (informacional)

**Tests**
- [ ] `nx test trei-data-access` PASS
- [ ] `loadHealth()` com `{ embedModelId: 'nomic-embed-text', degradedCapabilities: [] }` → `facade.embedModelId() === 'nomic-embed-text'`

**Milestone:**
Wave 7 / UX Polish (integração)

**Labels:**
type:integration
layer:frontend
scope:trei
priority:P7

**Acceptance criteria:**
- `npx nx test trei-data-access` PASS
- `embedModelId` visível no painel de saúde (informacional, não bloqueante)
- `loadHealth()` swallows erro — não propaga para o utilizador

**Dependencies:**
- TREI-FE-007 (TreiHealthHttpAdapter.readiness)
- TREI-FE-010 (TrainingSessionFacade)
- TREI-FE-025 (health-degraded-notice)

**Data impact:**
- Nenhum

**API / Contract impact:**
- `GET /api/v1/trei/health` → `{ embedModelId, degradedCapabilities }`

**Testing / Evidence required:**
- `nx test` + snapshot de `embedModelId` populado correctamente

**Risks / Notes:**
- Se `degradedCapabilities` incluir `'llm'`, o aviso de degradação deve ser mais proeminente (converter para mensagem específica de "Assistente de IA não disponível").
