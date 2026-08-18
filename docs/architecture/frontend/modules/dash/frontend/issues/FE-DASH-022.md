## [FE-DASH-022] UX Polish: banner 403 forbidden-scope inline (coverage gap G8)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Quando o backend responde `#forbidden-scope` (403), o DASH renderiza um banner inline "Você não tem acesso a este paciente." dentro da superfície afetada, sem revelar se o paciente existe e mantendo o shell chrome intacto. Fecha coverage gap G8.

**Context / Problem statement:**
- 403 `#forbidden-scope` pode ocorrer em `/panel/patients/{patientId}/overview` ou `/panel/summary`.
- O banner é inline na seção afetada — não modal, não full-screen.
- "SHALL NOT reveal whether the patient exists" — mensagem genérica.
- Fonte: SPEC-07 §2 (forbidden-scope → banner inline); coverage gap G8.
- Bloqueia: FE-DASH-030 (e2e G8)

**Approach (execution path):**
1. No `DashHomeFacade` (FE-DASH-006): capturar `AppError.code === 'forbidden-scope'` (status 403) e setar `_state` para um estado especializado (ex: `'forbidden'`).
2. Em `HomeStatusPageComponent` (FE-DASH-010): quando `state() === 'forbidden'`, projetar `ForbiddenBannerComponent` (ou usar `EmptyOrErrorStateComponent` com kind parametrizado).
3. Criar `ForbiddenBannerComponent` em `libs/dash/ui`:
   - Exibe: "Você não tem acesso a este paciente."
   - Sem botão de retry (a mensagem é definitiva para o escopo).
   - Shell chrome permanece intacto.
4. Garantir que a mensagem não vaza existência do recurso.

**Non-goals:**
- Não mostrar modal.
- Não desmontar o shell chrome.

**Layer(s):**
frontend / libs/dash/ui (type:ui) + libs/dash/data-access (type:data-access)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-07-error-handling.md` (§2 forbidden-scope)

**Architecture alignment:**
- Tag Nx: `scope:dash`, `type:ui`

**Implementation checklist (by file):**

**Lib: libs/dash/ui**
- 🔲 `libs/dash/ui/src/lib/forbidden-banner/forbidden-banner.component.ts`
- 🔲 `libs/dash/ui/src/lib/forbidden-banner/forbidden-banner.component.html`
- 🔲 `libs/dash/ui/src/index.ts` (barrel)

**Lib: libs/dash/data-access**
- 🔲 `libs/dash/data-access/src/lib/dash-home.facade.ts` (tratar AppError 403 → state 'forbidden')

**Tests**
- 🔲 `npx nx test dash-ui` PASS (banner renderiza mensagem sem revelar existência do paciente)
- 🔲 `npx nx test dash-data-access` PASS (403 → state 'forbidden')
- 🔲 `npx nx lint dash-ui` PASS

**Milestone:**
Wave 7 / UX Polish

**Labels:**
type:ui
layer:frontend
scope:dash
priority:P7
coverage-gap:G8

**Acceptance criteria:**
- `npx nx test dash-ui` PASS
- `npx nx lint dash-ui` PASS
- `npx nx build dash-ui` PASS
- 403 → banner "Você não tem acesso a este paciente." (coverage gap G8)
- Banner não revela existência do paciente
- Shell chrome intacto durante o banner

**Dependencies:**
- FE-DASH-006 (DashHomeFacade — tratamento de erro)
- FE-DASH-010 (HomeStatusPageComponent — projetar banner)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Trata resposta 403 de `/panel/summary` ou `/panel/patients/{patientId}/overview`

**Testing / Evidence required:**
- Screenshot: banner 403 inline com shell chrome intacto
- e2e: FE-DASH-030 (G8)

**Risks / Notes:**
- A mensagem "Você não tem acesso a este paciente." deve ser idêntica para 403 de qualquer endpoint de paciente — não customizar por endpoint para evitar enumeration.
