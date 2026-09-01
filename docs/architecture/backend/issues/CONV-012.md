# CONV-012 -- Criar VocabularyAdapter — Feign para pac-service (client_credentials, scope pac.vocabulary.read, WireMock stub)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

Implementa `VocabularyPort` como adapter Feign para `pac-service`. Autenticação serviço-a-serviço
via OAuth2 `client_credentials` (ADR-007), scope `pac.vocabulary.read`. O `engineFeatureRef` é
tratado como string opaca — o adapter envia o valor para PAC sem interpretar. Se PAC/GEST não
expuser o endpoint de resolução (gap G4 não resolvido), o adapter retorna `Optional.empty()` (soft
dependency). Para testes BDD, WireMock stubb o endpoint de vocabulário.
**Nota GAP G4:** o formato real do commit `89f4ee5` não foi confirmado; o adapter modela o contrato
como `string id → UUID do nó` e degradará para `bound: false` se PAC/GEST não responder.

## Spec de referencia

- SPEC-10 (integrations — VocabularyPort, gap G4)
- `contracts/hub-integration.md` §3

## Escopo

- [ ] Criar `infrastructure/integration/pac/VocabularyFeignClient.java` (`@FeignClient(name="pac-service", url="${neuroassist.pac.url}")`) com operação `resolveFeatureRef(String engineFeatureRef, UUID pacienteId): Optional<VocabularyRefResponse>` (GET `/pac/vocabulary/resolve?ref=<ref>&pacienteId=<id>`)
- [ ] Criar `infrastructure/integration/pac/VocabularyAdapter.java` implementando `VocabularyPort` — captura `FeignException.NotFound` e qualquer exceção de infra → `Optional.empty()` (soft dependency)
- [ ] Configurar OAuth2 `client_credentials` token provider para o Feign client (Spring Security OAuth2 Client: `@RegisteredOAuth2AuthorizedClient` ou `OAuth2AuthorizedClientManager` interceptor)
- [ ] Configurar `neuroassist.pac.url` stub em `application-test.yml` (Learning D-533)
- [ ] Criar `WireMockVocabularyStub.java` para testes BDD — responde com `{noId: "n-42"}` para `gest:blink-double`; `404` para refs desconhecidos

## Resultado esperado

`VocabularyPort` funcional com autenticação `client_credentials`; ref desconhecido ou PAC indisponível → `Optional.empty()`.

## Criterios de aceite

- [ ] `./gradlew :conv-service:test` BUILD SUCCESS
- [ ] Teste unitário: WireMock responde `{noId: "n-42"}` → `Optional.of(n-42 UUID)` retornado
- [ ] Teste unitário: WireMock responde `404` → `Optional.empty()` (sem exceção)
- [ ] Teste unitário: WireMock não inicia (timeout) → `Optional.empty()` (soft dependency, sem 500)
- [ ] Feign client usa `OAuth2AuthorizedClientManager` interceptor com scope `pac.vocabulary.read`

## Dependencias

- CONV-004 (VocabularyPort)
- CONV-007 (BindingResolutionService — consome este adapter)

## Estimativa

M

## Prioridade

P4
