# SPEC-05 — Idempotency and Concurrency

## Objetivo
Especificar a idempotência do protocolo de registro/heartbeat e a concorrência do
lease store in-memory de REG.

## Escopo
**In:** registro idempotente (re-registro do mesmo instanceId não duplica lease),
renovação idempotente, concorrência entre o loop de eviction e renovações
concorrentes.
**Out:** idempotência de eventos (N/A — REG não é participante de broker, SPEC-09),
idempotência de requisições de negócio (não há).

## Decisões técnicas
- O lease store é chaveado por `(app, instanceId)` — registro/renovação são
  operações idempotentes sobre essa chave (comportamento do Eureka core;
  `03-initial-domain-framing.md` §Invariantes #2-3).
- Renovação concorrente com o loop de eviction: uma renovação válida dentro da
  janela impede a eviction daquela instância.
- Sem persistência ⇒ não há concorrência de transação de banco; a consistência é a
  do store in-memory do servidor.

## Implementação
- Provido pelo Eureka server core: idempotência por chave de instância e timers
  internos de lease.
- Configuração coerente de intervalos (renovação < expiração) para evitar eviction
  de instâncias saudáveis sob carga.

## Casos de borda
- Re-registro do mesmo instanceId → atualiza o lease existente, não cria segundo.
- Renovação chegando no exato momento da eviction → renovação válida prevalece;
  instância permanece UP.
- Duas instâncias distintas do mesmo app (ex.: `dash-service` escalado) → dois
  leases independentes (não é duplicação).

## Critérios de aceite (EARS)
`WHEN the same instanceId registers more than once, THE SYSTEM SHALL hold exactly one lease for that instanceId.`
`WHEN a renewal arrives for a registered instance, THE SYSTEM SHALL refresh its existing lease without creating a new one.`
`IF a valid renewal occurs within the expiration window, THEN THE SYSTEM SHALL NOT evict that instance in the concurrent eviction cycle.`
`THE SYSTEM SHALL treat distinct instanceIds of the same logical id as independent leases.`
`THE SYSTEM SHALL NOT rely on a relational transaction for lease consistency (in-memory store only).`
