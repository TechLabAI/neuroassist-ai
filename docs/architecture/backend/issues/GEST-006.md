# GEST-006 -- Implement TipoGestoService.register() with duplicate-nome guard, tenant isolation, and PrimitiveRegistered in-process event

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto
O golden path de registro de um novo primitivo: validar `nome` (nao vazio, <= 120 chars), checar duplicata case-insensitive no tenant (excluindo deprecated), persistir com `detectabilityState=REGISTERED`, emitir evento in-process `PrimitiveRegistered` pos-commit. A tenant isolation e garantida pelo `TenantContextHolder` — nenhum `tenantId` vem do body.

## Spec de referencia
- SPEC-02, api-contract.md §2.3, event-contracts.md §2

## Escopo
- [ ] implementar `TipoGestoService.register(String nome)` em `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/service/TipoGestoService.java` (injetando `TipoGestoRepositoryPort` e `ApplicationEventPublisher`)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/event/PrimitiveRegistered.java` (record: tipoGestoId, nome, tenantRef, actor, at)
- [ ] publicar `PrimitiveRegistered` via `ApplicationEventPublisher.publishEvent()` dentro de `@Transactional` (o listener usa `@TransactionalEventListener(phase=AFTER_COMMIT)`)
- [ ] rejeitar com `DuplicateTipoGestoException` se nome ja existe (nao deprecated) no tenant
- [ ] testes unitarios de register (caminho feliz, duplicata, nome vazio, nome > 120)

## Resultado esperado
`POST /api/v1/gest/tipos-gesto` cria primitivo com estado REGISTERED, retorna `201 Created` com `Location` e nao permite nome duplicado no tenant.

## Criterios de aceite
- [ ] request com nome novo retorna `201 Created` com body `detectabilityState: "registered"` e `engineFeatureRef: null`
- [ ] request com nome duplicado (case-insensitive, nao deprecated) retorna `409 /errors/tipo-gesto-duplicate`
- [ ] request com nome vazio retorna `400 /errors/validation`
- [ ] `PrimitiveRegistered` e publicado exatamente uma vez por chamada bem-sucedida (verificado em teste com `@SpyBean ApplicationEventPublisher`)
- [ ] `detectabilityState` inicial e sempre REGISTERED, independente de qualquer campo de request

## Dependencias
GEST-002, GEST-004, GEST-005 (precede).

## Estimativa
M

## Prioridade
P2
