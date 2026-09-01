# CONV-FE-021 -- Speech tree loading / empty / error / forbidden states (SPEC-07 degradation-first, large 'tentar de novo' affordance)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Outcome
`libs/conv/feature-speech-tree` tem estados first-class para loading, empty, error e forbidden no `SpeechTreeLevelComponent`, visualmente distintos e acessíveis — o estado error tem um affordance "tentar de novo" grande o suficiente para controle motor limitado; o estado forbidden nunca expõe rota de cuidador.

## Approach (execution path)
1. Criar `SpeechTreeLevelEmptyComponent` (presentacional): mensagem para cuidador configurar comandos (`conv.state.empty.hint`) — sem link de cuidador.
2. Criar `SpeechTreeLevelErrorComponent` (presentacional):
   - Output `retryRequested: EventEmitter<void>`.
   - Botão "Tentar de novo" com `min-height: 48px` e `min-width: 120px` (controle motor limitado).
   - Texto: `conv.state.error.retry` (PT/EN).
3. Adicionar estado `forbidden` em `SpeechTreePageComponent`: renderizado quando `convNavegarGuard` redireciona com `?forbidden=true` — mensagem `conv.state.forbidden` sem nenhum link externo ao patient shell.
4. Integrar estados no `SpeechTreePageComponent` via `@if (vm.status === 'loading')` / `empty` / `error` / `ready`.

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/feature-speech-tree/src/lib/speech-tree-level-empty.component.ts`
- [ ] implementar `libs/conv/feature-speech-tree/src/lib/speech-tree-level-error.component.ts`
- [ ] implementar `libs/conv/ui/src/lib/loading-spinner.component.ts`
- [ ] teste: estado `error` renderiza botão de retry com dimensões mínimas de controle motor
- [ ] a11y: contraste AA em todos os estados; `aria-live="polite"` no container de estado

## Acceptance criteria
- [ ] Botão "tentar de novo" tem área clicável >= 48x48px
- [ ] Estado `forbidden` não contém nenhum link para rota de cuidador
- [ ] Estado `loading` não renderiza conteúdo stale
- [ ] `npx nx test conv-feature-speech-tree` PASS
- [ ] `npx nx lint conv-feature-speech-tree` PASS

## Dependencies
- CONV-FE-013 (SpeechTreePageComponent — renderiza os estados)
- CONV-FE-019 (convNavegarGuard — dispara forbidden state)
- CONV-FE-023 (i18n keys PT/EN para as mensagens de estado)

## Empresa-context impact
Nenhum.

## Prioridade
P7
