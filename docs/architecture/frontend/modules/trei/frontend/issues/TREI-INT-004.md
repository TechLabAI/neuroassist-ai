## [TREI-INT-004] Integration com shell neuroassist-ai-web — module-descriptors.ts + sidebar ordering + kill-switch

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/neuroassist-ai-web/src/app/config/module-descriptors.ts` inclui `TREI_MODULE_DESCRIPTOR` no array de descriptors — o item "Treinamento" aparece na sidebar da cuidadora, com kill-switch `treinamentoEnabled` funcional.

**Context / Problem statement:**
- A integração entre TREI e o shell é **unidireccional**: o shell importa `TREI_MODULE_DESCRIPTOR` de `@neuroassist/trei`; TREI nunca importa do shell (ADR-006).
- `module-descriptors.ts` é o único ponto de acoplamento estático entre o shell e o módulo TREI — qualquer outra integração é via `TreiShellIntegrationService` em runtime.
- O kill-switch `treinamentoEnabled: false` deve ocultar o item de menu e não registar as rotas TREI — verificável via feature flag service do shell.
- Requer decisão de posição na sidebar (após HIST ou DASH) — deixar para o scaffold (SPEC-06 §4 "open scaffold decision").
- Bloqueia: o smoke test de arranque do app (sidebar item "Treinamento" visível).

**Approach (execution path):**
1. Em `apps/neuroassist-ai-web/src/app/config/module-descriptors.ts`:
   - Adicionar `import { TREI_MODULE_DESCRIPTOR } from '@neuroassist/trei'`.
   - Incluir `TREI_MODULE_DESCRIPTOR` no array `MODULE_DESCRIPTORS`.
2. Verificar que o shell lê `TREI_MODULE_DESCRIPTOR.flags.treinamentoEnabled` para condicionar o menu item.
3. Testar smoke: `ng serve` → sidebar mostra "Treinamento" com ícone `model_training`.
4. Testar kill-switch: com `treinamentoEnabled: false` → item oculto.

**Non-goals:**
- Não alterar outros descritores no `module-descriptors.ts`.
- Não implementar o `TreiShellIntegrationService` aqui (TREI-FE-006).

**Layer(s):**
frontend / apps/neuroassist-ai-web · libs/trei/shell-entry

**Bounded context:**
shell → trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-06-shell-integration.md` (§1/§2)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-05-tool-contract.md` (§4)

**Architecture alignment:**
- `scope:web` importa `@neuroassist/trei` via descriptor apenas (sem acesso a internals)
- Tag Nx: `scope:web`, `type:app`

**Implementation checklist (by file)**

**apps/neuroassist-ai-web**
- [ ] `apps/neuroassist-ai-web/src/app/config/module-descriptors.ts` — adicionar `TREI_MODULE_DESCRIPTOR`

**libs/trei/shell-entry**
- [ ] Barrel `index.ts` exporta `TREI_MODULE_DESCRIPTOR` (já coberto por TREI-FE-004)

**Tests**
- [ ] `nx build neuroassist-ai-web` PASS após adição do descriptor
- [ ] `nx lint neuroassist-ai-web` PASS
- [ ] Smoke: sidebar item "Treinamento" visível

**Milestone:**
Wave 1 / Foundation (integração crítica)

**Labels:**
type:integration
layer:frontend
scope:trei
priority:P1

**Acceptance criteria:**
- `npx nx build neuroassist-ai-web` PASS
- Sidebar: item "Treinamento" com ícone `model_training` visível no shell da cuidadora
- Kill-switch: `treinamentoEnabled = false` → item não aparece na sidebar

**Dependencies:**
- TREI-FE-004 (TREI_MODULE_DESCRIPTOR — deve existir antes desta issue)
- TREI-FE-001/002/003 (contratos incluídos no descriptor)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum HTTP

**Testing / Evidence required:**
- Screenshot: sidebar com item "Treinamento" activo
- Smoke test de build sem erros após adição do import

**Risks / Notes:**
- A posição do item na sidebar (ordem relativa a HIST, DASH, etc.) é uma decisão de UX do scaffold — esta issue apenas faz a integração; a posição final é deferred.
- Confirmar que `@neuroassist/trei` já está no `tsconfig.base.json` path mapping antes de fazer o import.
