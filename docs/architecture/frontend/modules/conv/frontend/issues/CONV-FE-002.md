# CONV-FE-002 -- CONV_BOOTSTRAP init/destroy lifecycle (seed locale, camera start/release, audio blob revoke, session verification)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Outcome
`CONV_BOOTSTRAP` (`libs/conv/shell-entry`) implementa `init` e `destroy` com as seguintes garantias: (a) sessão verificada antes de qualquer render; (b) câmera liberada em todo cenário de saída; (c) blobs de áudio revogados ao final da sessão; (d) locale semeado a partir do JWT `locale` ∩ `localesSuportados`.

## Approach (execution path)
1. Implementar `CONV_BOOTSTRAP.init(ctx)`:
   - Verificar `SessaoPaciente` ativa via `@neuroassist/shared/access`; se ausente, abortar o mount (não redirecionar para login de cuidador).
   - Chamar `ConvLocaleFacade.seedFromJwt(jwt.locale, localesSuportados)` usando resposta `GET /tree?rootOnly=true`.
   - Se `CONV_FEATURE_FLAGS.gestureChannelEnabled`, chamar `INPUT_CHANNEL_PORT.start()` (câmera + Wake Lock via GEST).
   - Pré-carregar o nível raiz da árvore para primeiro paint.
2. Implementar `CONV_BOOTSTRAP.destroy()`:
   - `INPUT_CHANNEL_PORT.stop()` — liberar todos os tracks de câmera ativa.
   - `TtsPlaybackFacade.stop()` — parar áudio / Web Speech.
   - Revogar todos os blob URLs de áudio cacheado da sessão.
   - Limpar o cache de níveis da árvore.
3. Registrar `destroy` tanto no teardown do patient shell quanto no `routerLink` leave de `/paciente/arvore` (zero camera leaks).

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/shell-entry/src/lib/conv.bootstrap.ts`
- [ ] teste unitário: `destroy()` chama `INPUT_CHANNEL_PORT.stop()` mesmo quando `init()` foi abortado
- [ ] teste unitário: `init()` aborta (não monta) quando `SessaoPaciente` está ausente

## Acceptance criteria
- [ ] `CONV_BOOTSTRAP.destroy()` libera todos os `MediaStream` tracks (zero camera leaks)
- [ ] `CONV_BOOTSTRAP.init()` aborta sem redirecionar para rota de cuidador quando sessão é inválida
- [ ] blob URLs de áudio são revogados ao final da sessão (não persistem em localStorage/IndexedDB — SEC-002)
- [ ] `npx nx test conv-shell-entry` PASS
- [ ] `npx nx lint conv-shell-entry` PASS

## Dependencies
- CONV-FE-001 (CONV_FEATURE_FLAGS, CONV_ROUTES)
- CONV-FE-005 (INPUT_CHANNEL_PORT)
- CONV-FE-006 (SpeechTreeFacade — pré-load do nível raiz)
- CONV-FE-010 (TtsPlaybackFacade.stop)
- INT-FE-CONV-001 (shared/access — verificação de SessaoPaciente)

## Empresa-context impact
Nenhum.

## Prioridade
P1
