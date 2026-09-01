## [TREI-INT-006] Integration com @neuroassist/shared/observability + @neuroassist/shared/ui-* design tokens

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
As facades TREI emitem telemetria via `ObservabilityService` de `@neuroassist/shared/observability`; todos os componentes TREI usam exclusivamente tokens de design de `@neuroassist/shared/ui-*` (zero cor/espaçamento hardcoded).

**Context / Problem statement:**
- `@neuroassist/shared/observability` provê o `ObservabilityService.track(event, payload)` — TREI usa-o nas facades para `session-started`, `turn-submitted`, `node-approved` (TREI-FE-026).
- `@neuroassist/shared/ui-theme`, `@neuroassist/shared/ui-layout`, `@neuroassist/shared/ui-feedback` provêem todos os tokens CSS e componentes de UI partilhados — TREI não define cores ou espaçamentos próprios.
- Esta issue verifica (por lint e revisão de código) que nenhum componente TREI tem CSS hardcoded e que a integração de observabilidade está funcional.
- Também confirma que `@neuroassist/shared/ui-feedback` está disponível para banners, toasts, e dialogs usados em TREI.

**Approach (execution path):**
1. Confirmar que `@neuroassist/shared/observability` está listado como peer dep das libs TREI que o usam.
2. Confirmar que `@neuroassist/shared/ui-theme` está importado no `styles.scss` das libs TREI relevantes.
3. Escrever regra de lint (ou usar ESLint `no-restricted-syntax`) para detectar CSS vars hardcoded em `libs/trei/**/*.scss`.
4. Verificar que `TreiAuthoringBannerComponent` e `StatusChipComponent` (TREI-FE-021) usam apenas tokens de `@neuroassist/shared/ui-theme`.

**Non-goals:**
- Não implementar o `ObservabilityService` nem os tokens de UI.
- Não criar tokens de design TREI-specific (SPEC-02 §4 — usar shared apenas).

**Layer(s):**
frontend / libs/trei/ui · libs/trei/data-access

**Bounded context:**
trei → shared/observability · shared/ui-*

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§4)

**Architecture alignment:**
- `scope:trei` → `scope:shared` (permitido)
- Zero CSS hardcoded
- Tag Nx: `scope:trei`, `type:ui` / `type:data-access`

**Implementation checklist (by file)**

**libs/trei/ui + libs/trei/data-access**
- [ ] `@neuroassist/shared/observability` nas deps de `libs/trei/data-access`
- [ ] `@neuroassist/shared/ui-*` nas deps de `libs/trei/ui` e feature libs
- [ ] Nenhum `color: #...` ou `background-color: #...` em `libs/trei/**/*.scss`

**Tests**
- [ ] `nx lint trei-ui` PASS — sem hardcode CSS
- [ ] `nx lint trei-data-access` PASS — imports de observability válidos
- [ ] Spy: `ObservabilityService.track` chamado nos eventos correctos (TREI-FE-026)

**Milestone:**
Wave 8 / Observability (integração)

**Labels:**
type:integration
layer:frontend
scope:trei
priority:P8

**Acceptance criteria:**
- `npx nx lint` PASS em todas as libs TREI
- `grep -r 'color:\s*#\|background:\s*#' libs/trei/` retorna vazio
- `ObservabilityService.track` chamado em: session-started, turn-submitted, node-approved

**Dependencies:**
- TREI-FE-021 (TreiAuthoringBannerComponent + StatusChipComponent)
- TREI-FE-026 (Telemetria nas facades)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum HTTP

**Testing / Evidence required:**
- `nx lint` limpo em todas as libs
- Revisão de código: zero CSS hardcoded

**Risks / Notes:**
- Confirmar os nomes dos pacotes exactos de `@neuroassist/shared/observability` e `@neuroassist/shared/ui-*` no `package.json` do workspace antes de adicionar às deps — podem ter nomes ligeiramente diferentes.
