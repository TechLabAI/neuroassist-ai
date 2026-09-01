# TREI-020 -- Cucumber BDD: dual-authorization-gate + llm-authoring-proposal features

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto

Implementa step definitions para as features BDD `dual-authorization-gate.feature` e
`llm-authoring-proposal.feature` já geradas em M3 (nos arquivos de spec:
`tests/cucumber/dual-authorization-gate.feature` e `tests/cucumber/llm-authoring-proposal.feature`).
O runner usa Cucumber JVM + Spring Boot Test. **Pitfall D-492**: `application-test.yml`
DEVE ter `jwk-set-uri` (já criado em TREI-014) e NÃO usar `@EmbeddedKafka` (messaging
desabilitado). WireMock para simular Anthropic API, ACS e PAC.

## Spec de referencia

- SPEC-08 (bdd-test-scenarios — features e cenários de BDD)
- tests/cucumber/dual-authorization-gate.feature (31 cenários totais do M3)
- tests/cucumber/llm-authoring-proposal.feature

## Escopo

- [ ] criar `src/test/java/com/neuroassist/trei/bdd/CucumberRunnerTest.java` — `@CucumberOptions(features="classpath:features", glue="com.neuroassist.trei.bdd.steps")` + `@SpringBootTest` + configuração WireMock
- [ ] copiar features para `src/test/resources/features/dual-authorization-gate.feature` e `llm-authoring-proposal.feature` (a partir dos arquivos canônicos em `tests/cucumber/`)
- [ ] criar `src/test/java/com/neuroassist/trei/bdd/steps/DualAuthorizationSteps.java` — steps para: session start, caregiver approval (idempotent), patient approval, gate fired, outbox created, 403 wrong actor, 401 revoked session
- [ ] criar `src/test/java/com/neuroassist/trei/bdd/steps/LlmAuthoringSteps.java` — steps para: session creation com llmModelId recorded, proposal turn (Anthropic mock via WireMock), persist before return, 503 quando Anthropic down, aiDisclosed=true
- [ ] configurar WireMock stubs: Anthropic API mock (retorna propostas JSON fixas), ACS blacklist mock (retorna não-revogado por default), PAC patient resolution mock
- [ ] NÃO usar `@EmbeddedKafka` (messaging.enabled=false, D-492)

## Resultado esperado

Cenários BDD dos dois features passando com WireMock; nenhum teste requer Kafka, Keycloak
real ou ES ativo.

## Criterios de aceite

- [ ] `./gradlew :trei-service:test --tests "*CucumberRunnerTest*"` sai 0 com todos os cenários de dual-auth e llm-authoring passando
- [ ] Nenhuma classe de test usa `@EmbeddedKafka` (grep deve retornar vazio)
- [ ] `application-test.yml` tem `jwk-set-uri` e NÃO tem `issuer-uri` (D-492 pitfall)
- [ ] Steps WireMock: Anthropic retornando 503 → cenário de `llm-unavailable` passa

## Dependencias

- TREI-005 (SessaoTreinamentoService — testado pelos steps)
- TREI-006 (AutorizacaoService — testado pelos steps)
- TREI-014 (application-test.yml com jwk-set-uri)

## Estimativa

L

## Prioridade

P8
