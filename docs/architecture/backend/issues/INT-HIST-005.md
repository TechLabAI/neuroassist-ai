# INT-HIST-005 -- GWY routing integration test: JWT relay + X-Correlation-Id propagation

## Contexto

Verifica que o GWY encaminha corretamente as requisições para o `hist-service`:
JWT Bearer relayado, `X-Correlation-Id` gerado se ausente (ou propagado se presente),
e schema-pinning via `tenant` claim. Este teste é um teste de contrato entre GWY e HIST —
pode ser implementado como teste de integração do HIST com WireMock simulando o GWY,
ou como um smoke test de rota.

## Spec de referencia

- SPEC-01 (base path `/api/v1/hist`, routed through GWY)
- SPEC-10 (GWY como infra de roteamento)
- `contracts/api-contract.md §1.1 EARS`
- `contracts/integration-contracts.md §3`

## Escopo

**`src/test/kotlin/com/neuroassist/hist/integration/gateway/GatewayRoutingContractTest`:**
- Cenário 1 — JWT relay: request chega sem `X-Empresa-Id` header (removido pelo GWY); apenas `Authorization: Bearer` com `tenant` claim → HIST processa normalmente.
- Cenário 2 — X-Correlation-Id ausente: request sem `X-Correlation-Id` → HIST gera e retorna no response header.
- Cenário 3 — X-Correlation-Id presente: request com `X-Correlation-Id: abc-123` → response contém `X-Correlation-Id: abc-123` (ecoado sem modificação).
- Cenário 4 — X-Empresa-Id no body: request com `tenantId != JWT.tenant` → 403 `/errors/tenant-mismatch` (validação de HIST, não do GWY).
- Anotar que `X-Empresa-Id` como header de request não é trusted (documentado em api-contract §1.1).

## Resultado esperado

Os 4 cenários passam. HIST nunca confia em `X-Empresa-Id` do request.

## Criterios de aceite

- [ ] HIST ignora header `X-Empresa-Id` inbound (usa apenas JWT `tenant` claim).
- [ ] `X-Correlation-Id` gerado pelo HIST quando ausente no request.
- [ ] `X-Correlation-Id` ecoado sem modificação quando presente.
- [ ] Body `tenantId != JWT.tenant` → 403 independente de qualquer header.
- [ ] Testes documentam o contrato com GWY sem depender do código do GWY.

## Dependencias

- HIST-014, HIST-015 (security + tenant filter)
- HIST-018 (X-Correlation-Id filter)

## Estimativa

S

## Prioridade

P5
