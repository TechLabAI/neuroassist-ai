# INT-ACS-002 -- PacPatientResolveAdapter: Feign client para PAC (validação de link fail-closed)

**Status:** INTACT -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto

`PacPatientResolveAdapter` valida que um `pacienteId` existe e pertence ao tenant correto
antes de criar um vínculo cuidador↔paciente. Fail-closed: PAC unreachable → link rejeitado.

## Spec de referência

- `contracts/event-contracts.md §2.2` (PacPatientResolvePort, timeout 2s, 1 retry)
- SPEC-10 §2.2 (EARS)

## Escopo

- [ ] criar `PacPatientResolveClient extends PacPatientResolvePort` com `@FeignClient(name="pac-service")`
- [ ] configurar timeout 2s + 1 retry + circuit breaker OPEN → `UpstreamUnavailableException`
- [ ] mapear PAC `404` → `UnknownPatientException` (422)
- [ ] validar tenant match no adapter (response `tenantId` vs. `tenantId` do caller)
- [ ] propagar `X-Correlation-Id` e `X-Empresa-Id`

## Critérios de aceite

- [ ] PAC 404 → `422 unknown-patient` (não `404`)
- [ ] PAC timeout 2s → `502/504 upstream-unavailable`
- [ ] Tenant mismatch em response → `422 cross-tenant-link`

## Dependências

- ACS-001 (Feign + Resilience4j)
- PAC M5 scaffold (pac-service disponível no Eureka)

## Estimativa

S

## Prioridade

P3
