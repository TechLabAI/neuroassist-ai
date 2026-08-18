## [FE-DASH-003] dash-descriptor.ts — fallback estático para o Shell

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/dash/shell-entry/src/lib/dash.descriptor.ts` exporta `DASH_MODULE_DESCRIPTOR` como fallback estático, garantindo que o DASH monte mesmo quando o registry de runtime estiver indisponível.

**Context / Problem statement:**
- O Shell usa o descriptor estático quando o runtime descriptor registry não estiver disponível.
- Deve exportar o mesmo objeto definido em `dash.contract.ts` (SPEC-05 §4).
- Fonte: SPEC-06 §1.
- Bloqueia: FE-DASH-004

**Approach (execution path):**
1. Criar `dash.descriptor.ts` que re-exporta `DASH_MODULE_DESCRIPTOR` de `dash.contract.ts`.
2. Ou criar um arquivo separado que define o descriptor com o mesmo shape — preferir re-export para evitar duplicação.
3. Garantir que o barrel de `dash/shell-entry` exporte `DASH_MODULE_DESCRIPTOR` pelo nome canônico.

**Non-goals:**
- Não implementar o runtime descriptor registry (responsabilidade de shared/ui-components).

**Layer(s):**
frontend / libs/dash/shell-entry (type:domain)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-06-shell-integration.md` (§1)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-05-tool-contract.md` (§4)

**Architecture alignment:**
- Tag Nx: `scope:dash`, `type:domain`

**Implementation checklist (by file):**

**Lib: libs/dash/shell-entry**
- 🔲 `libs/dash/shell-entry/src/lib/dash.descriptor.ts`
- 🔲 `libs/dash/shell-entry/src/index.ts` (barrel — verificar export de DASH_MODULE_DESCRIPTOR)

**Tests**
- 🔲 `npx nx test dash-shell-entry` PASS
- 🔲 `npx nx lint dash-shell-entry` PASS

**Milestone:**
Wave 1 / Foundation

**Labels:**
type:domain
layer:frontend
scope:dash
priority:P1

**Acceptance criteria:**
- `npx nx test dash-shell-entry` PASS
- `npx nx lint dash-shell-entry` PASS
- `npx nx build dash-shell-entry` PASS
- `DASH_MODULE_DESCRIPTOR.shellOutletRegions` contém `'dash.global-interrupt'`

**Dependencies:**
- FE-DASH-001 (dash.contract.ts)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Smoke test verifica que o descriptor é igual ao importado de dash.contract.ts

**Risks / Notes:**
- Manter em sincronia com `dash.contract.ts`; se a interface `ModuleDescriptor` mudar, ambos os arquivos precisam ser atualizados.
