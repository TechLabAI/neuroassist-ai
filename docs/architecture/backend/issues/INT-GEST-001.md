# INT-GEST-001 -- Integration with EDGE service — device pairing verification for profile pull

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
`gest-service` chama `edge-service` para verificar pareamento de dispositivo antes de servir o profile pull (`GET /devices/{deviceId}/detection-profile`). Contrato: `GET /api/v1/edge/devices/{deviceId}/tenant` → `{ deviceId, tenantRef }` ou 404. Descoberta via REG (Eureka). Timeout: read 2000ms / connect 500ms. Circuit breaker configurado em GEST-025.

## Spec de referencia
- integration-contracts.md §1, api-contract.md §4.4, §6.3, SPEC-10

## Escopo
- [ ] validar endpoint `GET /api/v1/edge/devices/{deviceId}/tenant` com contrato de resposta definido em integration-contracts.md §1
- [ ] validar que `X-Correlation-Id` e forwarded para EDGE em todas as chamadas (inspecao no WireMock)
- [ ] validar resilience (circuit breaker GEST-025): EDGE indisponivel → 503 sem bypass de seguranca
- [ ] documentar em integration-contracts.md §1.3 o item aberto de bridge transport auth (mTLS vs client_credentials — deve virar decisao conjunta GEST+EDGE)

## Resultado esperado
Integracao funcional fim-a-fim: device com pareamento valido obtem perfil; device sem pareamento recebe 403; EDGE indisponivel recebe 503.

## Criterios de aceite
- [ ] WireMock stub de EDGE retornando 200 com tenantRef correto: profile pull bem-sucedido para o tenant correto
- [ ] WireMock stub de EDGE retornando 404: `403 /errors/device-not-paired`
- [ ] WireMock stub de EDGE com circuito aberto: `503 Service Unavailable` (nao 500)
- [ ] nenhuma informacao de paciente enviada ao EDGE — apenas deviceId no path URL

## Dependencias
GEST-015 (Feign client), GEST-025 (circuit breaker).

## Estimativa
M

## Prioridade
P4
