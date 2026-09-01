# CONV-FE-010 -- AudioPort + Http Adapter + TtsPlaybackFacade (blob URL cache, 3-state machine cached→web-speech→text, SEC-002 no persist)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Outcome
`TtsPlaybackFacade` expõe `Signal<PlaybackVM>` com a máquina de 3 estados (ADR-007): cached audio → Web Speech → texto proeminente; blobs de áudio mantidos em `Map<string, string>` (blob URL) exclusivamente em memória de sessão — nunca em localStorage/IndexedDB (SEC-002/LGPD).

## Approach (execution path)
1. Definir `AudioPort` com operação `resolve(audioRefUri: string): Observable<Blob>` (fetch via GWY; confirmar path base com BE no scaffold).
2. Implementar `AudioHttpAdapter`: fetch do blob da URI retornada por `/select`; criar `URL.createObjectURL(blob)` e armazenar em `Map<nodeId, blobUrl>`.
3. Implementar `TtsPlaybackFacade`:
   - Signal `vm: PlaybackVM` (`state`, `source`, `nodeId`, `locale`).
   - `speak(SelectResponseDto)`: `playbackMode === 'audio-cacheado'` → `AudioPort.resolve(audioRef.uri)` → play `<audio>`; `playbackMode === 'web-speech'` → `SpeechSynthesisUtterance` com o `texto`.
   - Fallback: áudio falha → Web Speech; Web Speech indisponível → `state: 'unavailable'`, `source: 'text-only'` (texto proeminente no `TtsPlayerComponent`).
   - `stop()`: cancelar audio/utterance; não revogar blobs (blob revocation é responsabilidade do `CONV_BOOTSTRAP.destroy()`).
4. Revogar todos os blob URLs em `CONV_BOOTSTRAP.destroy()` — nunca persistir (SEC-002).

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/data-access/src/lib/audio.port.ts`
- [ ] implementar `libs/conv/data-access/src/lib/audio.http-adapter.ts`
- [ ] implementar `libs/conv/data-access/src/lib/tts-playback.facade.ts`
- [ ] teste: fallback Web Speech ativado quando `playbackMode === 'web-speech'`
- [ ] teste: `state: 'unavailable'` quando Web Speech não está disponível no browser
- [ ] teste: nenhum blob URL armazenado em localStorage após play

## Acceptance criteria
- [ ] Blob URLs nunca chegam a localStorage, sessionStorage ou IndexedDB (SEC-002)
- [ ] Fallback text-only renderizado quando Web Speech ausente — voz nunca em silêncio
- [ ] `CONV_BOOTSTRAP.destroy()` revoga todos os blob URLs da sessão
- [ ] `npx nx test conv-data-access` PASS
- [ ] `npx nx lint conv-data-access` PASS

## Dependencies
- CONV-FE-002 (CONV_BOOTSTRAP.destroy — revogação dos blobs)
- CONV-FE-008 (NodeSelectionPort — SelectResponseDto contém `playbackMode` e `audioRef.uri`)
- CONV-FE-012 (SelectResponseDto, DATA_PROVIDERS)

## Empresa-context impact
Nenhum — blobs de áudio são isolados por sessão; não transitam entre pacientes.

## Prioridade
P2
