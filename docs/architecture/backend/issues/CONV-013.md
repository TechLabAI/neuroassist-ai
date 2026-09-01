# CONV-013 -- Criar HistAuditAdapter — Feign para hist-service (best-effort, falha → insere no outbox)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

Implementa `HistAuditPort` como adapter Feign para `hist-service`. Auditoria é **best-effort,
não-bloqueante** — qualquer falha de infra lança `HistUnavailableException`, que `NodeSelectionService`
captura e persiste no `conv_audit_outbox` (SPEC-09). O adapter envia os 4 tipos de fato definidos
em `event-contracts.md`: `SpeechNodeSelected`, `PhraseMatched`, `TreeNavigated`,
`ConvServedDegraded`. A autenticação de serviço para HIST é via `client_credentials` padrão do
monorepo.

## Spec de referencia

- SPEC-09 (messaging-architecture — best-effort, outbox)
- `contracts/event-contracts.md` §2
- `contracts/hub-integration.md` §4

## Escopo

- [ ] Criar `infrastructure/integration/hist/HistAuditFeignClient.java` (`@FeignClient(name="hist-service", url="${neuroassist.hist.url}")`) com operação `send(AuditFactDto fact): void` (POST `/hist/audit/ingest`)
- [ ] Criar `infrastructure/integration/hist/HistAuditAdapter.java` implementando `HistAuditPort` — chama `HistAuditFeignClient.send`; captura qualquer `FeignException`/timeout → relança como `HistUnavailableException`
- [ ] Criar `infrastructure/integration/hist/AuditFactDto.java` com discriminador `tipo` (String) e payload dinâmico (`Map<String,Object>` ou `JsonNode`) — reflete os 4 tipos de fato sem criar 4 Feign calls
- [ ] Configurar `neuroassist.hist.url` stub em `application-test.yml` (Learning D-533)
- [ ] Criar `WireMockHistStub.java` para testes BDD: responde `200` para `/hist/audit/ingest`; simulação de timeout para testar outbox

## Resultado esperado

`HistAuditPort.send` funcional; falha HIST → `HistUnavailableException` propagada para que o chamador persista no outbox.

## Criterios de aceite

- [ ] `./gradlew :conv-service:test` BUILD SUCCESS
- [ ] Teste unitário: WireMock responde `200` → `send()` retorna sem exceção
- [ ] Teste unitário: WireMock responde `503` → `HistUnavailableException` lançada
- [ ] Teste unitário: WireMock timeout → `HistUnavailableException` lançada (não `FeignException` raw)
- [ ] `AuditFactDto` carrega `correlationId` em todos os fatos

## Dependencias

- CONV-004 (HistAuditPort)
- CONV-009 (AuditOutbox persistence — o chamador usa o adapter de outbox)

## Estimativa

M

## Prioridade

P4
