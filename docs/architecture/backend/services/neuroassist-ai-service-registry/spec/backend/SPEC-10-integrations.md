# SPEC-10 — Integrations

## Objetivo
Especificar as integrações de REG: clientes Eureka in-fleet (inbound) e o pipeline
de observabilidade (outbound). REG não tem integração externa de negócio.

## Escopo
**In:** clientes Eureka (todos os serviços + GWY) registrando/descobrindo;
observabilidade (logs + métricas Actuator).
**Out:** SaaS/PSP/AI/datastore/messaging — todos ausentes (doc 04); JWKS (REG não
faz auth).

## Decisões técnicas
- REG é o **alvo de dependência** da plataforma: clientes chamam REG; REG não chama
  ninguém de volta (`contracts/hub-integration.md`).
- Plano de registro/discovery é interno (network trust), sem credencial externa.
- Logs/métricas best-effort; degradação da observabilidade não bloqueia o serviço.

## Implementação
- Inbound: protocolo Eureka (registro/renovação/de-registro/consulta) de cada
  cliente in-fleet (GWY, MIG, GEST, EVNT, PAC, ACS, HIST, DASH, EDGE).
- Outbound: logs estruturados de ciclo de vida + métricas Actuator ao pipeline de
  observabilidade da plataforma.
- Resiliência: self-preservation segura a membership em blip; serviço continua
  servindo discovery mesmo com observabilidade degradada.

## Casos de borda
- Cliente brevemente sem alcançar REG → roteia pelo registry cacheado do próprio
  cliente; self-preservation evita flush em REG.
- Pipeline de observabilidade indisponível → REG segue aceitando registros e
  respondendo discovery.

## Critérios de aceite (EARS)
`THE SYSTEM SHALL integrate only with in-fleet Eureka clients (inbound) and the platform observability pipeline (outbound).`
`THE SYSTEM SHALL accept registration and discovery without an external credential (network-trusted plane).`
`THE SYSTEM SHALL NOT integrate with any external SaaS, PSP, AI, datastore, or messaging system.`
`IF the observability pipeline is unavailable, THEN THE SYSTEM SHALL continue serving registration and discovery.`
