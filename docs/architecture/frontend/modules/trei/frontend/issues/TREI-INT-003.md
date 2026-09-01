## [TREI-INT-003] Integration com @neuroassist/shared/error-model — AppError contract (todos os codigos SPEC-07)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`TreiErrorMapper` produz `AppError` com todos os campos do contrato de `@neuroassist/shared/error-model` para cada código de erro de SPEC-07 — e o shared feedback layer (toasts, banners) renderiza-os correctamente a partir do `AppError.code`.

**Context / Problem statement:**
- `@neuroassist/shared/error-model` define o tipo `AppError` com campos `code`, `message`, `retriable`, `metadata` — o TREI adopta este tipo sem o redeclarar.
- O shared feedback layer (toasts, banners) de `@neuroassist/shared/ui-feedback` lê `AppError.code` para determinar o componente de exibição — TREI não reimplementa toasts.
- Esta issue verifica que o `TreiErrorMapper` produz `AppError` compliant para todos os 9 códigos de SPEC-07 §2 e que o shared feedback layer os exibe correctamente.

**Approach (execution path):**
1. Revisar a interface `AppError` de `@neuroassist/shared/error-model` — confirmar campos e tipos.
2. Verificar que `TreiErrorMapper` (TREI-FE-012) produz `AppError` válido para todos os códigos.
3. Em `TrainingSessionPageComponent` e `ProposalReviewPageComponent`: usar o shared feedback service/directive para exibir `AppError` (não implementar componentes de toast/banner custom para erros que o shared já cobre).
4. Documentar quais erros TREI usa o componente de toast shared vs componentes locais (per SPEC-07 §3).

**Non-goals:**
- Não implementar o shared error model ou o shared feedback layer.
- Não criar toasts custom para erros que o shared já cobre.

**Layer(s):**
frontend / libs/trei/data-access · libs/trei/feature-training-session · libs/trei/feature-proposal-review

**Bounded context:**
trei → shared/error-model · shared/ui-feedback

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-07-error-handling.md` (§1..§3)

**Architecture alignment:**
- `scope:trei` → `scope:shared` apenas
- Tag Nx: `scope:trei`, `type:data-access`

**Implementation checklist (by file)**

**libs/trei/data-access**
- [ ] `trei-error-mapper.ts` — todos os 9 códigos de SPEC-07 mapeados para `AppError` válido

**libs/trei/feature-training-session + feature-proposal-review**
- [ ] Uso do shared feedback service/directive para toasts transientes
- [ ] Componentes locais apenas para banners conversacionais (llm-unavailable, patient-not-found) e modais

**Tests**
- [ ] Testes de mapper: 9 códigos → 9 `AppError` com `code` correcto
- [ ] `nx lint` PASS sem imports de `scope:ui-feedback` em `scope:data-access`

**Milestone:**
Wave 2 / Data Access (integração)

**Labels:**
type:integration
layer:frontend
scope:trei
priority:P2

**Acceptance criteria:**
- `TreiErrorMapper` produz `AppError.code` para cada tipo de SPEC-07 §2
- Shared toast layer exibe `forbidden-scope`, `node-not-found`, `already-published`
- Componentes locais para: `llm-unavailable` (banner), `patient-not-found` (banner de início), `invalid-request` (inline field)

**Dependencies:**
- TREI-FE-012 (TreiErrorMapper)
- TREI-FE-016 (ConversationErrorPanelComponent — llm-unavailable)
- TREI-FE-025 (patient-not-found banner)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum HTTP

**Testing / Evidence required:**
- Tabela de mapeamento: `type` → `AppError.code` → componente de exibição

**Risks / Notes:**
- Se `@neuroassist/shared/ui-feedback` não tiver suporte para todos os tipos de superfície de SPEC-07 §3, levantar antes de implementar os componentes locais.
