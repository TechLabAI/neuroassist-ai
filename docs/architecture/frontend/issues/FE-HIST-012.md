## [FE-HIST-012] hist ui: UrgencyBadgeComponent (token-driven, WCAG AA)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`UrgencyBadgeComponent` em `libs/hist/ui` — badge de urgência token-driven (`rotina`/`atencao`/`critico`) com texto + ícone + cor, satisfazendo WCAG AA de independência de cor.

**Context / Problem statement:**
- SPEC-02 §4: input `urgency: Urgency`.
- SPEC-05 §5: tokens de `@neuroassist/shared/ui-tokens`; urgência via texto AND cor, nunca só cor.
- SPEC-07 §2 + FE-GAP-13 (WCAG AA): badge deve ser legível por screen-reader e sem dependência de cor.
- Paleta do EVNT via `shared/ui-tokens` (não importar de `scope:evnt` diretamente — boundary rule).

**Approach (execution path):**
1. `UrgencyBadgeComponent` standalone, OnPush; input `urgency = input<Urgency>()`.
2. Template: `<span [class]="urgencyClass()">{{ urgencyLabel() }} <mat-icon/></span>`.
3. `urgencyLabel()`: `rotina` → "Rotina", `atencao` → "Atenção", `critico` → "Crítico".
4. CSS usa tokens de `@neuroassist/shared/ui-tokens` (custom properties); contraste ≥ 4.5:1.
5. `aria-label` no span com texto de urgência (se ícone decorativo, `aria-hidden="true"`).

**Non-goals:**
- Não importar estilos de `scope:evnt` — usar apenas `scope:shared`.

**Layer(s):**
frontend / ui

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-05-ui-ux-spec.md` (§5)
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-07-accessibility.md` (§2, §6)

**Architecture alignment:**
- Standalone + OnPush · Tag Nx: `scope:hist`, `type:ui`

**Implementation checklist (by file):**
- 🔲 `libs/hist/ui/src/lib/urgency-badge/urgency-badge.component.ts`
- 🔲 `libs/hist/ui/src/lib/urgency-badge/urgency-badge.component.html`
- 🔲 `libs/hist/ui/src/lib/urgency-badge/urgency-badge.component.scss`
- 🔲 `libs/hist/ui/src/lib/urgency-badge/urgency-badge.component.spec.ts`
- 🔲 Atualizar `libs/hist/ui/src/index.ts`
- 🔲 `nx test hist-ui` PASS
- 🔲 `nx lint hist-ui` PASS

**Milestone:** Wave P3 / Feature Timeline

**Labels:**
type:feature · layer:frontend · scope:hist · priority:P3

**Acceptance criteria:**
- Cada urgência exibe texto + ícone (não só cor).
- Contraste de token validado ≥ 4.5:1 (teste manual ou axe).
- Screen-reader anuncia o nível de urgência.
- `npx nx test hist-ui` PASS.

**Dependencies:**
- FE-HIST-011, FE-HIST-020

**Risks / Notes:**
- Verificar se `@neuroassist/shared/ui-tokens` exporta a paleta de urgência já validada para AA; se não, adicionar variável com contraste documentado.
