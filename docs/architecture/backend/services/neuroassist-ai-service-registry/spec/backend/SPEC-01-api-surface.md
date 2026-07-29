# SPEC-01 — API Surface (Registry Surface)

## Objetivo
Definir a superfície que REG expõe: o protocolo do servidor Eureka (registro,
renovação de lease, de-registro, consulta de discovery), o dashboard e os
endpoints de health/readiness/metrics. REG **não** expõe recurso de negócio.

## Escopo
**In:** o protocolo Eureka como contrato comportamental; dashboard; Actuator
(health/readiness/metrics).
**Out:** roteamento de tráfego de negócio (GWY), autenticação/autorização, qualquer
recurso REST de domínio.

## Decisões técnicas
- A superfície é o protocolo padrão do `spring-cloud-starter-netflix-eureka-server`
  (ADR-001, local) — não se escreve controller de negócio.
- Health/readiness/metrics ficam fora da superfície do protocolo Eureka (SPEC-07).
- Single node MVP: `register-with-eureka=false`, `fetch-registry=false` (ADR-002).

## Implementação
- `@EnableEurekaServer` no bootstrap; comportamento vem do starter + `application.yml`
  (`02-architecture-blueprint.md` §Internal Boundaries).
- Protocolo: registro (`POST /eureka/apps/{app}`), renovação
  (`PUT /eureka/apps/{app}/{instanceId}`), de-registro (`DELETE ...`), consulta
  (`GET /eureka/apps[/{app}]`), override de status (`PUT .../status`).
- Dashboard built-in em `/`; Actuator em `/actuator/**`.

## Casos de borda
- Instância registra com status não-UP → não é anunciada para discovery.
- Consulta a um logical id sem instância UP → resultado vazio para aquele id.
- Re-registro do mesmo instanceId → não duplica lease (SPEC-05).

## Critérios de aceite (EARS)
`THE SYSTEM SHALL expose the standard Eureka server registration and discovery protocol.`
`THE SYSTEM SHALL NOT author any business REST resource.`
`THE SYSTEM SHALL NOT proxy or route business API traffic.`
`WHEN a discovery query arrives for a logical id, THE SYSTEM SHALL return only instances currently advertised as UP.`
`THE SYSTEM SHALL expose liveness, readiness, and metrics endpoints separate from the Eureka protocol surface.`
`THE SYSTEM SHALL run as a single registry node for the MVP (register-with-eureka=false, fetch-registry=false).`
