# EDGE-004 -- Implement REST controllers skeleton with DTOs (DeviceController, HeartbeatController, LivenessController, PairingController)

**Status:** DONE
**Maturidade:** 85 -- Maduro
**Done/Total:** 6/6 itens

## Contexto
O skeleton de controllers com DTOs e o ponto de entrada de todos os endpoints do api-contract.md. Nesta fase, os controllers delegam para services ainda nao implementados (lancam UnsupportedOperationException). O importante e a assinatura correta das rotas, DTOs e o GlobalExceptionHandler com RFC 7807.

## Spec de referencia
- SPEC-01

## Escopo
- [x] criar `api/DeviceController.java` com: POST /api/v1/edge/devices, POST /api/v1/edge/devices/enroll, POST /api/v1/edge/devices/{id}:retire, GET /api/v1/edge/devices/{deviceId}, GET /api/v1/edge/devices/{deviceId}/tenant, GET /api/v1/edge/patients/{pacienteId}/device
- [x] criar `api/HeartbeatController.java` com: POST /api/v1/edge/heartbeat
- [x] criar `api/LivenessController.java` com: GET /api/v1/edge/devices/{deviceId}/liveness, GET /api/v1/edge/patients/{pacienteId}/liveness
- [x] criar `api/PairingController.java` com: POST /api/v1/edge/devices/{id}/pairings, DELETE /api/v1/edge/devices/{id}/pairings/current
- [x] criar todos os DTOs de request/response (RegisterDeviceRequest, EnrollRequest, HeartbeatRequest, HeartbeatResponse, LivenessView, DeviceView, PairingRequest, ProblemDetail conforme api-contract §8)
- [x] criar `GlobalExceptionHandler` para converter excecoes de dominio em RFC 7807 ProblemDetail com campo `correlationId` e Content-Type `application/problem+json`

## Resultado esperado
Todos os 11 endpoints do api-contract §9 respondem 501 Not Implemented compilando sem erros; DTOs completos.

## Criterios de aceite
- [ ] `compileJava` exit 0 para todos os controllers e DTOs
- [ ] POST /api/v1/edge/devices mapeado com `produces=application/json` e retorno 201
- [ ] POST /api/v1/edge/devices/enroll nao exige Authorization header na rota
- [ ] `GlobalExceptionHandler` serializa erros como `application/problem+json` com campo `correlationId`
- [ ] `LivenessView` contem campo `serverTime` (hard dependency FE — M3 warning fechado aqui)

## Dependencias
- EDGE-001 (precede)

## Estimativa
M

## Prioridade
P1
