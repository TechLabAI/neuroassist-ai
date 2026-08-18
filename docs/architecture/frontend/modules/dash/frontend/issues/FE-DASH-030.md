## [FE-DASH-030] e2e Playwright: 403 forbidden-scope banner (G8)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/dash-e2e/src/error-states.spec.ts` verifica que ao receber `#forbidden-scope` (403) do backend, o DASH renderiza o banner inline "Você não tem acesso a este paciente." com shell chrome intacto. Fecha coverage gap G8.

**Context / Problem statement:**
- Coverage gap G8: "403 banner" identificado no Gate1 (M3.5).
- Fonte: SPEC-07 §2; coverage gap G8.

**Approach (execution path):**
1. Criar `apps/dash-e2e/src/error-states.spec.ts`.
2. Cenário 403: simular `GET /panel/summary` retornando 403 `problem+json` com `type: .../forbidden-scope` → verificar banner "Você não tem acesso a este paciente." no DOM.
3. Verificar que sidebar, topbar e outros elementos do shell chrome ainda estão presentes.
4. Verificar que a mensagem não revela existência do paciente (texto genérico).
5. Cenário 503: simular `GET /panel/summary` retornando 503 → verificar banner com retry button.
6. Cenário SSE drop + fallback: simular SSE drop → verificar reconexão com backoff; após N falhas → long-poll ativo.

**Non-goals:**
- Não testar modal (DASH não usa modal para erros).

**Layer(s):**
frontend / apps/dash-e2e (type:e2e)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-07-error-handling.md` (§2/§4)

**Architecture alignment:**
- Playwright; `scope:dash`, `type:e2e`

**Implementation checklist (by file):**

**App: apps/dash-e2e**
- 🔲 `apps/dash-e2e/src/error-states.spec.ts`

**Tests**
- 🔲 `npx nx e2e dash-e2e --spec error-states` PASS
- 🔲 `npx nx lint dash-e2e` PASS

**Milestone:**
Wave 9 / e2e + Hardening

**Labels:**
type:e2e
layer:frontend
scope:dash
priority:P9
coverage-gap:G8

**Acceptance criteria:**
- `npx nx e2e dash-e2e --spec error-states` PASS
- 403 → banner "Você não tem acesso a este paciente." inline (G8)
- Shell chrome intacto durante banner 403
- 503 → banner com retry button
- Mensagem 403 não revela existência do paciente

**Dependencies:**
- FE-DASH-022 (ForbiddenBannerComponent)
- FE-DASH-006 (DashHomeFacade — tratamento 403)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Mocks de erros RFC 7807 via Playwright page.route()

**Testing / Evidence required:**
- Screenshot: banner 403 inline com sidebar e topbar visíveis
- Screenshot: banner 503 com botão retry

**Risks / Notes:**
- O mock de 403 deve retornar `Content-Type: application/problem+json` com body `{ type: ".../forbidden-scope", status: 403 }` — verificar que o shared HTTP interceptor parseia corretamente.
