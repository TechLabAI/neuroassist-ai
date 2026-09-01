## [TREI-FE-001] TREI_TOOL_KEY + TREI_MENU_METADATA (tool.contract.ts)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
O arquivo `libs/trei/shell-entry/src/lib/trei.contract.ts` existe com `TREI_TOOL_KEY`, `TREI_MENU_METADATA` (surface: caregiver, icon: model_training, badgeKey AI-authoring) e `TREI_ROUTES` stub que aponta para os `loadComponent` lazy de cada feature.

**Context / Problem statement:**
- TREI é um módulo novo no shell da cuidadora (`neuroassist-ai-web`). O shell precisa de um descriptor de menu estático antes de qualquer carregamento lazy para exibir "Treinamento" na sidebar (SPEC-05 §1, SPEC-06 §1).
- `surface: 'caregiver'` e `registerInAdminShell: true` são obrigatórios — TREI nunca aparece no shell do paciente (INV-6, ADR-007).
- O `badgeKey` `'trei.menu.authoringBadge'` é a hint de menu do affordance "não é o app do paciente" (TECH-007).
- Bloqueia: TREI-FE-004 (usa `TREI_ROUTES`), TREI-FE-005 (usa `TREI_MODULE_DESCRIPTOR`), INT-FE-TREI-004 (shell registration).

**Approach (execution path):**
1. Criar `libs/trei/shell-entry/src/lib/trei.contract.ts`.
2. Exportar `TREI_TOOL_KEY = 'trei' as const`.
3. Exportar `TREI_MENU_METADATA: ToolMenuMetadata` com `key`, `labelKey: 'trei.menu.title'`, `route: '/treinamento/sessao'`, `surface: 'caregiver'`, `registerInAdminShell: true`, `icon: 'model_training'`, `badgeKey: 'trei.menu.authoringBadge'`.
4. Exportar `TREI_ROUTES: Routes` stub com a entrada `{ path: 'treinamento', loadChildren: () => import('@neuroassist/trei').then(m => m.TreiModule) }` — o detalhe interno das rotas vai em TREI-FE-003.
5. Verificar que o tipo `ToolMenuMetadata` é importado de `@neuroassist/shared/access` (não redeclará-lo).

**Non-goals:**
- Não implementar guards nesta issue (TREI-FE-022/023).
- Não registrar o descriptor no `module-descriptors.ts` do shell (INT-FE-TREI-004).
- Não definir `TREI_PERMISSION_MAP` nem `TREI_FEATURE_FLAGS` aqui (TREI-FE-002/003).

**Layer(s):**
frontend / libs/trei/shell-entry

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-05-tool-contract.md` (§1)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-06-shell-integration.md` (§1)

**Architecture alignment:**
- Standalone + OnPush + Signals (nenhum componente aqui, apenas constantes)
- Tag Nx: `scope:trei`, `type:domain`
- Nenhuma dependência circular: `scope:trei` nunca importa `scope:web`

**Implementation checklist (by file)**

**libs/trei/shell-entry**
- [ ] `libs/trei/shell-entry/src/lib/trei.contract.ts`
- [ ] exporta `TREI_TOOL_KEY`, `TREI_MENU_METADATA`, `TREI_ROUTES` (stub)

**Tests**
- [ ] `nx lint trei-shell-entry` PASS (boundaries + ESLint)
- [ ] Compilação TypeScript sem erros (`nx build trei-shell-entry`)

**Milestone:**
Wave 1 / Foundation

**Labels:**
type:feature
layer:frontend
scope:trei
priority:P1

**Acceptance criteria:**
- `npx nx lint trei-shell-entry` PASS
- `npx nx build trei-shell-entry` PASS (sem erros TypeScript)
- `TREI_MENU_METADATA.surface === 'caregiver'` — verificável em teste de snapshot
- `TREI_MENU_METADATA.registerInAdminShell === true`
- Nenhum import de `scope:web` em `scope:trei`

**Dependencies:**
- Nenhuma (primeira issue do módulo)

**Data impact:**
- Nenhum (FE não persiste dados client-side além de auth-session storage)

**API / Contract impact:**
- Nenhum (constantes estáticas)

**Testing / Evidence required:**
- Screenshot da sidebar com item "Treinamento" visível (depois do INT-FE-TREI-004)
- `nx lint` clean

**Risks / Notes:**
- Confirmar que `ToolMenuMetadata` já existe em `@neuroassist/shared/access`; se não, checar `@neuroassist/shared/shell-contracts` antes de criar um tipo local.
