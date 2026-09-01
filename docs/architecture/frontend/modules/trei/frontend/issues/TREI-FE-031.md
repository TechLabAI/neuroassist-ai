## [TREI-FE-031] e2e Playwright: independencia-de-locale.feature (PT/EN locale isolation)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/trei-e2e/src/independencia-de-locale.spec.ts` verifica que os proposals e flags de aprovação são independentes por locale: aprovar um nó para PT não altera o estado de EN, e trocar o locale re-projecta correctamente a lista sem alterar flags do outro locale.

**Context / Problem statement:**
- ADR-018: a autoria bilíngue PT/EN é independente — cada locale tem o seu `confirmadoCuidadora` e `confirmadoPaciente` separados.
- O `ProposalReviewFacade` projecta proposals pelo `activeLocale` activo; trocar locale re-projecta sem mudar os flags do outro locale (SPEC-03 §3 INV-3, SPEC-04 §4).
- Este feature cobre: trocar de PT para EN → proposal list muda; aprovação em PT → EN mantém `caregiverApproved = false`.

**Approach (execution path):**
1. Criar `apps/trei-e2e/src/independencia-de-locale.spec.ts`.
2. Setup: mock com proposta materializada em ambos os locales (PT e EN), com `confirmadoCuidadora: false` em ambos.
3. Cenário de isolamento: aprovar o nó para PT (`POST .../aprovacoes/cuidadora { locale: 'pt' }`); trocar para EN → `DualConfirmationIndicator` de EN ainda mostra ⏳ cuidadora.
4. Cenário de troca de locale: `LocaleTabsComponent` mostra "EN" activo → proposal list exibe o texto EN do nó.

**Non-goals:**
- Não testar o fluxo de publicação bilingue (TREI não actua sobre publicação).

**Layer(s):**
frontend / apps/trei-e2e

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/e2e/independencia-de-locale.feature`
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-03-view-models.md` (§3 INV-3)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-04-data-access.md` (§4)

**Architecture alignment:**
- Playwright + page.route()
- Tag Nx: `scope:trei`, `type:e2e`

**Implementation checklist (by file)**

**apps/trei-e2e**
- [ ] `apps/trei-e2e/src/independencia-de-locale.spec.ts`
- [ ] Cenário: aprovação PT não afecta EN (locale isolation)
- [ ] Cenário: troca de locale re-projecta a lista

**Tests**
- [ ] `nx e2e trei-e2e --grep "independencia-de-locale"` PASS

**Milestone:**
Wave 9 / e2e + Hardening

**Labels:**
type:e2e
layer:frontend
scope:trei
priority:P9

**Acceptance criteria:**
- `npx nx e2e trei-e2e` PASS
- Após aprovação em PT: `DualConfirmationIndicator` EN mostra ⏳ (não ✅)
- Após troca para EN: `LocaleTabsComponent` mostra "EN" activo e proposals em EN

**Dependencies:**
- TREI-FE-017 (LocaleTabsComponent)
- TREI-FE-019 (DualConfirmationIndicatorComponent)
- TREI-FE-011 (ProposalReviewFacade — isolamento de locale)

**Data impact:**
- Nenhum (mocks)

**API / Contract impact:**
- Mock `GET /nos/{id}` retorna `conteudo: { pt: {...}, en: {...} }` com flags independentes

**Testing / Evidence required:**
- Screenshot: locale PT aprovado, indicador EN ainda a laranja/pendente

**Risks / Notes:**
- Confirmar o formato do `DraftNodeResponseDto.conteudo` para PT e EN — se for um objecto indexado por locale, o mock deve replicar exactamente essa estrutura.
