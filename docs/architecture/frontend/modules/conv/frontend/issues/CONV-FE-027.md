# CONV-FE-027 -- G-1/G-3 EARS BDD gap closure: 5 thematic scenario groups + locale-switch cache invalidation scenario

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Outcome
41 cláusulas EARS sem cenário Gherkin formal (gap G-1) são agrupadas em 5 feature files temáticos e convertidas em cenários BDD verificáveis; e o novo EARS de invalidação de cache por troca de locale (gap G-3, SPEC-04 §5) ganha um cenário dedicado no grupo `data-access`. Nenhuma issue individual por cláusula EARS — agrupamento temático.

## Approach (execution path)
1. Criar `docs/architecture/frontend/modules/conv/frontend/e2e/routing-ears.feature`:
   - Cenários derivados de SPEC-01 EARS (redirect `/paciente` → `/paciente/arvore`, guard behaviors, wildcard redirect).
2. Criar `docs/architecture/frontend/modules/conv/frontend/e2e/vm-states-ears.feature`:
   - Cenários derivados de SPEC-03 EARS (loading/empty/pending-locale VM states, selectedNodeId single writer, activeChannel metadata only).
3. Criar `docs/architecture/frontend/modules/conv/frontend/e2e/data-access-ears.feature`:
   - Cenários derivados de SPEC-04 EARS (categoria vs frase activation, playback mode routing, binding resolve, blob no-persist).
   - **Adicionar cenário G-3:** "WHEN locale switches, THE SYSTEM SHALL invalidate the in-memory tree-level cache" → verificar que a cache é limpa e o nível é re-fetchado.
4. Criar `docs/architecture/frontend/modules/conv/frontend/e2e/shell-integration-ears.feature`:
   - Cenários derivados de SPEC-06 EARS (CONV not in caregiver shell, session verify on mount, gesture bridge, destroy lifecycle).
5. Criar `docs/architecture/frontend/modules/conv/frontend/e2e/error-handling-ears.feature`:
   - Cenários derivados de SPEC-07 EARS (pending-locale inline, session terminal, node-not-found toast, unsupported-locale banner, retry semantics).
6. Para cenários que dependem de features não buildáveis ainda: usar tag `@fixme` com mensagem explícita (não `describe` vazio).

## Implementation checklist (by layer/file)
- [ ] implementar `docs/architecture/frontend/modules/conv/frontend/e2e/routing-ears.feature`
- [ ] implementar `docs/architecture/frontend/modules/conv/frontend/e2e/vm-states-ears.feature`
- [ ] implementar `docs/architecture/frontend/modules/conv/frontend/e2e/data-access-ears.feature`
- [ ] implementar `docs/architecture/frontend/modules/conv/frontend/e2e/shell-integration-ears.feature`
- [ ] implementar `docs/architecture/frontend/modules/conv/frontend/e2e/error-handling-ears.feature`
- [ ] G-3: cenário de locale-switch cache invalidation incluído em `data-access-ears.feature`

## Acceptance criteria
- [ ] Todos os 41 EARS de SPEC-01/03/04/05/06/07 são rastreáveis para pelo menos um cenário Gherkin
- [ ] G-3 cenário de cache invalidation por locale-switch presente e executável
- [ ] Nenhum feature file com `describe` ou `Scenario Outline` vazio — `@fixme` com mensagem se não buildável
- [ ] Feature files são Gherkin válido (parseable pelo Playwright Cucumber plugin)

## Dependencies
- SPEC-01 a SPEC-07 (fonte das cláusulas EARS)
- CONV-FE-006 (SpeechTreeFacade.cache — verificável no cenário G-3)
- CONV-FE-028 (G-4 — stubs em conv-e2e usam `test.fixme()`)

## Empresa-context impact
Nenhum.

## Prioridade
P9
