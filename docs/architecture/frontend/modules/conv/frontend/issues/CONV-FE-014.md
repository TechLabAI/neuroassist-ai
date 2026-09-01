# CONV-FE-014 -- SpeechTreeLevelComponent + SpeechNodeButtonComponent (presentational, channel-agnostic, pending-locale disabled)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Outcome
`SpeechTreeLevelComponent` e `SpeechNodeButtonComponent` são componentes puramente apresentacionais que rendem a lista de nós do nível ativo: nós com `contentStatus === 'pending_locale'` aparecem desabilitados/esmaecidos (sem substituição do outro locale); nenhum componente sabe se o evento de seleção veio de gesto ou toque.

## Approach (execution path)
1. Criar `SpeechTreeLevelComponent` (presentacional, standalone, OnPush): input `nodes: SpeechNodeVM[]`, `selectedNodeId: string | null`, `locale: Locale`; outputs `nodeSelected: EventEmitter<string>` e `nodeActivated: EventEmitter<string>`.
2. Criar `SpeechNodeButtonComponent` (presentacional, standalone, OnPush): input `node: SpeechNodeVM`, `selected: boolean`; outputs `selected` e `activated`.
   - Quando `node.pendingInLocale === true`: renderizar desabilitado/esmaecido com texto da i18n key `conv.err.pendingLocale` inline — nunca substituir com o outro locale.
   - Quando `node.tipo === 'categoria'`: ativar `nodeActivated` (descida).
   - Quando `node.tipo === 'frase' && node.selecionavel`: ativar `nodeSelected` (seleção → speak).
3. Garantir estados first-class: `empty` (nenhum nó), `loading` (CONV-FE-021 trata), `error` (CONV-FE-021 trata).
4. Adicionar `aria-disabled`, `aria-pressed` (selected) e contraste AA para os estados.

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/feature-speech-tree/src/lib/speech-tree-level.component.ts`
- [ ] implementar `libs/conv/feature-speech-tree/src/lib/speech-tree-level.component.html`
- [ ] implementar `libs/conv/feature-speech-tree/src/lib/speech-node-button.component.ts`
- [ ] teste: nó com `pendingInLocale: true` renderiza desabilitado e não emite `nodeSelected`/`nodeActivated`
- [ ] teste: nó `categoria` emite `nodeActivated`; nó `frase` emite `nodeSelected`
- [ ] a11y: ARIA roles e contraste AA verificados

## Acceptance criteria
- [ ] Nó `pending_locale` renderiza texto da key `conv.err.pendingLocale` — nunca o texto do outro locale
- [ ] Ambos os outputs (`nodeSelected`, `nodeActivated`) são channel-agnostic — o componente não sabe a origem
- [ ] `aria-disabled="true"` em nós desabilitados; `aria-pressed` no nó selecionado
- [ ] `npx nx test conv-feature-speech-tree` PASS
- [ ] `npx nx lint conv-feature-speech-tree` PASS

## Dependencies
- CONV-FE-013 (SpeechTreePageComponent — consumidor dos outputs)
- CONV-FE-021 (estados de loading/empty/error encapsulados em componentes separados)

## Empresa-context impact
Nenhum.

## Prioridade
P3
