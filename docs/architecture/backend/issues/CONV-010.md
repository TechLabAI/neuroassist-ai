# CONV-010 -- Criar AudioRefPortAdapter (filesystem, CONV_AUDIO_ROOT, convenção de path por empresa/paciente/locale/noId)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

Implementa `AudioRefPort` como um adapter de filesystem local. O áudio pré-sintetizado é
armazenado em `${CONV_AUDIO_ROOT}/<empresa>/<paciente_id>/<locale>/<no_id>.<ext>` (ADR-004/ADR-017;
resolves gap G6 de `integration-contracts.md` §2). CONV só lê — nunca escreve ou deleta. A
presença do arquivo é o sinal de disponibilidade; arquivo ausente → `Optional.empty()` → frontend
usa Web Speech. O adapter não faz download nem streaming — apenas verifica existência e retorna a URI
relativa (que o frontend usa para `GET /api/v1/conv/audio/<path>` ou equivalente via GWY).

## Spec de referencia

- SPEC-12 (degradation-resilience — OPS-004 audio fallback)
- SPEC-10 (integrations — AudioRefPort)
- `contracts/integration-contracts.md` §2

## Escopo

- [ ] Criar `infrastructure/audio/AudioRefPortAdapter.java` implementando `AudioRefPort`
- [ ] Resolução do path: `Paths.get(audioRoot, empresa, pacienteId.toString(), locale, noId.toString() + "." + ext)` onde `ext` é lido do campo `audio_formato` em `conv_no_conteudo`
- [ ] `resolveAudioUri(UUID noId, String locale)`: busca `conv_no_conteudo` pelo `(noId, locale, status='ativo')`; se `audio_ref` presente e arquivo existe → retorna `Optional.of(audioRef)`; se ausente ou arquivo não encontrado → `Optional.empty()`
- [ ] Configuração: `@Value("${neuroassist.conv.audio-root:#{null}}")` → se null, sempre retorna `Optional.empty()` (degradação graceful em ambiente dev)
- [ ] Teste unitário com arquivo mock no filesystem temporário: presença → URI retornada; ausência → `Optional.empty()`

## Resultado esperado

`AudioRefPort` funcional com degradação graceful; CONV nunca falha por áudio ausente.

## Criterios de aceite

- [ ] `./gradlew :conv-service:test` BUILD SUCCESS
- [ ] Teste unitário: arquivo presente no path correto → `Optional.of(uri)` com path relativo ao `CONV_AUDIO_ROOT`
- [ ] Teste unitário: arquivo ausente → `Optional.empty()` (sem exceção)
- [ ] Teste unitário: `CONV_AUDIO_ROOT` não configurado (null) → `Optional.empty()` (sem NPE)
- [ ] Adapter nunca usa `File.delete()` ou operações de escrita sobre o store de áudio

## Dependencias

- CONV-004 (AudioRefPort declarado)

## Estimativa

S

## Prioridade

P3
