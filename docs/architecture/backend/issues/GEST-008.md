# GEST-008 -- Implement EligibilityService.check() — emergency guard invariant (all primitives must be supported; rotina allows non-deprecated)

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto
`POST /api/v1/gest/eligibility:check` e chamado sincronamente pelo servico PAC ao construir um `Comando`. Para urgencia `emergencia`, TODOS os primitivos da lista devem estar em estado SUPPORTED (invariante #1, ADR-006). Para urgencia `rotina`, qualquer primitivo nao-deprecated e aceito. Este endpoint e read-only (nenhuma mutacao de estado). Primitivos desconhecidos no tenant retornam 422.

## Spec de referencia
- SPEC-03, api-contract.md §3, integration-contracts.md §1 (consumido por PAC)

## Escopo
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/service/EligibilityService.java` (injetando `TipoGestoRepositoryPort`)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/dto/EligibilityCheckRequest.java` (urgencia: EMERGENCIA|ROTINA, primitiveIds: List<UUID>)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/dto/EligibilityCheckResponse.java` (eligible: boolean, urgencia, results: List<PrimitiveEligibilityResult>)
- [ ] adicionar query `findAllByIdsInTenant(List<UUID> ids, String tenantId)` em `TipoGestoRepositoryPort`
- [ ] testes unitarios: emergencia todos-supported=eligible, emergencia algum-nao-supported=ineligivel, rotina nao-deprecated=eligible, primitivo desconhecido=422

## Resultado esperado
`POST /eligibility:check` com primitivos todos SUPPORTED e urgencia EMERGENCIA retorna `{ "eligible": true }` sem efeitos colaterais.

## Criterios de aceite
- [ ] urgencia EMERGENCIA com todos os primitivos SUPPORTED retorna `eligible: true`
- [ ] urgencia EMERGENCIA com ao menos um primitivo nao-SUPPORTED retorna `eligible: false` com `reason` por primitivo
- [ ] urgencia ROTINA com primitivo PENDING_TRAINING retorna `eligible: true`
- [ ] primitivo desconhecido no tenant retorna `422 /errors/unknown-primitive` listando os ids ofensores
- [ ] endpoint nao altera estado de nenhum `TipoGesto` (verificado por teste que asserta contagem constante em BD)

## Dependencias
GEST-004, GEST-005 (precede).

## Estimativa
M

## Prioridade
P2
