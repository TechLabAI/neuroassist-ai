# EDGE-013 -- Implement TenantContextHolder propagation, MDC enrichment (tenantId, correlationId, deviceId) and X-Correlation-Id filter

**Status:** INTACT -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto
Todo request precisa propagar `tenantId` para o `SchemaRoutingDataSource` e enriquecer o MDC (correlationId, tenantId, actor, deviceId quando device token) para rastreabilidade e auditoria. O filtro tambem gera `X-Correlation-Id` quando ausente e o ecoa na resposta.

## Spec de referencia
- SPEC-07

## Escopo
- [ ] criar `infrastructure/filter/TenantContextFilter.java` (OncePerRequestFilter): extrair `tenant` do JWT, popular `TenantContextHolder`, popular MDC com `tenantId`, `correlationId`, `actor`
- [ ] criar `infrastructure/filter/CorrelationIdFilter.java`: ler `X-Correlation-Id` do request ou gerar UUID, propagar para MDC e para o response header
- [ ] criar `application/service/TenantContextHolder.java` (ThreadLocal<String>) com `get/set/clear`; limpar no `finally` do filter para evitar leak
- [ ] adicionar `logback-spring.xml` com padrao JSON estruturado incluindo MDC fields (timestamp, level, logger, message, correlationId, tenantId, actor)
- [ ] criar teste de integracao: request com `X-Correlation-Id=test-123` -> response header echo `X-Correlation-Id=test-123`

## Resultado esperado
Todos os logs de request tem `tenantId` e `correlationId` no MDC; X-Correlation-Id ecoado na resposta.

## Criterios de aceite
- [ ] Request sem `X-Correlation-Id` -> header gerado (UUID) e ecoado na resposta
- [ ] Request com `X-Correlation-Id` -> mesmo valor ecoado (nao substituido)
- [ ] MDC contem `tenantId` em todos os logs dentro de um request
- [ ] Nenhum valor de credential, token, hash ou secret aparece em qualquer log
- [ ] TenantContextHolder.clear() chamado no finally do filter (sem leak entre requests)

## Dependencias
- EDGE-012 (precede)

## Estimativa
S

## Prioridade
P5
