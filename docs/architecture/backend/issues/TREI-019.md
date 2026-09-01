# TREI-019 -- Structured logging + X-Correlation-Id propagation

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

`X-Correlation-Id` é injetado pelo GWY quando ausente e deve ser ecoado em toda response
e propagado para TODOS os downstream calls: Anthropic API, ElevenLabs, embedding sidecar,
ES, ACS, HIST (api-contract §1.1). MDC garante que o correlation id apareça em todos os
logs do request. Logging estruturado (JSON) para parsing pelo ELK stack (SPEC-07).

## Spec de referencia

- SPEC-07 (observability-health — X-Correlation-Id, MDC, structured logging)
- api-contract.md §1.1 (X-Correlation-Id: mandatory header — inject if absent, echo, propagate)

## Escopo

- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/logging/CorrelationIdFilter.java` — `OncePerRequestFilter`: extrai `X-Correlation-Id` do header (se ausente, gera UUID); coloca em `MDC.put("correlationId", id)`; adiciona `X-Correlation-Id` na response; limpa MDC no finally
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/logging/MdcLoggingConfig.java` — configura Logback para incluir `%X{correlationId}` no pattern JSON; ativa `logback-spring.xml` profile `json`
- [ ] criar `src/main/resources/logback-spring.xml` — configuração Logback com appender JSON estruturado; inclui campos: `timestamp`, `level`, `logger`, `message`, `correlationId`, `empresa`, `traceId`
- [ ] propagar correlation id nos outbound calls: os adapters de Anthropic, ElevenLabs, sidecar, HistAuditAdapter, AcsSessionAdapter, ElasticsearchIndexAdapter devem adicionar `X-Correlation-Id: <value>` nos headers de suas requests HTTP/Feign; usar `RequestInterceptor` Feign para os Feign clients
- [ ] criar `src/test/java/com/neuroassist/trei/infrastructure/logging/CorrelationIdFilterTest.java` — verificar que correlation id é ecoado na response e que MDC é limpo após cada request

## Resultado esperado

Todos os logs do request carregam `correlationId`; header propagado para todos os
subsistemas downstream; MDC limpo entre requests.

## Criterios de aceite

- [ ] `./gradlew :trei-service:test` sai 0
- [ ] `CorrelationIdFilter` gera UUID se `X-Correlation-Id` ausente (não propaga null)
- [ ] `logback-spring.xml` produz JSON válido com campo `correlationId` em cada linha de log
- [ ] Feign `RequestInterceptor` adiciona `X-Correlation-Id` a todas as chamadas para ACS, PAC, HIST

## Dependencias

- TREI-004 (controllers — filter atua sobre requests que chegam aos controllers)

## Estimativa

S

## Prioridade

P7
