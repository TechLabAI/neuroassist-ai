# TREI-022 -- Outbox retry backoff + circuit-breaker para LLM/TTS + alert surface de outbox-falha

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto

Hardening do pipeline de publicação: (1) backoff exponencial no OutboxDrainer para
evitar DDoS em LLM/TTS; (2) circuit-breaker para chamadas ao Anthropic e ElevenLabs
(alta latência/custo); (3) steps em estado `falha` (budget excedido) ficam visíveis
via `GET /health` (`degradedCapabilities`) e `GET /nos/{noId}/publicacao` (per-step
state). Nunca silencioso — operador sempre pode ver o que está preso.

## Spec de referencia

- SPEC-07 (observability-health — alert surface, health degradedCapabilities)
- SPEC-09 (messaging-architecture — retry budget, backoff, circuit-breaker patterns)
- api-contract.md §2.8 (GET /nos/{noId}/publicacao — per-step state)
- api-contract.md §2.9 (GET /health — degradedCapabilities quando step em falha)

## Escopo

- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/outbox/OutboxRetryConfig.java` — configura backoff: `proxima_em = now() + min(2^tentativas * 10s, maxBackoff)`; `maxBackoff = 1h`; `maxTentativas` configurável via env (default 5)
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/resilience/LlmCircuitBreaker.java` — Resilience4j `CircuitBreaker` para chamadas ao `LlmAuthoringPort`; configuração: `failureRateThreshold=50`, `waitDurationInOpenState=60s`, `slidingWindowSize=10`
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/resilience/TtsCircuitBreaker.java` — Resilience4j `CircuitBreaker` para `TtsSynthesisPort`; mesma configuração que LLM
- [ ] integrar circuito no `OutboxDrainer`: se circuit breaker aberto → step fica `pendente` + `proxima_em` estendida (não marca `falha` por circuit aberto — apenas atrasa)
- [ ] alert surface: `TreiHealthIndicator` (TREI-018) deve incluir em `degradedCapabilities` quando algum `(no_id, locale)` tem step em `falha` há mais de 30min; `GET /nos/{noId}/publicacao` já expõe `tentativas` por step
- [ ] criar `src/main/resources/application.yml` — chaves de configuração: `trei.outbox.max-tentativas`, `trei.outbox.max-backoff`, `trei.resilience.llm.*`, `trei.resilience.tts.*`

## Resultado esperado

Outbox drainer com backoff exponencial; circuit breakers para LLM e TTS; steps em `falha`
visíveis no health endpoint sem bloquear o serviço.

## Criterios de aceite

- [ ] `./gradlew :trei-service:compileJava` sai 0 com Resilience4j no classpath
- [ ] `OutboxRetryConfig` produz `proxima_em` crescente para `tentativas 1,2,3,4,5` (verificável em teste unitário)
- [ ] `GET /health` inclui `degradedCapabilities: ["publication"]` quando existe step em `falha` há mais de 30min
- [ ] Circuit breaker aberto não marca step como `falha` permanente — apenas incrementa `proxima_em`

## Dependencias

- TREI-016 (OutboxDrainer — é o target do retry config)
- TREI-018 (TreiHealthIndicator — recebe input do alert surface)

## Estimativa

M

## Prioridade

P9
