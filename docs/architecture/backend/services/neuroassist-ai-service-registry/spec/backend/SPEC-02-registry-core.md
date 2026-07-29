# SPEC-02 — Registry Core (Registration, Lease, Eviction, Discovery)

## Objetivo
Especificar o núcleo de REG: como instâncias se registram, mantêm o lease por
heartbeat, são evictadas quando param de renovar, e como consumidores resolvem um
logical id para instâncias saudáveis. Este é o comportamento central do serviço
(substantivo+verbo: **registrar/descobrir instâncias**).

## Escopo
**In:** ciclo de vida do lease (register → renew → evict / de-register), membership
health-aware, resolução de discovery, dashboard como leitura do estado.
**Out:** roteamento (GWY), postura standalone/fail-open detalhada (SPEC-03),
persistência (não há — doc 05).

## Decisões técnicas
- **Discover, never route:** REG responde "onde está o serviço X?" e nunca
  compõe/roteia chamada de negócio (`02-architecture-blueprint.md` §System Boundary).
- **Health-aware truth** (doc 01): só instâncias renovando lease e em `UP` são
  anunciadas.
- **Self-registration**: serviços se anunciam; consumidores nunca são
  reconfigurados quando uma instância escala/move.
- **In-memory de leases** (doc 05): membership é efêmero e reconstruído por
  re-registro no restart (`03-initial-domain-framing.md` §Invariantes #4).

## Implementação
- Comportamento provido pelo Eureka server core (registro, lease store, loop de
  eviction, self-preservation). O serviço é o runtime do servidor + bootstrap fino.
- Configuração declarativa (`application.yml`): intervalos de renovação/expiração,
  threshold de self-preservation, flags de single-node.
- O dashboard built-in lê o estado in-memory do registry.

## Casos de borda
- Re-registro do mesmo instanceId → lease único (idempotência, SPEC-05).
- Instância para de heartbeat → vira eviction-eligible após a janela de expiração;
  removida quando o loop roda e self-preservation não está ativo.
- Queda fleet-wide do renewal rate → self-preservation segura a membership (não
  esvazia o registry) até recuperar.
- Restart do REG → registry reconstruído pelos re-registros dos clientes.

## Critérios de aceite (EARS)
`WHEN a service instance registers under a logical id, THE SYSTEM SHALL record its lease and advertise it as UP.`
`WHEN a registered instance renews its lease within the expiration window, THE SYSTEM SHALL keep it advertised.`
`WHEN an instance de-registers gracefully, THE SYSTEM SHALL remove it from discovery immediately.`
`IF an instance stops renewing its lease beyond the expiration window, THEN THE SYSTEM SHALL make it eviction-eligible.`
`WHEN the eviction loop runs and self-preservation is not active, THE SYSTEM SHALL remove expired instances.`
`WHILE the fleet-wide renewal rate is below the self-preservation threshold, THE SYSTEM SHALL stop evicting and preserve the last known registry.`
`WHEN a consumer queries discovery for a logical id, THE SYSTEM SHALL return only instances currently UP.`
`WHEN a new or scaled instance registers, THE SYSTEM SHALL advertise it without requiring consumer reconfiguration.`
`WHEN the registry restarts, THE SYSTEM SHALL rebuild membership from client re-registration, not from a datastore.`
