# HIST-017 -- Micrometer metrics: contadores e timers do ledger

## Contexto

Métricas operacionais do HIST para Prometheus/Grafana. Definidas em SPEC-07, elas
permitem alertar sobre picos de ingestão, duplicatas ignoradas, falhas de verificação
e degradação do ACS. Implementadas via Micrometer (`MeterRegistry`) sem dependência de
exportador específico (o registry de Prometheus é adicionado pela dependency já na build).

## Spec de referencia

- SPEC-07 (Observability & Health — lista de métricas, §Critérios de aceite)

## Escopo

**`com.neuroassist.hist.infrastructure.metrics.HistMetrics` (`@Component`):**
- `hist_audit_fact_recorded_total{factType}` — Counter: incrementado no append bem-sucedido (201).
- `hist_duplicate_fact_ignored_total` — Counter: incrementado em idempotência (200).
- `hist_correction_recorded_total` — Counter: incrementado quando `correctsFactId != null`.
- `hist_chain_verification_total{status}` — Counter{intact, broken, empty}: incrementado ao final de cada verificação.
- `hist_ingestion_latency_seconds` — Timer: latência do append completo (do início ao commit).
- `hist_acs_scope_resolution_total{outcome}` — Counter{success, timeout, circuit_open}: incrementado em cada chamada ao ACS.
- Registrar todos os meters no construtor via `meterRegistry.counter(...)`.
- Injetar `HistMetrics` em `RecordAuditFactService` (HIST-006), `ChainVerificationService` (HIST-008) e `ActorDirectoryAdapter` (HIST-012).

## Resultado esperado

`/actuator/prometheus` expõe todos os 6 meters. Testes de integração verificam incremento
dos contadores após append, duplicate e verify.

## Criterios de aceite

- [ ] `hist_audit_fact_recorded_total` incrementa após append 201 bem-sucedido com `factType` correto.
- [ ] `hist_duplicate_fact_ignored_total` incrementa após replay de `factId` (200 idempotente).
- [ ] `hist_chain_verification_total{status=intact}` incrementa após verify intacto.
- [ ] `hist_ingestion_latency_seconds` registra tempo do append.
- [ ] `hist_acs_scope_resolution_total{outcome=success}` incrementa após ACS retornar scope com sucesso.
- [ ] `hist_acs_scope_resolution_total{outcome=timeout}` incrementa após timeout do ACS.
- [ ] `GET /actuator/prometheus` retorna 200 com os meters declarados.

## Dependencias

- HIST-006 (RecordAuditFactService — ponto de incremento de fact_recorded e duplicate)
- HIST-008 (ChainVerificationService — verification counter)
- HIST-012 (ActorDirectoryAdapter — acs_scope_resolution counter)
- HIST-001 (micrometer-registry-prometheus já na build)

## Estimativa

S

## Prioridade

P7
