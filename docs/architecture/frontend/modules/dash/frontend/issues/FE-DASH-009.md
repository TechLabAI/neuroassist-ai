## [FE-DASH-009] EntradaPageComponent — brand entry chromeless (feature-entrada)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/dash/feature-entrada` contém `EntradaPageComponent` (smart, chromeless), `BrandHeroComponent` (ui) e `EnterPanelCtaComponent` (ui), renderizando a entrada de marca fora do shell chrome — sem sidebar, sem topbar, sem breadcrumb.

**Context / Problem statement:**
- `/entrada` é a tela de entrada da aplicação para cuidadores não autenticados.
- Se a sessão já for válida, o CTA redireciona para `/` diretamente.
- Deve renderizar fora do shell chrome (coverage gap G3).
- Fonte: SPEC-01 §2; SPEC-02 (feature-entrada).
- Bloqueia: FE-DASH-026 (e2e G3)

**Approach (execution path):**
1. Criar lib Nx `dash/feature-entrada` (type:feature, scope:dash).
2. `EntradaPageComponent` (standalone, OnPush): lê `authSession.isAuthenticated()`; se true → redireciona para `/`; se false → renderiza template.
3. `BrandHeroComponent` (standalone, OnPush): recebe sem inputs — logo + tagline "Um gesto que você não vai perder.".
4. `EnterPanelCtaComponent` (standalone, OnPush): `output: enter`; ao clicar → `EntradaPageComponent` dispara login (ACS auth flow) ou redireciona se já autenticado.
5. Verificar que a rota `/entrada` não tem breadcrumb e não injeta o shell layout (FE-DASH-004 cuida disso, mas o componente não deve adicionar layout próprio).

**Non-goals:**
- Não implementar o fluxo de autenticação (responsabilidade de ACS).
- Não renderizar sidebar/topbar nesta lib.

**Layer(s):**
frontend / libs/dash/feature-entrada (type:feature)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-01-route-map.md` (§2)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (feature-entrada)

**Architecture alignment:**
- Standalone + OnPush + Signals
- Tag Nx: `scope:dash`, `type:feature`

**Implementation checklist (by file):**

**Lib: libs/dash/feature-entrada**
- 🔲 `libs/dash/feature-entrada/src/lib/entrada-page/entrada-page.component.ts`
- 🔲 `libs/dash/feature-entrada/src/lib/brand-hero/brand-hero.component.ts`
- 🔲 `libs/dash/feature-entrada/src/lib/enter-panel-cta/enter-panel-cta.component.ts`
- 🔲 `libs/dash/feature-entrada/src/index.ts` (barrel)

**Tests**
- 🔲 `npx nx test dash-feature-entrada` PASS (testar: redirecionamento quando autenticado; renderiza brand-hero e CTA quando não autenticado)
- 🔲 `npx nx lint dash-feature-entrada` PASS

**Milestone:**
Wave 3 / Feature Libs

**Labels:**
type:feature
layer:frontend
scope:dash
priority:P3

**Acceptance criteria:**
- `npx nx test dash-feature-entrada` PASS
- `npx nx lint dash-feature-entrada` PASS
- `npx nx build dash-feature-entrada` PASS
- Rota `/entrada` renderiza sem sidebar/topbar/breadcrumb (chromeless — coverage gap G3)
- Sessão válida → redirect para `/` imediato

**Dependencies:**
- FE-DASH-002 (dash.routes.ts — rota /entrada)
- FE-DASH-004 (shell registration — layout chromeless)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum (apenas leitura de authSession)

**Testing / Evidence required:**
- Screenshot mostrando `/entrada` sem chrome do shell
- e2e coberto por FE-DASH-026

**Risks / Notes:**
- O comportamento chromeless depende da estratégia de layout do shell (FE-DASH-004); esta issue apenas verifica que o componente não adiciona layout próprio.
