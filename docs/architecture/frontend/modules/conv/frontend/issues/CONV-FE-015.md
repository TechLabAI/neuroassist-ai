# CONV-FE-015 -- InputChannelIndicatorComponent + BackButtonComponent + SemanticAssistPanelComponent (presentational, feature-speech-tree)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Outcome
Três componentes presentacionais de `libs/conv/feature-speech-tree` que completam a Tela 06: `InputChannelIndicatorComponent` (mostra canal ativo: gesto/toque/off), `BackButtonComponent` (navegação para o nível pai), `SemanticAssistPanelComponent` (exibe matches do assist e permite escolher um).

## Approach (execution path)
1. Criar `InputChannelIndicatorComponent` (presentacional, standalone, OnPush):
   - Inputs: `activeChannel: 'gesture' | 'touch' | null`, `gestureAvailable: boolean`.
   - Renderiza indicador discreto; quando `gestureAvailable: false` (câmera negada ou flag off), exibe "gesto off" honestamente (sem esconder o estado).
2. Criar `BackButtonComponent` (presentacional, standalone, OnPush):
   - Output: `backRequested: EventEmitter<void>`.
   - Desabilitado/oculto quando `SpeechTreeVM.path.length === 0` (nível raiz, SPEC-01 §2).
3. Criar `SemanticAssistPanelComponent` (presentacional, standalone, OnPush):
   - Inputs: `result: SemanticAssistResultVM`, `enabled: boolean`.
   - Outputs: `assistTriggered: EventEmitter<void>`, `matchChosen: EventEmitter<string>`.
   - Quando `enabled: false` (flag off ou degradado), ocultar o painel — nenhum toast.
4. Exportar todos pelo `src/index.ts`.

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/feature-speech-tree/src/lib/input-channel-indicator.component.ts`
- [ ] implementar `libs/conv/feature-speech-tree/src/lib/back-button.component.ts`
- [ ] implementar `libs/conv/feature-speech-tree/src/lib/semantic-assist-panel.component.ts`
- [ ] implementar `libs/conv/feature-speech-tree/src/index.ts`
- [ ] teste: `InputChannelIndicatorComponent` com `gestureAvailable: false` não oculta o indicador — mostra "gesto off"

## Acceptance criteria
- [ ] `BackButtonComponent` não renderiza no nível raiz (`path.length === 0`)
- [ ] `SemanticAssistPanelComponent` oculta o painel quando `enabled: false` sem toast
- [ ] `InputChannelIndicatorComponent` mostra estado honesto (gesture off quando câmera indisponível)
- [ ] `npx nx test conv-feature-speech-tree` PASS
- [ ] `npx nx lint conv-feature-speech-tree` PASS

## Dependencies
- CONV-FE-013 (SpeechTreePageComponent — consumidor)
- CONV-FE-007 (SemanticAssistResultVM via SemanticAssistFacade)

## Empresa-context impact
Nenhum.

## Prioridade
P3
