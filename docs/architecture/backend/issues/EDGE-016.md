# EDGE-016 -- Implement Spring TransactionalEventListener wiring for all 7 in-process domain events (post-commit, DispositivoRegistrado .. ProfileDriftDetected)

**Status:** INTACT -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
Os 7 eventos de dominio (event-contracts.md §2) sao in-process (Spring ApplicationEventPublisher, sem broker). Os listeners devem ser `@TransactionalEventListener(phase=AFTER_COMMIT)` para nao observar estado nao-commitado. Falha no listener nao propaga para a transacao original. Os eventos sao: DispositivoRegistrado, Pareado, Despareado, HeartbeatRecebido, FicouOffline, VoltouOnline, ProfileDriftDetected.

## Spec de referencia
- SPEC-09

## Escopo
- [ ] criar classes de evento em `domain/event/`: DispositivoRegistrado, DispositivoPareado, DispositivoDespareado, HeartbeatRecebido, DispositivoFicouOffline, DispositivoVoltouOnline, ProfileDriftDetected (com payloads logicos do event-contracts §2.1)
- [ ] criar `application/listener/DeviceAuditEventListener.java` com `@TransactionalEventListener(phase=AFTER_COMMIT)` para cada evento (log estruturado + emissao de metrica via EdgeMetrics)
- [ ] garantir que falha no listener NAO propaga para a transacao original (try-catch no listener ou `@Async` com pool isolado)
- [ ] criar teste unitario: evento publicado pos-commit, listener acionado; rollback da transacao principal nao aciona listener

## Resultado esperado
Cada operacao de dominio (register, pair, heartbeat, etc.) publica o evento correspondente apos commit; listeners registram no log estruturado sem propagar excecoes.

## Criterios de aceite
- [ ] `@TransactionalEventListener(phase=AFTER_COMMIT)` presente em todos os listeners
- [ ] Rollback da transacao principal nao aciona listeners
- [ ] Falha no listener nao causa rollback ou excecao na requisicao original
- [ ] Todos os 7 tipos de evento tem classe correspondente em `domain/event`

## Dependencias
- EDGE-008 (precede)

## Estimativa
S

## Prioridade
P6
