# CONV-FE-005 -- INPUT_CHANNEL_PORT token + ConvShellIntegrationService (gesture bridge + locale/playback orchestration, ADR-005)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Outcome
`libs/conv/integration` expõe o `INPUT_CHANNEL_PORT` (InjectionToken + interface) e `ConvShellIntegrationService`, que coordena: (a) bridge gesto→`SpeechTreeFacade` sem importar `scope:gest`; (b) sequência ativação→playback; (c) coordenação de locale (invalidar cache + parar playback na troca).

## Approach (execution path)
1. Criar `input-channel.port.ts` com `INPUT_CHANNEL_PORT = new InjectionToken<InputChannelPort>('conv.input-channel')` e a interface com `recognizedGesture: Signal<{ engineFeatureRef: string } | null>`, `start(): Promise<void>`, `stop(): void`, `available: Signal<boolean>`.
2. Criar `ConvShellIntegrationService` (`@Injectable({ providedIn: 'root' })`):
   - Injetar `INPUT_CHANNEL_PORT` via `inject(INPUT_CHANNEL_PORT)`.
   - `effect()` sobre `inputChannel.recognizedGesture`: quando não-nulo, chamar `BindingPort.resolve(engineFeatureRef)` → se `bound && selecionavel`, chamar `SpeechTreeFacade.selectNode/activateNode(noId)`.
   - Orquestrar ativação de frase: `NodeSelectionPort.select(...)` → `TtsPlaybackFacade.speak(...)`.
   - Escutar evento `localeSwitched`: invalidar cache da árvore + `TtsPlaybackFacade.stop()` + recarregar nível atual.
3. Exportar pelo `src/index.ts`.

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/integration/src/lib/input-channel.port.ts`
- [ ] implementar `libs/conv/integration/src/lib/conv-shell-integration.service.ts`
- [ ] implementar `libs/conv/integration/src/index.ts`
- [ ] `nx lint conv-integration` PASS (Nx boundary: `scope:conv` — sem imports de `scope:gest` dentro da lib)
- [ ] teste unitário: gesture reconhecido com `bound=false` → `SpeechTreeFacade` não chamado

## Acceptance criteria
- [ ] `libs/conv/integration` não importa nenhum artefato de `libs/gest/*` (verificável via `nx lint`)
- [ ] Um `engineFeatureRef` com `bound: false` é tratado como no-op (nenhuma seleção disparada)
- [ ] Troca de locale para playback (stop + reload) testada em isolamento com mock
- [ ] `npx nx test conv-integration` PASS
- [ ] `npx nx lint conv-integration` PASS

## Dependencies
- CONV-FE-001 (CONV_FEATURE_FLAGS.gestureChannelEnabled)
- CONV-FE-006 (SpeechTreeFacade)
- CONV-FE-008 (NodeSelectionPort)
- CONV-FE-009 (BindingPort)
- CONV-FE-010 (TtsPlaybackFacade)
- INT-FE-CONV-003 (composição do adapter GEST em scope:web)

## Empresa-context impact
Nenhum.

## Prioridade
P1
