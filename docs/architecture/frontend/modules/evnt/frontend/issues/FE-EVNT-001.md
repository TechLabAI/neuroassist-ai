## [FE-EVNT-001] evnt.contract.ts — permission map, feature flags, module key

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/evnt/shell-entry/src/lib/evnt.contract.ts` está criado com `MODULE_KEY`, `EVNT_MENU_METADATA`, `EVNT_PERMISSION_MAP`, `EVNT_FEATURE_FLAGS` e `EVNT_MODULE_DESCRIPTOR` exportados — o contrato público do módulo que o Shell e outros consumidores lêem.

**Context / Problem statement:**
- O Shell precisa de um contrato estático para registrar rotas, menu e flags do módulo EVNT (SPEC-05).
- `EVNT_MODULE_DESCRIPTOR` inclui o hook `alarmOutlet` que permite ao Shell montar o `AlarmOverlayComponent` fora do router-outlet (ADR-005).
- Bloqueia: FE-EVNT-003 (EvntModule), FE-EVNT-004 (tool-descriptor Shell), FE-EVNT-010 (polling flag).

**Approach (execution path):**
1) Criar `libs/evnt/shell-entry/src/lib/evnt.contract.ts` com os três objetos `const` (`EVNT_MENU_METADATA`, `EVNT_PERMISSION_MAP`, `EVNT_FEATURE_FLAGS`) exatamente como especificado em SPEC-05 §1.
2) Exportar `EVNT_MODULE_DESCRIPTOR` com os campos `key`, `menu`, `permissions`, `flags`, `loadChildren`, `alarmOutlet` (SPEC-05 §4).
3) Re-exportar tudo via `index.ts` da lib (`@neuroassist/evnt`).
4) Verificar que nenhum valor de flag ou permission é hardcoded fora deste arquivo.

**Non-goals:**
- Implementação de guards ou serviços (FE-EVNT-019/020).
- Registrar o descriptor no Shell (FE-EVNT-004).

**Layer(s):**
frontend / shell-entry (tool contract)

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-05-tool-contract.md` (§1-§4)

**Architecture alignment:**
- Standalone + OnPush + Signals
- Tag Nx: `scope:evnt`, `type:domain`
- Nenhum import de libs de feature ou data-access neste arquivo (contrato puro)

**Implementation checklist (by file):**

**Lib shell-entry**
- [ ] `libs/evnt/shell-entry/src/lib/evnt.contract.ts` — `MODULE_KEY`, `EVNT_MENU_METADATA`, `EVNT_PERMISSION_MAP`, `EVNT_FEATURE_FLAGS`, `EVNT_MODULE_DESCRIPTOR`
- [ ] `libs/evnt/shell-entry/src/index.ts` — re-exporta `evnt.contract.ts`

**Tests**
- [ ] `nx test evnt-shell-entry` PASS
- [ ] `nx lint evnt-shell-entry` PASS

**Milestone:**
Wave P1 / Foundation

**Labels:**
type:feature
layer:frontend
scope:evnt
priority:P1

**Acceptance criteria:**
- `npx nx test evnt-shell-entry` PASS
- `npx nx lint evnt-shell-entry` PASS
- `npx nx build evnt-shell-entry` PASS
- `EVNT_MODULE_DESCRIPTOR.alarmOutlet` retorna `AlarmOverlayComponent` via dynamic import

**Dependencies:**
- (nenhuma — é a base da hierarquia)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum diretamente; define os nomes de permission que o BE valida (`evnt.view`, `evnt.confirm`, `evnt.routine.view`)

**Testing / Evidence required:**
- Unit: importar o contrato e verificar que os campos existem com os valores corretos (snapshot test)

**Risks / Notes:**
- FLAG-01 gap (M3.5): `liveChannel` default `true` — implementação em FE-EVNT-007/010 precisa ler esta constante, não hardcodar.
- FLAG-02 gap (M3.5): `routineNotices` default `true` — FE-EVNT-014 precisa verificar esta flag antes de renderizar rotinas.
