# GEST-019 -- Implement Spring TransactionalEventListener wiring for Primitive* in-process domain events (post-commit, no rollback on listener failure)

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
Os eventos de dominio `PrimitiveRegistered`, `PrimitivePromoted` e `PrimitiveDeprecated` sao publicados em `TipoGestoService` via `ApplicationEventPublisher`. Os listeners devem processar esses eventos pos-commit (`TransactionPhase.AFTER_COMMIT`) para que nenhum consumer observe estado nao-commitado. A falha em um listener NAO deve reverter a transacao principal (logado como erro, mas isolado). O listener de `PrimitiveRegistered` grava no log de auditoria estruturado; `PrimitiveDeprecated` invalida cache de eligibility.

## Spec de referencia
- SPEC-09, event-contracts.md §2

## Escopo
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/listener/PrimitiveEventListener.java` com handlers `@TransactionalEventListener(phase=AFTER_COMMIT)` para `PrimitiveRegistered`, `PrimitivePromoted`, `PrimitiveDeprecated`
- [ ] handler de `PrimitiveRegistered`: loga evento estruturado com tipoGestoId, nome, tenantRef, actor, at
- [ ] handler de `PrimitiveDeprecated`: invalida entrada de cache (Spring Cache ou ConcurrentHashMap simples) de eligibility para o tenant
- [ ] garantir que excecao no listener nao propaga para a transacao principal (try-catch com log.error no handler ou `@Async` com propagation NEVER)
- [ ] testes: listener e chamado apos commit; rollback nao dispara listener; excecao no listener nao afeta retorno do endpoint

## Resultado esperado
Apos commit de `register()` ou `transition()`, os listeners sao invocados fora da transacao original; excecao em listener nao desfaz o registro.

## Criterios de aceite
- [ ] listener NAO e invocado se a transacao de `register()` for revertida (rollback)
- [ ] listener E invocado apos commit bem-sucedido de `register()` (verificado com `@SpyBean`)
- [ ] excecao lancada dentro do listener nao propaga para o caller de `register()` (response 201 mesmo com listener falhando)
- [ ] log estruturado contem `tipoGestoId` e `tenantRef` (sem dados biometricos)

## Dependencias
GEST-006, GEST-007 (produtores de eventos).

## Estimativa
S

## Prioridade
P6
