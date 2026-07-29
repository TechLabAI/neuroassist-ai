# GEST-007 -- Implement TipoGestoService.transition() state-machine enforcement, append-only audit row, PrimitivePromoted and PrimitiveDeprecated events

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto
`POST /api/v1/gest/tipos-gesto/{id}/transitions` e o unico ponto de mutacao do estado de detectabilidade. A logica de negocio aplica o allowed-transition set do ADR-006, exige `engineFeatureRef` nao nulo para transicionar para SUPPORTED (invariante #5), exige `reason` para regressoes de SUPPORTED (invariante #4), e grava um registro imutavel em `TipoGestoTransition`. Eventos `PrimitivePromoted` ou `PrimitiveDeprecated` sao emitidos pos-commit.

## Spec de referencia
- SPEC-02, api-contract.md §2.4, event-contracts.md §2, ADR-006

## Escopo
- [ ] implementar `TipoGestoService.transition(UUID id, DetectabilityState toState, String engineFeatureRef, String reason)` em `TipoGestoService.java` (loads aggregate, validates transition, appends audit row, publishes event)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/event/PrimitivePromoted.java` (record: tipoGestoId, fromState, toState, engineFeatureRef, actor, at)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/event/PrimitiveDeprecated.java` (record: tipoGestoId, fromState, actor, reason, at)
- [ ] adicionar `appendTransition(TipoGestoTransition)` em `TipoGestoRepositoryPort` e no adapter
- [ ] validar invariante #4: regressao de SUPPORTED sem `reason` retorna 400
- [ ] testes unitarios: transicoes validas, transicao ilegal, supported-sem-ref, regressao-sem-reason

## Resultado esperado
Transicoes validas atualizam estado e gravam audit row; transicoes invalidas retornam 409; invariante #5 retorna 422 se engineFeatureRef ausente para SUPPORTED.

## Criterios de aceite
- [ ] transicao REGISTERED→PENDING_TRAINING retorna `200` com estado atualizado e `tipo_gesto_transition` com from/to corretos
- [ ] transicao REGISTERED→DEPRECATED retorna `409 /errors/illegal-transition` (nao e arco permitido)
- [ ] transicao →SUPPORTED sem `engineFeatureRef` retorna `422 /errors/engine-feature-ref-required`
- [ ] regressao de SUPPORTED para DEPRECATED sem `reason` retorna `400 /errors/regression-reason-required`
- [ ] cada transicao bem-sucedida cria exatamente um registro em `tipo_gesto_transition` (verificado em teste de integracao)

## Dependencias
GEST-006 (precede — mesmo service).

## Estimativa
M

## Prioridade
P2
