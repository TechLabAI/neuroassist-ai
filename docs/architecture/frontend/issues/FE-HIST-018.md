## [FE-HIST-018] hist feature-timeline: inline error card de carregamento de lista

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`HistoryTimelineComponent` exibe `InlineErrorComponent` (de `@neuroassist/shared/ui-feedback`) quando `status=error`, com `correlationId` visível e botão de retry. Nunca exibe blank screen ou spinner infinito.

**Context / Problem statement:**
- SPEC-06 §1/§2: falhas de carregamento da lista → inline card na região da lista, com retry.
- FE-GAP-12 (Specs-Validate): `correlationId` deve ser surfaced em toda superfície de erro.
- SPEC-07 §4: erro de carregamento → `role="alert"` (assertive `aria-live`).
- SPEC-06 §3: retry manual; sem auto-retry loop.

**Approach (execution path):**
1. Em `HistoryTimelineComponent` template: `@if status==='error'` → `<InlineErrorComponent [error]="facade.screenVm().error" (retry)="facade.retry()" />`.
2. `InlineErrorComponent` exibe `error.correlationId` como texto de suporte.
3. Vincular `role="alert"` ao container de erro.
4. Testar: mock Facade retorna `status=error`; verificar que InlineError renderiza; botão retry chama `facade.retry()`.

**Non-goals:**
- Não reimplementar `InlineErrorComponent` — usar `@neuroassist/shared/ui-feedback`.

**Layer(s):**
frontend / feature-timeline

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-06-error-states.md` (§1, §2, §3)
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-07-accessibility.md` (§4)

**Architecture alignment:**
- Standalone + OnPush · Tag Nx: `scope:hist`, `type:feature`

**Implementation checklist (by file):**
- 🔲 Atualizar `history-timeline.component.html` — branch `@if status==='error'`
- 🔲 Atualizar `history-timeline.component.spec.ts` — cenários de erro
- 🔲 `nx test hist-feature-timeline` PASS
- 🔲 `nx lint hist-feature-timeline` PASS

**Milestone:** Wave P5 / Error States

**Labels:**
type:feature · layer:frontend · scope:hist · priority:P5

**Acceptance criteria:**
- `status=error` → InlineErrorComponent visível com `correlationId`.
- Botão retry chama `facade.retry()`.
- `role="alert"` no container de erro.
- Nenhum blank screen ou spinner sem feedback.

**Dependencies:**
- FE-HIST-009, FE-HIST-017
