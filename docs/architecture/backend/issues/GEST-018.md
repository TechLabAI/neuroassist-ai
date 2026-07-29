# GEST-018 -- Implement TenantContextHolder propagation + MDC enrichment (tenantId, correlationId, X-Correlation-Id echo)

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto
Dois filtros de servlet sao necessarios: (1) `TenantContextFilter` extrai o `tenantId` do JWT autenticado e popula `TenantContextHolder` (ThreadLocal); (2) `CorrelationIdFilter` le `X-Correlation-Id` do request (ou gera um UUID se ausente), popula MDC, e garante que o header seja ecoado na resposta. Ambos filtros limpam o contexto no finally para evitar vazamento entre requests.

## Spec de referencia
- SPEC-05, SPEC-07, api-contract.md §1.1, hub-integration.md §1

## Escopo
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/filter/TenantContextFilter.java` (OncePerRequestFilter: extrai `tenant` claim do SecurityContextHolder, seta `TenantContextHolder.set(tenantId)`, limpa em finally)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/filter/CorrelationIdFilter.java` (OncePerRequestFilter: le `X-Correlation-Id`, gera UUID se ausente, seta MDC `correlationId`, adiciona ao response header, limpa em finally)
- [ ] criar `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/config/TenantContextHolder.java` (ThreadLocal wrapper com get/set/clear)
- [ ] registrar ambos filtros com ordem correta (CorrelationId antes de TenantContext) em `SecurityConfig` ou `FilterRegistrationBean`
- [ ] testes de filtro: correlationId gerado quando ausente, correlationId ecoado quando presente, tenantId limpo apos request

## Resultado esperado
Todo request tem `X-Correlation-Id` no response; `TenantContextHolder.get()` retorna o tenantId correto dentro de qualquer application service durante o request.

## Criterios de aceite
- [ ] request sem `X-Correlation-Id` recebe header gerado na resposta
- [ ] request com `X-Correlation-Id: abc123` recebe `X-Correlation-Id: abc123` na resposta (ecoado)
- [ ] `TenantContextHolder.get()` retorna tenantId do JWT dentro de `TipoGestoService.register()`
- [ ] `TenantContextHolder.get()` retorna null apos o request completar (ThreadLocal limpo)
- [ ] MDC `correlationId` presente em todos os logs emitidos durante o request (verificado em logback test appender)

## Dependencias
GEST-001, GEST-017 (SecurityConfig deve existir para que o JWT esteja no SecurityContextHolder).

## Estimativa
S

## Prioridade
P5
