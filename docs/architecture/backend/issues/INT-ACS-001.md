# INT-ACS-001 -- MigProvisioningAdapter: Feign client para MIG (saga de schema provisioning)

**Status:** INTACT -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto

Implementar `MigProvisioningAdapter` como adapter Feign que implementa `MigProvisioningPort`.
Crítico: o timeout (5s) e o circuit breaker são fail-closed — falha no MIG nunca ativa um tenant.

## Spec de referência

- `contracts/event-contracts.md §2.1` (MigProvisioningPort, timeout 5s, circuit breaker)
- SPEC-10 §2.1 (EARS: saga integration)

## Escopo

- [ ] criar `MigProvisioningClient extends MigProvisioningPort` com `@FeignClient(name="neuroassist-ai-migrations")`
- [ ] configurar Resilience4j `CircuitBreaker` + `TimeLimiter` (timeout 5s) para este client
- [ ] circuit breaker OPEN → lançar `MigUnavailableException` (mapeada para `202 Accepted`)
- [ ] propagar `X-Correlation-Id` e `X-Empresa-Id` via Feign `RequestInterceptor`

## Critérios de aceite

- [ ] MIG timeout 5s → `MigUnavailableException` lançada (não hang)
- [ ] Circuit breaker OPEN após N falhas → fail-fast imediato
- [ ] `X-Correlation-Id` presente em toda chamada ao MIG

## Dependências

- ACS-001 (spring-cloud-starter-openfeign, spring-cloud-starter-circuitbreaker-resilience4j)
- INT-GWY/REG (neuroassist-ai-migrations registrado no Eureka)

## Estimativa

S

## Prioridade

P2
