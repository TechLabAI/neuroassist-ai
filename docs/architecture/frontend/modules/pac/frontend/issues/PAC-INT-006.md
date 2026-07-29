## [PAC-INT-006] GEST TipoGesto cross-read — TiposGestoReadClient via GWY + requestNew inline flow

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
A integração end-to-end do `TiposGestoReadClient` com o GEST service via GWY está validada: `list()` popula o `GesturePickerComponent`, e o fluxo inline `requestNew(nome)` → GEST → seleção do id retornado funciona sem que nenhuma lib `scope:pac` importe `scope:gest`.

**Context / Problem statement:**
- PAC lê tipos de gesto do GEST via `GET /api/gest/v1/tipos-gesto` (GWY routing); o `TiposGestoReadFacade` (PAC-FE-012) usa este client.
- O fluxo de "+ novo tipo" no `GesturePickerComponent` (PAC-FE-015): usuário digita nome → `addNew(nome)` → `TiposGestoReadClient.requestNew(nome)` → GEST cria o tipo → retorna `TipoGesto` → facade seleciona apenas o `id` → editor atualiza `primitivoPrincipal`/`secundario`.
- Feature flag `pac.inlineNewTipoGesto=off` desabilita este fluxo (apenas lista existente).
- Esta issue valida a integração completa (client + facade + componente) via e2e com mock de GEST.

**Approach (execution path):**
1. Verificar que `TiposGestoReadClient` (PAC-FE-009) e `TiposGestoReadFacade` (PAC-FE-012) estão funcionando.
2. Criar mock fixture para GEST endpoint em `apps/pac-e2e/src/fixtures/gest-mocks.ts`: lista de 3 tipos de gesto + resposta de `requestNew`.
3. Escrever e2e integration test: abrir editor → verificar dropdown populado com 3 tipos → clicar "+ Novo tipo" → digitar nome → verificar que novo tipo aparece selecionado.
4. Escrever e2e: flag `pac.inlineNewTipoGesto=off` → affordance "+ novo tipo" ausente.
5. Lint check: nenhum import de `scope:gest` em qualquer arquivo de `libs/pac/`.

**Non-goals:**
- Sem implementação do GEST side — apenas o client PAC que consome o endpoint.
- Sem teste de training pipeline do GEST — o mock retorna o tipo como `pending-training`.

**Layer(s):**
frontend / libs/pac/data-access (integration validation)

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-04-data-access.md` (§1 TipoGesto row, §3 TiposGestoReadFacade)
- `docs/architecture/frontend/modules/pac/frontend/e2e/command-editor.feature` (cenários "Requesting a new tipo de gesto from GEST...", "inline novo tipo flag off hides affordance")

**Architecture alignment:**
- Tag Nx: `scope:pac`, `type:data-access`
- Sem `scope:gest` imports em libs

**Implementation checklist (by file):**

**App: apps/pac-e2e**
- [ ] `apps/pac-e2e/src/fixtures/gest-mocks.ts`
- [ ] `apps/pac-e2e/src/command-editor.spec.ts` (cenários GEST — já na issue PAC-FE-024, referência cruzada)

**Lib: libs/pac/data-access**
- [ ] Verificar `tipos-gesto-read.client.ts` via e2e (não duplicar — referência a PAC-FE-009/012)

**Tests**
- [ ] `npx nx e2e pac-e2e` (cenários GEST) PASS
- [ ] `npx nx lint pac` PASS (zero `scope:gest` imports em libs)

**Milestone:**
Wave 4 / P4 Hub Integrations

**Labels:**
type:integration
layer:frontend
scope:pac
priority:P4

**Acceptance criteria:**
- e2e PASS: dropdown de gestos populado com tipos retornados pelo mock GEST.
- e2e PASS: `requestNew('Piscar Duplo')` → novo tipo selecionado no editor.
- e2e PASS: flag off → affordance "+ novo tipo" ausente no DOM.
- `npx nx lint pac` PASS: zero imports `scope:gest` em `libs/pac/**`.

**Dependencies:**
- PAC-FE-009 (TiposGestoReadClient)
- PAC-FE-012 (TiposGestoReadFacade)
- PAC-FE-015 (GesturePickerComponent)

**Data impact:**
Nenhum.

**API / Contract impact:**
- `GET /api/gest/v1/tipos-gesto` e `POST /api/gest/v1/tipos-gesto` via GWY → gest-service.

**Testing / Evidence required:**
- e2e: request log mostra `POST /api/gest/v1/tipos-gesto` com `{ nome: 'Piscar Duplo' }`.
- Lint: zero `scope:gest` em libs.

**Risks / Notes:**
- O gest-service pode ter o endpoint de `requestNew` ainda não implementado; usar mock via `page.route` e adicionar uma nota de "pendente de implementação GEST" para tracking.
