# CONV-FE-013 -- SpeechTreePageComponent (smart, signals — orchestrates tree + assist + playback, Tela 06)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Outcome
`SpeechTreePageComponent` (smart, standalone, OnPush) em `libs/conv/feature-speech-tree` integra `SpeechTreeFacade`, `SemanticAssistFacade` e `TtsPlaybackFacade` via signals, fornecendo a Tela 06 completa (navegação da árvore + assist + voz) sem nenhuma lógica de canal (gesture vs touch) no componente.

## Approach (execution path)
1. Promover `alarme/calibracao/entrada` ou componente equivalente do protótipo `design-reference/` como ponto de partida visual.
2. Criar `SpeechTreePageComponent`: injetar `SpeechTreeFacade`, `SemanticAssistFacade`, `TtsPlaybackFacade`; ler signals `vm`, `selectedNodeId`, `result`, playback `vm`.
3. Conectar `SpeechTreeLevelComponent.nodeSelected` → `SpeechTreeFacade.selectNode(id, 'touch')`.
4. Conectar `SpeechTreeLevelComponent.nodeActivated` → `SpeechTreeFacade.activateNode(id)`.
5. Conectar `SemanticAssistPanelComponent.matchChosen(noId)` → `SpeechTreeFacade.selectNode(noId)` → `activateNode`.
6. Conectar `TtsPlayerComponent.playbackReplayRequested(nodeId)` → `TtsPlaybackFacade.replay(nodeId)`.
7. Criar `libs/conv/feature-speech-tree/src/lib/speech-tree.routes.ts` com `{ path: 'arvore', component: SpeechTreePageComponent }` e `{ path: 'arvore/:categoriaId', component: SpeechTreePageComponent }`.

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/feature-speech-tree/src/lib/speech-tree-page.component.ts`
- [ ] implementar `libs/conv/feature-speech-tree/src/lib/speech-tree-page.component.html`
- [ ] implementar `libs/conv/feature-speech-tree/src/lib/speech-tree.routes.ts`
- [ ] teste: `nodeActivated` de tipo `categoria` não chama `NodeSelectionPort.select`
- [ ] teste: `matchChosen` do assist chama `SpeechTreeFacade.selectNode` com `noId`

## Acceptance criteria
- [ ] `SpeechTreePageComponent` não contém lógica de detecção de canal (gesture vs touch)
- [ ] Sinais `vm.status === 'loading'` renderizam loading state (não stale content)
- [ ] `categoriaId` do route param é passado como `parentId` para `SpeechTreeFacade.loadLevel`
- [ ] `npx nx test conv-feature-speech-tree` PASS
- [ ] `npx nx lint conv-feature-speech-tree` PASS

## Dependencies
- CONV-FE-006 (SpeechTreeFacade)
- CONV-FE-007 (SemanticAssistFacade)
- CONV-FE-010 (TtsPlaybackFacade)
- CONV-FE-014 (SpeechTreeLevelComponent)
- CONV-FE-015 (InputChannelIndicatorComponent, SemanticAssistPanelComponent)
- CONV-FE-016 (TtsPlayerComponent)

## Empresa-context impact
Nenhum.

## Prioridade
P3
