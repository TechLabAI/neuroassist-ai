# GEST-005 -- Implement REST controllers skeleton with DTOs (TipoGestoController, EligibilityController, DetectionProfileController, DetectionContractController)

**Status:** EM ANDAMENTO -- reconciliado 2026-08-10
**Maturidade:** 2 -- Camada REST presente; logica de negocio deferida para P2 (GEST-006..010)
**Done/Total:** 7/8 itens (scope) + 4/6 criterios de aceite

## Contexto
Os quatro controllers expoe todos os 11 endpoints definidos em `api-contract.md`. Nesta issue o objetivo e ter a superficie REST compilavel, com mapeamento de paths, DTOs de request/response e handlers de erro RFC 7807 conectados — sem logica de negocio (essa vai em GEST-006..010). O `GlobalExceptionHandler` mapeia os error types do api-contract.md (tipo-gesto-not-found, illegal-transition, etc.) para `ProblemDetail`.

NOTA DE RECONCILIACAO (2026-08-10): os controllers estao em `api/controller/` (nao `api/` plano como descrito originalmente), os DTOs em `api/dto/`, e o GlobalExceptionHandler em `api/exception/` — essas sao as localizacoes corretas no padrao hexagonal do projeto. O item de paginacao nao foi implementado e permanece pendente. `X-Detection-Contract-Version` nao esta sendo ecoado nos controllers de deteccao — pendente para P2/hardening. A geracao de `X-Correlation-Id` esta implementada via `CorrelationIdInterceptor` em `infrastructure/security/`.

## Spec de referencia
- SPEC-01, api-contract.md (todos os endpoints), SPEC-07

## Escopo
- [x] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/controller/TipoGestoController.java` (GET list, GET by id, POST create, POST transitions) delegando para `TipoGestoUseCase` — sem lançar UnsupportedOperationException; servico retorna null (stub P2)
- [x] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/controller/EligibilityController.java` (POST eligibility:check) delegando para `EligibilityUseCase`
- [x] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/controller/DetectionProfileController.java` (POST publish, GET active, GET by version, GET devices/{deviceId}/detection-profile) delegando para `DetectionProfileUseCase`
- [x] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/controller/DetectionContractController.java` (GET detection-contract, POST recognized-commands validate) delegando para `DetectionContractUseCase`
- [x] implementar todos os DTOs de request e response em `api/dto/` (TipoGestoRequest, TipoGestoResponse, TransitionRequest, EligibilityRequest, EligibilityResponse, DetectionProfileRequest, DetectionProfileResponse, DetectionContractRequest, DetectionContractResponse) como Java records com anotacoes Bean Validation onde aplicavel
- [x] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/exception/GlobalExceptionHandler.java` com `@ExceptionHandler(CodedException.class)` mapeando todos os 20+ error types via `ProblemDetailFactory.forCode()` para `ProblemDetail` RFC 7807 com `correlationId` do MDC
- [ ] adicionar paginacao (page/size) em `GET /tipos-gesto` e `GET /detection-profiles` -- PENDENTE: controllers retornam `List<>` (nao `Page<>`); diferido para P2 ou hardening pos-P8
- [x] `X-Correlation-Id` gerado se ausente na request e ecoado na resposta -- implementado em `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/security/CorrelationIdInterceptor.java` (`HandlerInterceptor` registrado no `WebMvcConfig`)

## Resultado esperado
Todos os 11 endpoints respondem (servicos com stubs null aceitos em P1) e o error model RFC 7807 com `correlationId` funciona para todos os tipos de erro do api-contract.md.

## Criterios de aceite
- [x] `GET /gest-service/tipo-gesto` retorna `200` (path real: `/gest-service/tipo-gesto`; criterio original referenciava `/api/v1/gest/tipos-gesto` que e o path apos gateway/prefixo; controller delega e retorna 200 com body null ate P2)
- [ ] request com body `tenantId != JWT.tenant` retorna `403 /errors/tenant-mismatch` -- PENDENTE: `TenantContextFilter` nao extrai tenantId do JWT ainda (P5 GEST-018)
- [x] endpoint inexistente retorna `404` com `application/problem+json` -- GlobalExceptionHandler estende `ResponseEntityExceptionHandler` que intercepta `NoHandlerFoundException` e produz `ProblemDetail` com Spring Boot 3 RFC 7807
- [x] todos os 13+ `type` de erro do api-contract.md §7 possuem handler mapeado em `GlobalExceptionHandler` -- implementado via `CodedException` + `ProblemDetailFactory`; 20+ codigos mapeados (tipo-gesto-not-found, tipo-gesto-duplicate, illegal-transition, eligibility-ineligible, no-active-profile, profile-not-found, profile-already-active, contract-violation, tenant-mismatch, tenant-not-found, device-not-paired, edge-unavailable, internal, validation, + outros)
- [ ] `X-Detection-Contract-Version` presente em todas as respostas dos endpoints de deteccao -- NAO implementado; DetectionContractController e DetectionProfileController nao setam este header; diferido para P2 (junto com logica de contrato)
- [x] `X-Correlation-Id` ecoado na resposta (gerado se ausente na request) -- `CorrelationIdInterceptor` gera UUID se header ausente e ecoa `X-Correlation-Id` em toda resposta

## Dependencias
GEST-001, GEST-004 (precede).

## Estimativa
M

## Prioridade
P1

## Dependencias
GEST-001, GEST-004 (precede).

## Estimativa
M

## Prioridade
P1
