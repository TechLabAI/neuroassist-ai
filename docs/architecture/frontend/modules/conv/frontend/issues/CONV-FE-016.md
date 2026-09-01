# CONV-FE-016 -- TtsPlayerComponent + PlaybackStatusComponent + ReplayButtonComponent (feature-tts, ADR-007 fallback chain)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Outcome
`libs/conv/feature-tts` fornece `TtsPlayerComponent` (smart, standalone, OnPush) que implementa visualmente a cadeia de fallback ADR-007: indicador de playing (cached audio), badge "web-speech" (fallback), e texto full-bleed proeminente (text-only unavailable) — voz nunca em silêncio.

## Approach (execution path)
1. Criar `TtsPlayerComponent` (smart): injetar `TtsPlaybackFacade`; ler signal `vm: PlaybackVM`.
   - `state === 'speaking'` + `source === 'cached-audio'`: mostrar `PlaybackStatusComponent` com indicador de audio playing.
   - `state === 'fallback'` + `source === 'web-speech'`: mostrar badge "web-speech" em `PlaybackStatusComponent`.
   - `state === 'unavailable'` + `source === 'text-only'`: renderizar `<div>` full-bleed com `vm` texto do nó.
2. Criar `PlaybackStatusComponent` (presentacional, standalone, OnPush): inputs `state: PlaybackState`, `source: PlaybackSource`; renderiza variante visual por combinação.
3. Criar `ReplayButtonComponent` (presentacional, standalone, OnPush): output `playbackReplayRequested: EventEmitter<string>` (nodeId); oculto durante `state === 'loading'`.
4. Exportar pelo `src/index.ts`.

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/feature-tts/src/lib/tts-player.component.ts`
- [ ] implementar `libs/conv/feature-tts/src/lib/tts-player.component.html`
- [ ] implementar `libs/conv/feature-tts/src/lib/playback-status.component.ts`
- [ ] implementar `libs/conv/feature-tts/src/lib/replay-button.component.ts`
- [ ] implementar `libs/conv/feature-tts/src/index.ts`

## Acceptance criteria
- [ ] `state: 'unavailable'` renderiza texto do nó em full-bleed — nenhum silêncio
- [ ] Badge "web-speech" visível quando `source === 'web-speech'`
- [ ] `ReplayButtonComponent` oculto em `state === 'loading'`
- [ ] `npx nx test conv-feature-tts` PASS
- [ ] `npx nx lint conv-feature-tts` PASS

## Dependencies
- CONV-FE-010 (TtsPlaybackFacade → PlaybackVM)
- CONV-FE-022 (TTS fallback chain — implementação do Web Speech adapter e text-only)

## Empresa-context impact
Nenhum.

## Prioridade
P3
