# CONV-004 -- Declarar ports do domínio (SearchPort, AudioRefPort, AcsSessionPort, VocabularyPort, HistAuditPort)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto

Declara as interfaces de port do `conv-service` no pacote `domain/port/`. Os ports são o
contrato entre a camada de aplicação e a infra externa; as implementações (adapters) vêm em P3/P4.
`SearchPort` já existe na `elastic-stack-client-lib` do monorepo — CONV importa e usa diretamente
(não recria). Os demais são declarados localmente como interfaces Java.

## Spec de referencia

- SPEC-01 (api-surface — ports necessários)
- SPEC-10 (integrations — AudioRefPort, VocabularyPort)
- `contracts/hub-integration.md` (AcsSessionPort, VocabularyPort, HistAuditPort)
- `contracts/integration-contracts.md` §1-2 (SearchPort via lib, AudioRefPort filesystem)

## Escopo

- [ ] Importar `SearchPort` da `elastic-stack-client-lib` como dependência do `build.gradle.kts` (não criar novo port de busca)
- [ ] Criar `domain/port/AudioRefPort.java` — `Optional<String> resolveAudioUri(UUID noId, String locale)` (retorna URI relativa ao CONV_AUDIO_ROOT ou empty se ausente)
- [ ] Criar `domain/port/AcsSessionPort.java` — `void validateAndCheckBlacklist(String bearerToken)` throws `SessionRevokedException`, `UnauthorizedException` (blacklist consultada em toda chamada)
- [ ] Criar `domain/port/VocabularyPort.java` — `Optional<UUID> resolveEngineFeatureRef(String engineFeatureRef, UUID pacienteId)` (retorna noId ou empty se não resolvido; engineFeatureRef opaco, gap G4)
- [ ] Criar `domain/port/HistAuditPort.java` — `void send(AuditFact fact)` throws `HistUnavailableException` (chamador captura e escreve no outbox)
- [ ] Criar exceptions de domínio: `SessionRevokedException`, `UnauthorizedException`, `NodeNotFoundException`, `NodeNotSelectableException`, `ContentPendingLocaleException`, `HistUnavailableException` em `domain/exception/`

## Resultado esperado

Ports e exceptions compiláveis no pacote `domain/`; nenhum port importa classes Spring ou infra.

## Criterios de aceite

- [ ] `./gradlew :conv-service:compileJava` BUILD SUCCESS
- [ ] Nenhum port em `domain/port/` importa `org.springframework.*` ou `co.elastic.*` (ArchUnit RULE — camada domain não depende de infra)
- [ ] `SearchPort` não é redeclarado localmente — importado via `elastic-stack-client-lib`
- [ ] `VocabularyPort.resolveEngineFeatureRef` retorna `Optional` (nunca lança exceção para ref desconhecido — soft dependency)
- [ ] `HistAuditPort.send` declara `HistUnavailableException` checked (força o chamador a tratar a falha)

## Dependencias

- CONV-001 (precede)

## Estimativa

S

## Prioridade

P1
