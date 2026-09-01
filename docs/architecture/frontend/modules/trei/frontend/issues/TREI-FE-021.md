## [TREI-FE-021] TreiAuthoringBannerComponent + StatusChipComponent (libs/trei/ui local primitives)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/trei/ui` exporta `TreiAuthoringBannerComponent` ("Treinamento — não é o app do paciente" + badge "gerado por IA" quando `aiDisclosed`) e `StatusChipComponent` (chip visual do `NodeSubEstado`) — primitivos locais que compõem apenas tokens de `@neuroassist/shared/ui-*`.

**Context / Problem statement:**
- `TreiAuthoringBannerComponent` implementa o affordance INV-6 (TECH-007): presente em **todas** as rotas TREI, renderizado no `TreiRootComponent` — é o primeiro nível de distinção visual entre o shell da cuidadora e o do paciente.
- `StatusChipComponent` exibe `NodeSubEstado` de cada proposta como chip colorido: rascunho (cinzento), aguardando_aprovacao (amarelo), ativo (verde), descartado (vermelho) — o esquema de cores usa tokens de `@neuroassist/shared/ui-theme`, zero cor hardcoded.
- Ambos os componentes usam **apenas** tokens de `@neuroassist/shared/ui-*` — nenhuma cor ou espaçamento hardcoded (SPEC-02 §4).
- Bloqueia: TREI-FE-004 (TreiRootComponent usa TreiAuthoringBannerComponent), TREI-FE-019 (ProposedNodeCardComponent usa StatusChipComponent).

**Approach (execution path):**
1. Criar `libs/trei/ui/src/lib/trei-authoring-banner.component.ts`:
   - `input()`: `aiDisclosed: boolean` (quando true, exibe badge "gerado por IA" — ADR-017).
   - Template: banner de fundo distinto + texto "Treinamento — não é o app do paciente" + badge condicional.
2. Criar `libs/trei/ui/src/lib/status-chip.component.ts`:
   - `input()`: `subEstado: NodeSubEstado`.
   - Template: chip com label localizado (rascunho="Rascunho", etc.) e cor via CSS var token.
3. Exportar ambos do `libs/trei/ui/src/index.ts`.

**Non-goals:**
- Não renderizar lógica de negócio (são primitivos presentacionais puros).
- Não usar `inject()`.

**Layer(s):**
frontend / libs/trei/ui

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§4)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-05-tool-contract.md` (§1 — badgeKey)

**Architecture alignment:**
- `standalone: true`, `OnPush`
- Zero cor/espaçamento hardcoded — apenas tokens
- Tag Nx: `scope:trei`, `type:ui`

**Implementation checklist (by file)**

**libs/trei/ui**
- [ ] `libs/trei/ui/src/lib/trei-authoring-banner.component.ts`
- [ ] `libs/trei/ui/src/lib/status-chip.component.ts`
- [ ] `libs/trei/ui/src/index.ts` (barrel)

**Tests**
- [ ] `nx test trei-ui` PASS
- [ ] `TreiAuthoringBannerComponent`: badge "gerado por IA" visível quando `aiDisclosed = true`
- [ ] `StatusChipComponent`: cor por token CSS var (sem `style="color:#..."`)
- [ ] `nx lint trei-ui` PASS

**Milestone:**
Wave 4 / UI Primitives

**Labels:**
type:feature
layer:frontend
scope:trei
priority:P4

**Acceptance criteria:**
- `npx nx test trei-ui` PASS
- `npx nx lint trei-ui` PASS
- `grep -n 'color:\s*#\|background-color:\s*#' libs/trei/ui/src/` retorna vazio (sem hardcode)
- `TreiAuthoringBannerComponent` tem `role="banner"` ou equivalente a11y

**Dependencies:**
- TREI-FE-009 (NodeSubEstado)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Screenshot do banner com e sem `aiDisclosed`
- Screenshot dos 4 estados de `StatusChipComponent`

**Risks / Notes:**
- Confirmar os nomes exactos dos tokens de cor de estado em `@neuroassist/shared/ui-theme` antes de implementar (ex.: `--color-status-draft`, `--color-status-pending`).
