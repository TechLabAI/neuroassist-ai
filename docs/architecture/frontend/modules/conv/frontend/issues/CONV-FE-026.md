# CONV-FE-026 -- Playwright e2e: fallback-voz.feature + assist-e-degradacao.feature (degradation chains, silent fallbacks, OPS-003/004)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Outcome
`apps/conv-e2e` contém specs que cobrem as cadeias de degradação críticas: (a) fallback de voz (áudio ausente → Web Speech → texto proeminente); (b) assist e degradação (ES indisponível → navegação manual silenciosa, sem erro).

## Approach (execution path)
1. Criar `apps/conv-e2e/src/fallback-voz.spec.ts` transpilando `fallback-voz.feature` (3 cenários):
   - `playbackMode: 'web-speech'` → Web Speech ativado (badge visível).
   - Web Speech indisponível → texto full-bleed renderizado (não silêncio).
   - Nó `pending_locale` → desabilitado/esmaecido + nota inline + nunca fala o outro locale.
2. Criar `apps/conv-e2e/src/assist-e-degradacao.spec.ts` transpilando `assist-e-degradacao.feature` (2 cenários):
   - Match do assist escolhido → seleção e fala.
   - ES indisponível (`degraded: true`) → navegação manual disponível, sem toast/banner de erro.
3. Usar `SemanticSearchMockAdapter` configurado com `degraded: true` para o cenário de ES down.
4. Stub de `window.speechSynthesis = undefined` para o cenário de text-only.

## Implementation checklist (by layer/file)
- [ ] implementar `apps/conv-e2e/src/fallback-voz.spec.ts`
- [ ] implementar `apps/conv-e2e/src/assist-e-degradacao.spec.ts`
- [ ] Configurar mock de degradação do Elasticsearch no ambiente de e2e
- [ ] `npx nx e2e conv-e2e` PASS (ou `test.fixme()` com mensagem explícita)

## Acceptance criteria
- [ ] `fallback-voz.spec.ts` cobre os 3 cenários de `fallback-voz.feature`
- [ ] `assist-e-degradacao.spec.ts` verifica que `degraded: true` não renderiza nenhum toast/banner
- [ ] Cenário text-only: `TextOnlyDisplayComponent` visível no DOM
- [ ] Nunca `describe` vazio — `test.fixme()` com mensagem se ainda não buildável

## Dependencies
- CONV-FE-022 (TTS fallback chain)
- CONV-FE-007 (SemanticAssistFacade — modo degradado)
- CONV-FE-016 (TtsPlayerComponent — badge web-speech + text-only)

## Empresa-context impact
Nenhum.

## Prioridade
P9
