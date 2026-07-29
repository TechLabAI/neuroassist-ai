## [PAC-FE-015] feature-command-editor — CommandEditorComponent + form sub-components + LifecycleActionBar

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/feature-command-editor` implementa `CommandEditorComponent` (smart) com todos seus filhos apresentacionais: `CommandFormComponent`, `RotuloFieldComponent`, `UrgencySelectorComponent`, `GesturePickerComponent`, `SensitivitySelectorComponent`, `TimingWindowComponent`, `LifecycleActionBarComponent` e `CommandPreviewComponent` — cobrindo a criação/edição de draft, validação síncrona de formulário, gating de lifecycle por estado e o slot de composição para o GEST live-test (sem importar `scope:gest`).

**Context / Problem statement:**
- É a feature mais complexa do PAC: cobre 42 cenários Gherkin do `command-editor.feature`.
- Validações síncronas do formulário: rótulo obrigatório, `tempoMin < tempoMax`, `primitivoSecundario != primitivoPrincipal` — todas desabilitam "Salvar" via signal derivado `canSave`.
- `LifecycleActionBarComponent` renderiza botões condicionalmente por `estado` (drafted/validated/published/retired) e por permissão `pac.comando.publish` (SPEC-05 §2, SPEC-03 §2).
- O slot `<ng-content select="[gest-live-test]">` é declarado no template do `CommandEditorComponent` — o shell page insere o `GestureLiveTestComponent` neste slot (SPEC-06 §2, SPEC-02 §2 nota). PAC never imports `scope:gest`.
- `SyncStatusBannerComponent` mostra "pendente de sincronização" quando `editorVM.syncState === 'pending'` (ADR-005).
- `GesturePickerComponent` inclui o affordance "+ novo tipo" (gated pelo flag `pac.inlineNewTipoGesto`) que emite `@Output addNew(nome)` para o smart component despachar ao facade.
- Bloqueia: PAC-FE-024 (e2e command-editor)

**Approach (execution path):**
1. Criar `command-editor.component.ts` (smart, standalone, OnPush). Injetar `PacComandoFacade`, `TiposGestoReadFacade`, `ActivatedRoute`. No `ngOnInit`: if `:comandoId` → `facade.startEdit(id)` else `facade.startNew()`.
2. Criar `command-form.component.ts` (presentacional): `@Input draft: ComandoFormVM`, `@Output change: EventEmitter<Partial<ComandoFormVM>>`.
3. Criar `rotulo-field.component.ts`: validação síncrona — emite `changeRotulo`; status inválido se vazio.
4. Criar `urgency-selector.component.ts`: dropdown/radio para `emergencia|rotina`.
5. Criar `gesture-picker.component.ts`: lista `tipos`; "+ novo tipo" se flag on → emite `addNew(nome)`. Inputs: `tipos: TipoGestoVM[]`, `principal: string`, `secundario: string | null`. Guarda: `secundario !== principal`.
6. Criar `sensitivity-selector.component.ts`: `baixa|media|alta` (NUNCA threshold raw — SPEC-03 §1 / SPEC-05).
7. Criar `timing-window.component.ts`: validação `min < max` síncrona.
8. Criar `lifecycle-action-bar.component.ts`: inputs `estado`, `canSave`, `canValidate`, `canPublish`, `canRecalibrate`, `canRetire`; outputs para cada ação.
9. Criar `command-preview.component.ts`: render humanizado do draft (sem thresholds raw).
10. `SyncStatusBannerComponent` (reutiliza o de PAC-FE-020 ou cria local se diferente — a implementar na mesma issue).
11. Unit tests para cada componente individualmente; integration test para o smart component com facade mock.

**Non-goals:**
- Sem implementação do `GestureLiveTestComponent` (GEST-owned) — apenas o slot de ng-content.
- Sem routing logic — o smart component não navega; o shell faz isso via eventos.

**Layer(s):**
frontend / libs/pac/feature-command-editor

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§2)
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-03-view-models.md` (§2 EditorVM)

**Architecture alignment:**
- Standalone + OnPush; Signals-only no smart; `@Input`/`@Output` nos apresentacionais
- Tag Nx: `scope:pac`, `type:feature`
- Zero imports de `scope:gest`

**Implementation checklist (by file):**

**Lib: libs/pac/feature-command-editor**
- [ ] `libs/pac/feature-command-editor/src/lib/command-editor.component.ts`
- [ ] `libs/pac/feature-command-editor/src/lib/command-form.component.ts`
- [ ] `libs/pac/feature-command-editor/src/lib/rotulo-field.component.ts`
- [ ] `libs/pac/feature-command-editor/src/lib/urgency-selector.component.ts`
- [ ] `libs/pac/feature-command-editor/src/lib/gesture-picker.component.ts`
- [ ] `libs/pac/feature-command-editor/src/lib/sensitivity-selector.component.ts`
- [ ] `libs/pac/feature-command-editor/src/lib/timing-window.component.ts`
- [ ] `libs/pac/feature-command-editor/src/lib/lifecycle-action-bar.component.ts`
- [ ] `libs/pac/feature-command-editor/src/lib/command-preview.component.ts`
- [ ] `libs/pac/feature-command-editor/src/lib/command-editor.component.spec.ts`

**Tests**
- [ ] `npx nx test pac-feature-command-editor` PASS
- [ ] `npx nx lint pac-feature-command-editor` PASS
- [ ] `npx nx build pac-feature-command-editor` PASS

**Milestone:**
Wave 3 / P3 Feature Libs

**Labels:**
type:feature
layer:frontend
scope:pac
priority:P3

**Acceptance criteria:**
- `npx nx test pac-feature-command-editor` PASS: rótulo vazio → `canSave === false`.
- `npx nx test pac-feature-command-editor` PASS: `tempoMin >= tempoMax` → `canSave === false`.
- `npx nx test pac-feature-command-editor` PASS: `secundario === principal` → `canSave === false`.
- `npx nx test pac-feature-command-editor` PASS: `estado === 'retired'` → todos os lifecycle buttons disabled.
- `npx nx test pac-feature-command-editor` PASS: `estado === 'drafted'` → validar enabled, publicar disabled.
- `npx nx test pac-feature-command-editor` PASS: `sensibilidade` renderiza `'baixa'|'media'|'alta'` nunca threshold numérico.
- `npx nx lint pac-feature-command-editor` PASS: zero imports `scope:gest`.

**Dependencies:**
- PAC-FE-011 (PacComandoFacade)
- PAC-FE-012 (TiposGestoReadFacade)
- PAC-FE-013 (EditorVM, ComandoFormVM)
- PAC-FE-020 (SyncStatusBannerComponent)

**Data impact:**
Nenhum direto — despacha ao facade.

**API / Contract impact:**
- Editor → facade → todos os endpoints do PacComandoClient.

**Testing / Evidence required:**
- Screenshots: blank draft (editing), validated state (publicar enabled), retired (tudo disabled), syncState banner.
- EARS orphans mapeados: cenários de "editar identity de comando not-drafted → recalibrate routing", "inline novo tipo affordance hidden when flag off", "GEST live-test composição no shell sem importar scope:gest".

**Risks / Notes:**
- O slot `<ng-content>` do GEST live-test deve ser testado com `ContentChild` mock no unit test para garantir que o binding de `@Input` do GEST funciona via projeção.
- A validação síncrona de `timingWindow` (min < max) deve ser Angular `Validator` ou signal computed — não lógica no template.
