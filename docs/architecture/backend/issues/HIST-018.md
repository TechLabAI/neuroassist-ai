# HIST-018 -- Structured JSON logging + X-Correlation-Id propagation

## Contexto

Cada linha de log deve ser JSON estruturado e carregar `correlationId`, `tenant`,
`factType` e `streamKey` quando aplicável. Nunca logar `payload` bruto (LGPD) nem
hashes completos além do necessário (SPEC-07). O `X-Correlation-Id` gerado ou recebido
no GWY deve ser propagado para chamadas ACS outbound (MDC → Feign interceptor).

## Spec de referencia

- SPEC-07 (Observability & Health — logging estruturado, propagação de correlação)
- `contracts/api-contract.md §1.1` (X-Correlation-Id)

## Escopo

**`com.neuroassist.hist.infrastructure.logging`:**
- Dependência `logstash-logback-encoder` para JSON estruturado em `build.gradle.kts`.
- `logback-spring.xml`: `JsonEncoder` para profile non-test; pattern legível para testes.
- `CorrelationIdFilter` (`OncePerRequestFilter`): lê `X-Correlation-Id` do header (ou gera UUID v4 se ausente); seta `MDC.put("correlationId", ...)` e `MDC.put("tenant", ...)`; ecoa no response header. Limpa MDC no finally.
- `FeignCorrelationInterceptor` (`RequestInterceptor`): propaga `MDC.get("correlationId")` como `X-Correlation-Id` nas chamadas ACS.
- Log statements nos services (HIST-006, HIST-008): incluir `factType`, `streamKey`, `sequenceNo` via `log.info("{}", StructuredArguments.kv(...))` (logstash pattern) — **nunca** incluir `payload`.
- Nível `DEBUG` para hash computation; `INFO` para appends e verificações.

## Resultado esperado

Log de um append produz linha JSON com `correlationId`, `tenant`, `factType`, `streamKey`,
`sequenceNo` mas SEM `payload`, SEM hash completo além de primeiros 8 chars para debug.

## Criterios de aceite

- [ ] Log de append contém campos `correlationId`, `tenant`, `factType`, `streamKey`, `sequenceNo`.
- [ ] Log não contém `payload` (nem parcialmente).
- [ ] `X-Correlation-Id` ausente na requisição → gerado e ecoado na resposta.
- [ ] `X-Correlation-Id` presente na requisição → ecoado sem modificação.
- [ ] Chamada ACS outbound carrega `X-Correlation-Id` do MDC.
- [ ] MDC limpo após cada request (teste: dois requests sequenciais não compartilham correlationId).

## Dependencias

- HIST-001 (logback dependency)
- HIST-012 (Feign client — adicionar interceptor)
- HIST-006, HIST-008 (log statements a adicionar)

## Estimativa

S

## Prioridade

P7
