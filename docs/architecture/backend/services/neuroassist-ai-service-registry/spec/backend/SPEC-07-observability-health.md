# SPEC-07 — Observability and Health

## Objetivo
Especificar logs de ciclo de vida, métricas do registry e endpoints de
health/readiness de REG.

## Escopo
**In:** logs de registro/renovação/eviction para o pipeline de observabilidade da
plataforma; métricas (apps registrados, renewals/min, evictions, self-preservation
ativo); liveness/readiness.
**Out:** business analytics (fora do escopo de REG), eventos de broker (N/A,
SPEC-09), trace de requisição de negócio (REG não roteia tráfego de negócio).

## Decisões técnicas
- Observabilidade desde o dia 1: REG envia seus logs de ciclo de vida ao pipeline
  de observabilidade da plataforma (`02-architecture-blueprint.md` §Cross-cutting
  Concerns — "standard structured logs").
- Readiness reflete o registry estar aceitando registros; liveness reflete o
  servidor no ar.
- Métricas do Eureka expostas via Actuator/Prometheus.
- Nenhum dado pessoal de paciente/cuidador transita por REG (`03-initial-domain-
  framing.md` §Invariantes #5) — logs carregam apenas metadados operacionais
  (nomes de serviço, host, porta, health URL, timestamps de lease).

## Implementação
- Logs estruturados em registro/renovação/eviction/self-preservation, com o
  logical id e instanceId afetados, enviados best-effort ao pipeline de logging.
- Actuator: `/actuator/health` (liveness), `/actuator/health/readiness`
  (readiness), `/actuator/prometheus` (métricas) — fora da superfície do protocolo
  Eureka.
- Métricas-chave: número de apps/instâncias, renewals por minuto, evictions, flag
  de self-preservation.

## Casos de borda
- Pipeline de observabilidade brevemente indisponível → logging best-effort; nunca
  bloqueia registro/discovery.
- Self-preservation ativo → exposto como métrica/sinal para o operador.
- Readiness `false` enquanto o registry ainda não aceita registros → instância não
  considerada pronta.

## Critérios de aceite (EARS)
`THE SYSTEM SHALL emit a structured lifecycle log for each registration, renewal, and eviction to the platform observability pipeline.`
`THE SYSTEM SHALL expose metrics for registered apps/instances, renewal rate, evictions, and self-preservation state.`
`THE SYSTEM SHALL expose liveness, readiness, and metrics endpoints separate from the Eureka protocol surface.`
`WHILE the registry is not yet accepting registrations, THE SYSTEM SHALL report readiness as not-ready.`
`IF the observability pipeline is unavailable, THEN THE SYSTEM SHALL continue accepting registrations and answering discovery (logging is best-effort).`
`THE SYSTEM SHALL NOT log any patient or caregiver personal data — only operational metadata (service name, host, port, health URL, lease timestamps).`
