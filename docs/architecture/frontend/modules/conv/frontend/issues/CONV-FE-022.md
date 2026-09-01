# CONV-FE-022 -- TTS fallback chain (cached audio → Web Speech → prominent full-bleed text, voice-never-silent, OPS-004/ADR-007)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Outcome
`libs/conv/feature-tts` implementa o adapter `WebSpeechAdapter` e o componente `TextOnlyDisplayComponent` que completam a cadeia ADR-007: quando o áudio cacheado falha, Web Speech é acionado; quando Web Speech não está disponível no dispositivo, o texto do nó é exibido em full-bleed proeminente — a seleção nunca resulta em silêncio.

## Approach (execution path)
1. Criar `WebSpeechAdapter`:
   - Detectar `window.speechSynthesis` disponível.
   - `speak(text: string, locale: Locale)`: criar `SpeechSynthesisUtterance` com `lang` mapeado do locale (`pt` → `pt-BR`, `en` → `en-US`).
   - Se `speechSynthesis` indisponível: emitir sinal `unavailable` para `TtsPlaybackFacade` transitar para `state: 'unavailable'`.
2. Criar `TextOnlyDisplayComponent` (presentacional, standalone, OnPush):
   - Input `text: string | null`.
   - Renderiza o texto em full-bleed, fonte grande (acessível para controle visual limitado), contraste AA garantido.
   - Não tem interação — o paciente apenas lê/ouve o que a síntese não conseguiu entregar.
3. Integrar no `TtsPlayerComponent` (CONV-FE-016): `@if (vm.source === 'text-only')` → renderizar `TextOnlyDisplayComponent`.

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/feature-tts/src/lib/web-speech-adapter.ts`
- [ ] implementar `libs/conv/feature-tts/src/lib/text-only-display.component.ts`
- [ ] teste: `window.speechSynthesis` ausente → `TtsPlaybackFacade.vm.source === 'text-only'`
- [ ] a11y: contraste AA em `TextOnlyDisplayComponent`; font-size >= 1.25rem

## Acceptance criteria
- [ ] Seleção de frase com áudio ausente e Web Speech indisponível → texto proeminente renderizado
- [ ] `TtsPlaybackFacade.vm.state` nunca permanece em `'loading'` indefinidamente
- [ ] `npx nx test conv-feature-tts` PASS (cobertura do caminho text-only)
- [ ] `npx nx lint conv-feature-tts` PASS

## Dependencies
- CONV-FE-010 (TtsPlaybackFacade — gerencia a máquina de estados)
- CONV-FE-016 (TtsPlayerComponent — renderiza TextOnlyDisplayComponent)

## Empresa-context impact
Nenhum.

## Prioridade
P7
