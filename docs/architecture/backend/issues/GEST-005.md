# GEST-005 -- Implement REST controllers skeleton with DTOs (TipoGestoController, EligibilityController, DetectionProfileController, DetectionContractController)

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/8 itens

## Contexto
Os quatro controllers expoe todos os 11 endpoints definidos em `api-contract.md`. Nesta issue o objetivo e ter a superficie REST compilavel, com mapeamento de paths, DTOs de request/response e handlers de erro RFC 7807 conectados — sem logica de negocio (essa vai em GEST-006..010). O `GlobalExceptionHandler` mapeia os error types do api-contract.md (tipo-gesto-not-found, illegal-transition, etc.) para `ProblemDetail`.

## Spec de referencia
- SPEC-01, api-contract.md (todos os endpoints), SPEC-07

## Escopo
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/TipoGestoController.java` (GET list, GET by id, POST create, POST transitions) delegando para `TipoGestoService` (stub)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/EligibilityController.java` (POST eligibility:check)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/DetectionProfileController.java` (POST publish, GET active, GET by version, GET devices/{deviceId}/detection-profile)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/DetectionContractController.java` (GET detection-contract, POST recognized-commands validate)
- [ ] implementar todos os DTOs de request e response em `api/dto/` (TipoGestoDto, TransitionRequest, EligibilityCheckRequest/Response, DetectionProfileDto, RecognizedCommandReportDto, ContractDescriptorDto)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/GlobalExceptionHandler.java` com todos os `@ExceptionHandler` mapeando para `ProblemDetail` RFC 7807 (incluindo correlationId)
- [ ] adicionar paginacao (page/size) em `GET /tipos-gesto` e `GET /detection-profiles`
- [ ] validacao de `@RequestHeader` para `X-Correlation-Id` (opcional, gerado se ausente)

## Resultado esperado
Todos os 11 endpoints respondem (mesmo que com 501 Not Implemented temporario) e o error model RFC 7807 com `correlationId` funciona para todos os tipos de erro do api-contract.md.

## Criterios de aceite
- [ ] `GET /api/v1/gest/tipos-gesto` retorna `200` (mesmo que com lista vazia)
- [ ] request com body `tenantId != JWT.tenant` retorna `403 /errors/tenant-mismatch`
- [ ] endpoint inexistente retorna `404` com `application/problem+json`
- [ ] todos os 13 `type` de erro do api-contract.md §7 possuem handler mapeado em `GlobalExceptionHandler`
- [ ] `X-Detection-Contract-Version` presente em todas as respostas dos endpoints de deteccao
- [ ] `X-Correlation-Id` ecoado na resposta (gerado se ausente na request)

## Dependencias
GEST-001, GEST-004 (precede).

## Estimativa
M

## Prioridade
P1
