## [PAC-FE-020] SyncStatusBannerComponent + honest-state ADR-005 surface

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`SyncStatusBannerComponent` (em `libs/pac/ui` ou `feature-command-editor`) exibe o banner "pendente de sincronização" quando `editorVM.syncState === 'pending'`, implementando o ADR-005 honest-state: nenhum comando publicado/salvo é marcado como "ativo no dispositivo" até que o EDGE device-ack seja recebido via INT-FE-PAC-001.

**Context / Problem statement:**
- ADR-005 (FE): "draft-first authoring — comando salvo fica 'pendente de sincronização' até device-ack (EDGE)".
- O banner é exibido no editor (`CommandEditorComponent`) sempre que `syncState === 'pending'`.
- Feature flag `pac.deferredEdgeSync=off` colapsa o badge para um único estado "salvo" (sem diferenciação — SPEC-05 §3); quando on, o banner persiste até device-ack.
- Como INT-FE-PAC-001 stub mantém sempre `'pending'`, o banner será sempre visível no MVP enquanto o contrato EDGE não existir.
- SPEC-07 não categoriza o estado "pendente" como erro — é estado informativo, não bloqueante.

**Approach (execution path):**
1. Criar `sync-status-banner.component.ts` (standalone, OnPush). `@Input syncState: 'synced' | 'pending'`. Quando `pending`: renderizar "Este comando está pendente de sincronização com o dispositivo edge. As alterações serão aplicadas quando o dispositivo se reconectar."
2. Quando `synced`: renderizar nenhum banner (ou estado sucinto "Ativo no dispositivo").
3. Integrar no `CommandEditorComponent` (PAC-FE-015): `<pac-sync-status-banner [syncState]="editorVM().syncState" />`.
4. Unit test: `syncState='pending'` → banner presente no DOM; `syncState='synced'` → banner ausente.

**Non-goals:**
- Sem polling ou WebSocket para verificar sync (responsabilidade do INT-FE-PAC-001).
- Sem botão de "forçar sync" — apenas informativo.

**Layer(s):**
frontend / libs/pac/ui

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§2 SyncStatusBannerComponent)
- `docs/architecture/frontend/modules/pac/frontend/adr/ADR-005-command-edit-not-live-until-synced.md`

**Architecture alignment:**
- Standalone + OnPush
- Tag Nx: `scope:pac`, `type:ui`

**Implementation checklist (by file):**

**Lib: libs/pac/ui**
- [ ] `libs/pac/ui/src/lib/sync-status-banner.component.ts`
- [ ] `libs/pac/ui/src/lib/sync-status-banner.component.spec.ts`

**Tests**
- [ ] `npx nx test pac-ui` PASS
- [ ] `npx nx lint pac-ui` PASS

**Milestone:**
Wave 7 / P7 UX Polish

**Labels:**
type:feature
layer:frontend
scope:pac
priority:P7

**Acceptance criteria:**
- `npx nx test pac-ui` PASS: `syncState='pending'` → elemento `.sync-status-banner` presente no DOM com texto "pendente de sincronização".
- `npx nx test pac-ui` PASS: `syncState='synced'` → banner ausente ou collapsed.
- EARS orphan coberto: "Saved comando edit not acknowledged stays pendente de sincronização not ativo no dispositivo".

**Dependencies:**
- PAC-FE-011 (PacComandoFacade — `editorVM.syncState` signal)
- INT-FE-PAC-001 (stub que mantém syncState='pending' até device-ack real)

**Data impact:**
Nenhum.

**API / Contract impact:**
- Indiretamente: quando INT-FE-PAC-001 implementar o device-ack, o banner transita para 'synced'.

**Testing / Evidence required:**
- Screenshot do editor com banner "pendente de sincronização" visível após save.

**Risks / Notes:**
- Gap rastreado: EDGE device-ack signal NÃO contratado no MVP (ver backend-api-cross-link.md §5 e INT-FE-PAC-001). O banner permanecerá sempre visível até o contrato EDGE ser implementado e o adapter real substituir o stub.
