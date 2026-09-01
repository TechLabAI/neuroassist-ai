# TREI-012 -- TtsSynthesisPort + ElevenLabsAdapter + AudioStorePort + FilesystemAudioStoreAdapter

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/7 itens

## Contexto

Implementa os dois ports de saída de áudio: `TtsSynthesisPort` (ElevenLabs API, voz
clonada por paciente) e `AudioStorePort` (filesystem local, `TREI_AUDIO_ROOT`). A síntese
é **idempotente**: antes de chamar ElevenLabs, o adapter verifica se o arquivo já existe
para `(no_id, locale)` — se sim, reutiliza (FIN-002, custo). `TREI_AUDIO_ROOT` DEVE
apontar para o mesmo filesystem que `CONV_AUDIO_ROOT` (CONV serve o áudio que TREI grava).
Texto clínico/diagnóstico nunca enviado à ElevenLabs (SEC-002).

## Spec de referencia

- SPEC-12 (publication-pipeline-outbox — step tts: idempotente, retryable)
- integration-contracts.md §3 (ElevenLabs: uma vez por (no_id, locale), layout de path)
- integration-contracts.md §5 (AudioStorePort: layout, TREI_AUDIO_ROOT==CONV_AUDIO_ROOT)

## Escopo

- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/tts/ElevenLabsAdapter.java` — implementa `TtsSynthesisPort`; lê `ELEVENLABS_API_KEY` + `ELEVENLABS_VOICE_ID`; formata request ElevenLabs Text-to-Speech API; retorna `AudioSynthesisResult{byte[] audio, AudioFormato formato}`
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/tts/ElevenLabsConfig.java` — lê env vars `ELEVENLABS_API_KEY`, `ELEVENLABS_VOICE_ID`
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/audiostore/FilesystemAudioStoreAdapter.java` — implementa `AudioStorePort`; escreve arquivo em `${TREI_AUDIO_ROOT}/<empresa>/<paciente_id>/<locale>/<no_id>.<ext>`; retorna `audioRef` (path relativo ao root); verifica existência antes de sintetizar (idempotência)
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/audiostore/AudioStoreConfig.java` — lê `TREI_AUDIO_ROOT` do env
- [ ] lógica idempotente no `FilesystemAudioStoreAdapter`: se arquivo `<no_id>.<ext>` já existe → retornar `audioRef` sem chamar `TtsSynthesisPort` (step tts do outbox drainer verifica isso)
- [ ] criar `src/test/java/com/neuroassist/trei/infrastructure/tts/ElevenLabsAdapterTest.java` — mock da HTTP call; verificar path canônico do arquivo; verificar idempotência (ElevenLabs NÃO é chamado se arquivo já existe)

## Resultado esperado

`tts` step: verifica arquivo, sintetiza se ausente, grava no path correto; resultado
retornado como `audioRef` relativo ao root para gravação em `conv_no_conteudo.audio_ref`.

## Criterios de aceite

- [ ] `./gradlew :trei-service:test --tests "*ElevenLabsAdapterTest*"` sai 0
- [ ] Teste verifica que se arquivo `<no_id>.opus` já existe no path correto, ElevenLabs NÃO é chamado
- [ ] Teste verifica que o `audioRef` retornado segue o layout `<empresa>/<paciente>/<locale>/<no_id>.<ext>`
- [ ] `ELEVENLABS_API_KEY` não é logado (SEC-002: segredo não aparece em logs)

## Dependencias

- TREI-003 (TtsSynthesisPort + AudioStorePort interfaces)

## Estimativa

M

## Prioridade

P4
