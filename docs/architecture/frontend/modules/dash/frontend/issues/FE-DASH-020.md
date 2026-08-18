## [FE-DASH-020] libs/dash/navigation — BreadcrumbItem[] para Status e Notificações

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/dash/navigation` implementa a lógica de breadcrumb para as rotas DASH: `Status` (root) e `Status › Notificações`, alimentando o `BreadcrumbService` do shell.

**Context / Problem statement:**
- O breadcrumb do shell consome `BreadcrumbItem[]` por rota; DASH define suas próprias entradas.
- `/entrada` não tem breadcrumb (chromeless).
- `/` → `[{ label: 'Status', route: '/' }]`.
- `/notificacoes` → `[{ label: 'Status', route: '/' }, { label: 'Notificações', route: '/notificacoes' }]`.
- Fonte: SPEC-01 §4.

**Approach (execution path):**
1. Criar lib Nx `dash/navigation` (type:domain, scope:dash).
2. Definir constante `DASH_BREADCRUMBS`:
   - `home: [{ label: 'Status', route: '/' }]`
   - `notifications: [{ label: 'Status', route: '/' }, { label: 'Notificações', route: '/notificacoes' }]`
3. Usar `data.breadcrumb` nas rotas (já configurado em FE-DASH-002) ou integrar com o `BreadcrumbService` do shell via `DashShellIntegrationService`.
4. Garantir que `/entrada` não contribui nenhum breadcrumb.

**Non-goals:**
- Não implementar o `BreadcrumbService` global (shared).
- Não incluir breadcrumb de rotas de outros módulos.

**Layer(s):**
frontend / libs/dash/navigation (type:domain)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-01-route-map.md` (§4)

**Architecture alignment:**
- Tag Nx: `scope:dash`, `type:domain`

**Implementation checklist (by file):**

**Lib: libs/dash/navigation**
- 🔲 `libs/dash/navigation/src/lib/dash-breadcrumbs.ts` (constantes)
- 🔲 `libs/dash/navigation/src/lib/dash-navigation.service.ts` (opcional — integração com BreadcrumbService do shell)
- 🔲 `libs/dash/navigation/src/index.ts` (barrel)

**Tests**
- 🔲 `npx nx test dash-navigation` PASS (testar: /notificacoes tem 2 itens; / tem 1 item; /entrada sem breadcrumb)
- 🔲 `npx nx lint dash-navigation` PASS

**Milestone:**
Wave 6 / Navigation

**Labels:**
type:domain
layer:frontend
scope:dash
priority:P6

**Acceptance criteria:**
- `npx nx test dash-navigation` PASS
- `npx nx lint dash-navigation` PASS
- `npx nx build dash-navigation` PASS
- `/notificacoes` breadcrumb: `[Status, Notificações]`
- `/` breadcrumb: `[Status]`
- `/entrada` sem breadcrumb

**Dependencies:**
- FE-DASH-002 (rotas com data.breadcrumb)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Screenshot do breadcrumb em `/notificacoes`: "Status > Notificações"

**Risks / Notes:**
- Se o shell usa `ActivatedRoute.data.breadcrumb` para construir o trail automaticamente, basta configurar `data` nas rotas (FE-DASH-002) e esta lib fica como utilitário de constantes; adaptar conforme a implementação do BreadcrumbService.
