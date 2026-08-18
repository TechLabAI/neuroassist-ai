## [FE-DASH-021] UX Polish: StatusCard loading state skeleton (coverage gap G4)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`StatusCardComponent` renderiza um skeleton animado quando `vm().kind === 'loading'`, sem assertar "está bem" e sem mostrar dados incompletos. Fecha coverage gap G4.

**Context / Problem statement:**
- Durante o carregamento do summary (`state='loading'`), o StatusCard deve mostrar feedback visual adequado — não um estado em branco nem "está bem" prematuro.
- Coverage gap G4: "loading state StatusCard" identificado no Gate1 (M3.5).
- Fonte: SPEC-03 §1 (EARS: "WHILE the Home summary is loading, THE SYSTEM SHALL render the StatusCard in the `loading` kind").

**Approach (execution path):**
1. Garantir que `StatusCardComponent` (FE-DASH-011) tem template adequado para `kind='loading'`:
   - Skeleton div(s) animados (CSS pulse) no lugar do nome do paciente e labels.
   - Nenhum texto de "está bem" ou ícone de shield green.
   - Acessível: `aria-busy="true"` no container.
2. `HomeStatusPageComponent` (FE-DASH-010): derivar `vm.kind = state() === 'loading' ? 'loading' : calm() ? 'calm' : 'ok'`.
3. Escrever test que verifica ausência de "está bem" quando `kind='loading'`.

**Non-goals:**
- Não criar animações custom (usar CSS utilitário do design system).

**Layer(s):**
frontend / libs/dash/ui (type:ui) + libs/dash/feature-home (type:feature)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-03-view-models.md` (§1 EARS)

**Architecture alignment:**
- Tag Nx: `scope:dash`, `type:ui`

**Implementation checklist (by file):**

**Lib: libs/dash/ui**
- 🔲 `libs/dash/ui/src/lib/status-card/status-card.component.html` (skeleton branch para loading)
- 🔲 `libs/dash/ui/src/lib/status-card/status-card.component.scss` (pulse animation ou CSS utility class)

**Lib: libs/dash/feature-home**
- 🔲 `libs/dash/feature-home/src/lib/home-status-page/home-status-page.component.ts` (derivar vm.kind de state())

**Tests**
- 🔲 `npx nx test dash-ui` PASS (verificar que kind='loading' não renderiza texto "está bem")
- 🔲 `npx nx lint dash-ui` PASS

**Milestone:**
Wave 7 / UX Polish

**Labels:**
type:ui
layer:frontend
scope:dash
priority:P7
coverage-gap:G4

**Acceptance criteria:**
- `npx nx test dash-ui` PASS
- `npx nx lint dash-ui` PASS
- `npx nx build dash-ui` PASS
- Loading state NÃO renderiza "está bem" nem ícone green (coverage gap G4)
- Skeleton visível durante carregamento (`aria-busy="true"`)

**Dependencies:**
- FE-DASH-011 (StatusCardComponent — base do template)
- FE-DASH-010 (HomeStatusPageComponent — derivar vm.kind)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Screenshot: StatusCard em loading (skeleton visível, sem "está bem")

**Risks / Notes:**
- Verificar com design se há um skeleton token no `@neuroassist/shared/ui-tokens` ou se é necessário criar.
