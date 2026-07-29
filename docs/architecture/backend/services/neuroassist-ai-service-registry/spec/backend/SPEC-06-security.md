# SPEC-06 — Security

## Objetivo
Especificar a postura de segurança de REG: superfície interna, sem decisões de
authn/authz, e proteção do plano de registro/discovery.

## Escopo
**In:** plano de registro/discovery como interno à frota (network trust); exposição
não-pública; ausência de decisão de auth; hardening do dashboard.
**Out:** validação de token de borda (GWY, ADR-005 lá); autorização de domínio (cada
serviço); JWKS (REG não consome).

## Decisões técnicas
- **REG não autentica nem autoriza** (doc 01, `_brief.md` §5). Edge auth (caregiver
  JWT / device token) é do GWY; authz de domínio fica nos serviços.
- O plano Eureka é **platform-internal**: no deploy, só serviços in-fleet e o
  gateway alcançam REG; REG **não** é exposto no edge público
  (`02-architecture-blueprint.md` §Cross-cutting Concerns).
- O dashboard Eureka deve ficar restrito à rede interna / operadores, não público —
  hardening pré-produção listado em `06-risks-gaps-assumptions.md` §Riscos
  ("Unauthenticated dashboard").

## Implementação
- Topologia de rede: REG atrás do perímetro interno; sem rota pública via GWY para
  o protocolo Eureka.
- Dashboard e Actuator restritos por rede/credencial de plataforma conforme padrão
  de infraestrutura do projeto.
- Sem manuseio de JWT/token de dispositivo, sem leitura de `X-Empresa-Id`.

## Casos de borda
- Tentativa de acesso ao protocolo Eureka a partir do edge público → bloqueada por
  topologia de rede (REG não é roteado publicamente pelo GWY).
- Operador acessa o dashboard → via rede interna/credencial de plataforma.

## Critérios de aceite (EARS)
`THE SYSTEM SHALL NOT validate caregiver JWT or edge device token.`
`THE SYSTEM SHALL NOT make any authorization decision.`
`THE SYSTEM SHALL treat the registration/discovery plane as platform-internal and SHALL NOT expose it at the public edge.`
`THE SYSTEM SHALL restrict the Eureka dashboard to internal operators, not the public internet.`
`THE SYSTEM SHALL NOT read, derive, or propagate any tenant (empresa) context.`
