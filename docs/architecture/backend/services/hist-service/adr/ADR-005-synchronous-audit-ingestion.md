<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-005 — Synchronous audit ingestion with producer-side durability (broker-deferred)

## Status
aceita

## Contexto
HIST must capture audit facts from four producers (EVNT, PAC, ACS, EDGE). The natural fit for
fire-and-forget audit fan-out is an event bus, but the project runs with `stack.messaging.enabled = false`:
the MVP has no broker (project-context justifies this by the pilot's tiny scale — one patient — and
edge→backend sync being REST/HTTPS with a local retry outbox). The tension: an audit ledger must not lose
facts, yet synchronous coupling risks a producer failing its primary operation, or dropping the audit fact,
when HIST is momentarily down. A missing audit fact is the worst outcome for this service.

## Decisão
Vamos manter a ingestão **síncrona via REST** no MVP (produtor → `POST /audit-facts` roteado pelo GWY,
descoberto via REG), consistente com ADR-002 (microsserviços independentes) e com o padrão do projeto.
Durabilidade contra indisponibilidade transitória de HIST é responsabilidade do **outbox/fila de retry do
produtor** — exatamente o padrão já adotado na sincronização edge→backend — e o replay é seguro porque a
ingestão é **idempotente por `factId`** (um fato → no máximo um registro). A chamada de auditoria é
desacoplada do sucesso da operação primária do produtor apenas na direção segura: a operação primária pode
concluir e o fato de auditoria ser reenviado depois, mas nunca descartado. O **barramento assíncrono fica
explicitamente adiado**: se o volume/fan-out crescer (múltiplos consumidores, assinatura entre serviços),
a evolução natural é produtores publicarem eventos de domínio e HIST assinar — reavaliado sob o mesmo
gatilho que introduziria mensageria no projeto todo.

## Consequências
**Positivas:** zero infraestrutura nova no MVP (sem broker a operar); caminho de escrita simples e
testável; idempotência por `factId` torna o replay exato-uma-vez; coerente com a topologia e o rigor de
custo do piloto. **Negativas / trade-offs:** a garantia de não-perda depende de cada produtor implementar
corretamente outbox+retry (risco R1, doc 06) — HIST não pode forçá-lo, só contratá-lo nas Specs de cada
produtor; acoplamento de latência na borda (produtor espera o `201` ou enfileira); quando um segundo
consumidor de auditoria surgir, a ausência de bus fará HIST virar ponto de *pull*, não *push*. Aceito
porque respeita `messaging.enabled=false`, evita infra prematura e a idempotência preserva a integridade do
ledger sob retry.
