# PAC-INT-005 -- Implementar propagação de X-Correlation-Id e X-Empresa-Id em todas as chamadas Feign outbound

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto
O SPEC-10 §3 exige que `X-Correlation-Id` e `X-Empresa-Id` sejam propagados em TODAS as chamadas
outbound Feign (GEST, ACS, EDGE, HIST). `FeignHeaderPropagator` é um `RequestInterceptor` global
registrado no Feign que lê do MDC (`correlationId`) e do `TenantContextHolder` (`empresaId`)
e injeta nos headers outbound. Isso garante rastreabilidade end-to-end sem modificar cada client.

## Spec de referencia
- SPEC-10 §3 (common outbound conventions — header propagation)
- SPEC-01 §1.1 (X-Correlation-Id echoed end-to-end)

## Escopo
- [ ] implementar `FeignHeaderPropagator implements RequestInterceptor`:
      `apply(RequestTemplate)` → adiciona `X-Correlation-Id` do MDC
      e `X-Empresa-Id` do `TenantContextHolder.get()`
- [ ] registrar como `@Bean` em `FeignConfig` (aplicado globalmente a todos os FeignClients)
- [ ] tratar o caso onde `TenantContextHolder` é nulo (chamadas de background/outbox):
      usar o valor capturado no momento da request original
- [ ] TODO[agent]: itens adicionais de escopo

## Criterios de aceite
- [ ] Toda chamada Feign outbound inclui `X-Correlation-Id` (do MDC do thread atual)
- [ ] Toda chamada Feign outbound inclui `X-Empresa-Id` (do TenantContextHolder)
- [ ] Retry do outbox (`HistRetryOutbox`) também propaga os headers corretamente
- [ ] Sem `FeignHeaderPropagator`, o SPEC-10 §3 fica violado — deve ser testado unitariamente
- [ ] TODO[agent]: criterio de aceite verificavel

## Dependencias
- PAC-001 (spring-cloud-starter-openfeign)
- PAC-009 (TenantContextHolder — fonte do empresaId)
- PAC-024 (CorrelationMdcFilter — fonte do correlationId no MDC)

## Estimativa
S

## Prioridade
P4
