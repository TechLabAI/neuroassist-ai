## [FE-DASH-024] e2e Playwright: home-status calm + role-hide nav + rota /notificacoes (G1, G2)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/dash-e2e/src/home-status-calm.spec.ts` cobre os cenários do feature file existente + coverage gaps G1 (role-hide nav) e G2 (rota /notificacoes acessível e com breadcrumb correto).

**Context / Problem statement:**
- Feature file existente: `docs/architecture/frontend/modules/dash/frontend/e2e/home-status-calm.feature`.
- Coverage gap G1: `WHILE role=cuidadora`, item Onboarding oculto na sidebar.
- Coverage gap G2: `/notificacoes` rota existe e renderiza NotificationCenter com breadcrumb `Status › Notificações`.
- Fonte: SPEC-01 §2/§3; coverage gaps G1 e G2.

**Approach (execution path):**
1. Criar `apps/dash-e2e/src/home-status-calm.spec.ts` em Playwright.
2. Cenários do feature file original (autenticação → Home → StatusCard calm/loading/error).
3. Cenário G1: Login com role `cuidadora` → verificar que link "Onboarding" não está na sidebar.
4. Cenário G1b: Login com role `coordenador_clinica` → verificar que link "Onboarding" está na sidebar.
5. Cenário G2: Login → navegar para `/notificacoes` → verificar breadcrumb `Status › Notificações` presente.
6. Usar fixtures de mock de API (Playwright `page.route()`) para simular `GET /panel/summary` e auth.

**Non-goals:**
- Não testar fluxos de autenticação completos (ACS e2e — escopo separado).

**Layer(s):**
frontend / apps/dash-e2e (type:e2e)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/e2e/home-status-calm.feature`
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-01-route-map.md` (§2/§3)

**Architecture alignment:**
- Playwright; `scope:dash`, `type:e2e`

**Implementation checklist (by file):**

**App: apps/dash-e2e**
- 🔲 `apps/dash-e2e/src/home-status-calm.spec.ts`
- 🔲 `apps/dash-e2e/src/fixtures/auth.fixture.ts` (mock JWT + sessão por role)
- 🔲 `apps/dash-e2e/src/fixtures/panel-api.fixture.ts` (mock GET /panel/summary)

**Tests**
- 🔲 `npx nx e2e dash-e2e --spec home-status-calm` PASS
- 🔲 `npx nx lint dash-e2e` PASS

**Milestone:**
Wave 9 / e2e + Hardening

**Labels:**
type:e2e
layer:frontend
scope:dash
priority:P9
coverage-gap:G1
coverage-gap:G2

**Acceptance criteria:**
- `npx nx e2e dash-e2e --spec home-status-calm` PASS
- Role `cuidadora` → "Onboarding" ausente da sidebar (G1)
- Role `coordenador_clinica` → "Onboarding" presente (G1b)
- `/notificacoes` acessível e breadcrumb correto (G2)

**Dependencies:**
- FE-DASH-017 (roleGuard)
- FE-DASH-013 (NotificationCenterPageComponent)
- FE-DASH-020 (breadcrumb)

**Data impact:**
- Nenhum (mocks de API)

**API / Contract impact:**
- Mocks de `/panel/summary` e auth via Playwright page.route()

**Testing / Evidence required:**
- Screenshot: sidebar com e sem item Onboarding por role
- Screenshot: breadcrumb `Status › Notificações`

**Risks / Notes:**
- Playwright e2e requer o app rodando ou `page.route()` para interceptar; definir estratégia (MSW ou Playwright nativo).
