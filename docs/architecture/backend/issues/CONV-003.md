# CONV-003 -- Criar DTOs de API (request e response para os 4 endpoints públicos)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/7 itens

## Contexto

Define os DTOs de entrada e saída dos 4 endpoints públicos do `conv-service` conforme o
`api-contract.md`. DTOs vivem no pacote `api/dto/` (camada pública, não expõe entidades JPA).
Inclui os records/classes de request, response e o modelo de erro RFC 7807 `ProblemDetail`
(Spring 6 nativo). Os DTOs são imutáveis (Java records ou `@JsonDeserialize` com `@JsonProperty`).

## Spec de referencia

- SPEC-01 (api-surface — shapes JSON de todos os endpoints)
- `contracts/api-contract.md` §2 (corpos de request e response por endpoint)

## Escopo

- [ ] Criar `api/dto/tree/SpeechTreeResponse.java` (arvoreId, pacienteId, localeRequested, localeServed, localesSuportados, degraded, retrieval, nodes: List<NodeDto>)
- [ ] Criar `api/dto/tree/NodeDto.java` (id, parentId, tipo, ordem, engineFeatureRef, conteudo: NodeConteudoDto)
- [ ] Criar `api/dto/tree/NodeConteudoDto.java` (locale, texto, contentStatus, audioRef: AudioRefDto, selecionavel)
- [ ] Criar `api/dto/search/SearchRequest.java` (intent String, k Integer, locale String)
- [ ] Criar `api/dto/search/SearchResponse.java` (localeServed, degraded, retrieval, matches: List<MatchDto>, reason nullable)
- [ ] Criar `api/dto/search/MatchDto.java` (noId, texto, score, tipo, engineFeatureRef, audioRef: AudioRefDto)
- [ ] Criar `api/dto/selection/SelectRequest.java` (via: String, locale: String, clientEventId: String)
- [ ] Criar `api/dto/selection/SelectResponse.java` (noId, localeServed, texto, audioRef: AudioRefDto, playbackMode, degraded, via)
- [ ] Criar `api/dto/binding/BindingRequest.java` (engineFeatureRef: String, locale: String)
- [ ] Criar `api/dto/binding/BindingResponse.java` (engineFeatureRef, noId nullable, bound, selecionavel nullable)
- [ ] Criar `api/dto/common/AudioRefDto.java` (present: boolean, uri nullable, formato nullable)
- [ ] Criar `api/dto/health/ConvHealthResponse.java` (status, elasticsearch, audioStore, acs, degradedCapabilities: List<String>)
- [ ] `./gradlew :conv-service:compileJava` BUILD SUCCESS

## Resultado esperado

Todos os DTOs compiláveis, imutáveis (records Java ou construtores `@JsonCreator`), sem referência a entidades JPA.

## Criterios de aceite

- [ ] `./gradlew :conv-service:compileJava` BUILD SUCCESS
- [ ] DTOs em `com.neuroassist.conv.api.dto.*` sem dependência do pacote `domain.model`
- [ ] `SelectRequest.clientEventId` é `String` não-nulo (idempotency key)
- [ ] `SearchResponse` tem campo `reason` nullable (para a resposta degradada)
- [ ] `AudioRefDto.present` é `boolean` primitivo (nunca `Boolean` nullable)

## Dependencias

- CONV-001 (precede)

## Estimativa

M

## Prioridade

P1
