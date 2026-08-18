## [FE-DASH-004] Shell: registrar DASH_MODULE_DESCRIPTOR em module-descriptors.ts

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/neuroassist-ai-web/src/app/config/module-descriptors.ts` importa e inclui `DASH_MODULE_DESCRIPTOR` na lista de descritores do shell, tornando DASH a rota default (`/`) do painel do cuidador.

**Context / Problem statement:**
- O shell só monta DASH se o descriptor estiver registrado na configuração central.
- DASH é o módulo de landing (order: 0, route: `/`); a ordem do registro importa.
- Fonte: SPEC-06 §1; generator-config.yml `shell_route: /`.
- Bloqueia: INT-FE-DASH-001..003, FE-DASH-020

**Approach (execution path):**
1. Abrir `apps/neuroassist-ai-web/src/app/config/module-descriptors.ts`.
2. Importar `DASH_MODULE_DESCRIPTOR` de `@neuroassist/dash`.
3. Adicionar o descriptor ao array `MODULE_DESCRIPTORS` na posição 0 (DASH é default landing).
4. Verificar que o app shell define `{ path: '', redirectTo: '/', pathMatch: 'full' }` ou usa o loadChildren do DASH_MODULE_DESCRIPTOR na rota raiz.
5. Garantir que `/entrada` renderiza sem o shell chrome (configuração de rota ou layout strategy no shell).

**Non-goals:**
- Não criar o shell app (já existe).
- Não implementar o descriptor (FE-DASH-001/003).

**Layer(s):**
frontend / apps/neuroassist-ai-web (scope:web)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-06-shell-integration.md` (§1)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-01-route-map.md` (§1)

**Architecture alignment:**
- Tag Nx: `scope:web`, `type:app`
- `scope:dash` NÃO importa `scope:web` — sentido único

**Implementation checklist (by file):**

**App: apps/neuroassist-ai-web**
- 🔲 `apps/neuroassist-ai-web/src/app/config/module-descriptors.ts` (adicionar DASH_MODULE_DESCRIPTOR)
- 🔲 `apps/neuroassist-ai-web/src/app/app.routes.ts` (verificar rota `/` → loadChildren DashModule, `/entrada` → chromeless layout)

**Tests**
- 🔲 `npx nx test neuroassist-ai-web` PASS
- 🔲 `npx nx lint neuroassist-ai-web` PASS

**Milestone:**
Wave 1 / Foundation

**Labels:**
type:feature
layer:frontend
scope:web
priority:P1

**Acceptance criteria:**
- `npx nx test neuroassist-ai-web` PASS
- `npx nx lint neuroassist-ai-web` PASS
- `npx nx build neuroassist-ai-web` PASS
- Navegando para `/` no browser, o DASH monta como landing padrão
- `/entrada` renderiza sem sidebar/topbar (chromeless — coverage gap G3)

**Dependencies:**
- FE-DASH-001 (dash.contract.ts)
- FE-DASH-003 (dash.descriptor.ts)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Screenshot da rota `/` mostrando shell chrome com DASH montado
- Screenshot da rota `/entrada` sem sidebar nem topbar

**Risks / Notes:**
- Layout chromeless para `/entrada` pode exigir estratégia de layout no shell (e.g., guard de layout ou rota fora do shell wrapper); validar com o time de plataforma.
