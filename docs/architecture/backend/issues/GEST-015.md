# GEST-015 -- Implement EdgePairingClient Feign adapter (GET /api/v1/edge/devices/{deviceId}/tenant via REG discovery) with WireMock stub

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto
`gest-service` precisa verificar se um `deviceId` pertence ao tenant do chamador antes de servir o profile pull (endpoint `GET /devices/{deviceId}/detection-profile`). Esta verificacao e feita chamando `edge-service` via Feign (descoberto pelo REG). O `EdgePairingAdapter` implementa `EdgePairingPort` e mapeia `404` do edge para `Optional.empty()` (que o controller converte em `403 device-not-paired`). WireMock cobre os caminhos feliz, nao-encontrado e timeout.

## Spec de referencia
- SPEC-10, integration-contracts.md §1, api-contract.md §4.4, §6.3

## Escopo
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/integration/EdgePairingClient.java` (Feign interface: `GET /api/v1/edge/devices/{deviceId}/tenant`, retorna `EdgePairingResponse`)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/integration/EdgePairingAdapter.java` (implementa `EdgePairingPort`; mapeia `FeignException.NotFound` para `Optional.empty()`; forwarda `X-Correlation-Id`)
- [ ] adicionar config de timeout (read 2000ms, connect 500ms) em `application.yml` para o feign client edge-service
- [ ] implementar `backend/services/gest-service/src/test/java/com/neuroassist/gest/integration/EdgePairingClientWireMockTest.java` (stubs: 200 com tenantRef, 404, timeout/5xx)
- [ ] adicionar `EdgePairingResponse.java` DTO (deviceId, tenantRef)

## Resultado esperado
`GET /devices/{deviceId}/detection-profile` retorna `403 device-not-paired` quando EDGE retorna 404; funciona normalmente quando EDGE confirma o pareamento.

## Criterios de aceite
- [ ] EDGE retorna 200 com tenantRef correto: profile pull funciona para o tenant certo
- [ ] EDGE retorna 404: endpoint retorna `403 /errors/device-not-paired`
- [ ] EDGE retorna timeout (WireMock com delay): `EdgePairingAdapter` propaga excecao de timeout (tratada em GEST-025 com Resilience4j)
- [ ] `X-Correlation-Id` presente no header da chamada para EDGE (verificado no WireMock request log)
- [ ] nenhuma informacao de paciente/biometrica enviada para EDGE (apenas deviceId no path)

## Dependencias
GEST-004, GEST-009 (precisa de perfil ativo para testar o pull).

## Estimativa
M

## Prioridade
P4
