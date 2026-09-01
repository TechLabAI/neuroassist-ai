## [TREI-FE-017] LocaleTabsComponent (PT/EN switch, activeLocale binding)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/trei/feature-training-session/src/lib/locale-tabs.component.ts` renderiza as abas PT/EN para o utilizador trocar o locale de autoria activo — `input(activeLocale)`, `input(locales)`, `output(localeChange)`.

**Context / Problem statement:**
- O locale de autoria é estado de ecrã (não um route param) e é o `activeLocale` da `TrainingSessionFacade` (SPEC-01 §1, SPEC-03 §2 INV-3).
- O componente é puramente presentacional: recebe o locale activo via `input()` e emite a mudança via `output()` — não toca a facade directamente.
- Trocar locale re-projecta as proposals no `ProposalReviewFacade` para o novo locale (via `facade.switchLocale()` chamado pelo smart component pai).
- O scope do bilinguismo é `{pt, en}` por ADR-018.

**Approach (execution path):**
1. Criar `locale-tabs.component.ts`: `standalone: true`, `OnPush`.
2. `input()`: `activeLocale: Locale`, `locales: Locale[]` (default `['pt', 'en']`).
3. `output()`: `localeChange: OutputEmitterRef<Locale>`.
4. Template: duas abas com labels "PT" / "EN", a activa destacada.
5. Click na aba inactiva emite `localeChange` com o novo locale.
6. A11y: `role="tablist"` + `role="tab"` + `aria-selected`.

**Non-goals:**
- Não chamar `facade.switchLocale()` directamente (o smart component faz isso).
- Não adicionar mais locales (ADR-018 fixa `{pt,en}`).

**Layer(s):**
frontend / libs/trei/feature-training-session

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§2)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-01-route-map.md` (§1 — locale como screen state)

**Architecture alignment:**
- `standalone: true`, `OnPush`
- Tag Nx: `scope:trei`, `type:feature`

**Implementation checklist (by file)**

**libs/trei/feature-training-session**
- [ ] `locale-tabs.component.ts`
- [ ] ARIA: `role="tablist"`, `aria-selected` nas abas

**Tests**
- [ ] `nx test trei-feature-training-session` PASS
- [ ] Click em "EN" emite `localeChange` com `'en'`
- [ ] Aba activa tem `aria-selected="true"`
- [ ] `nx lint trei-feature-training-session` PASS

**Milestone:**
Wave 3 / Feature Libs

**Labels:**
type:feature
layer:frontend
scope:trei
priority:P3

**Acceptance criteria:**
- `npx nx test trei-feature-training-session` PASS
- `npx nx lint trei-feature-training-session` PASS
- `localeChange` não emite se a aba já está activa (click na mesma aba = no-op)

**Dependencies:**
- TREI-FE-009 (tipo `Locale`)
- TREI-FE-013 (smart component usa este componente)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Screenshot das abas com "PT" activo e "EN" inactivo

**Risks / Notes:**
- Nenhum — componente simples e sem lógica de negócio.
