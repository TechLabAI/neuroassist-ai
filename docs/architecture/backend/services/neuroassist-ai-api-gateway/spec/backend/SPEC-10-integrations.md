# SPEC-10 — Integrations

> External and platform integrations with auth, SLA and fallback. Mirrors
> `contracts/integration-contracts.md` + `contracts/hub-integration.md` as testable
> criteria. EARS per standard.

## Objetivo
Especificar as integrações do gateway (REG/Eureka, ACS JWK, serviços downstream, LB) e o
comportamento em falha de cada uma.

## Escopo
- **In:** discovery via Eureka, validação JWK via ACS, roteamento para serviços downstream,
  probe de saúde pelo LB, esquema de auth de dispositivo (ponte para SPEC-03).
- **Out:** TechLab AI (nenhum — `ai.hasAi=false`); mensageria (SPEC-09).

## Integrações
| # | Sistema | Protocolo | Auth | Fallback |
|---|---|---|---|---|
| 1 | Service Registry (Eureka) | Eureka client | rede interna | cache local; readiness DOWN se vazio → 503 |
| 2 | ACS JWK / OIDC | HTTPS GET | público (JWK) | cache TTL + stale-while-revalidate; 401 se cache frio + endpoint down |
| 3 | Device auth authority (EDGE/ACS) | JWK (proposta, ADR-005) | assinatura | filtro swappable (G-1) |
| 4 | Downstream (EVNT/PAC/ACS/HIST/DASH/EDGE) | HTTP via `lb://` | contexto propagado | timeouts + 502/503/504; retry só GET |
| 5 | LB externo / DNS | HTTP probe | n/a | health `/actuator/health` público |

## Decisões técnicas
- TechLab AI: nenhuma chamada (hub-integration §1).
- Circuit breaker (Resilience4j) é caminho de upgrade documentado; MVP = fail-fast tipado.

## Casos de borda
- ACS JWK indisponível com cache válido → segue validando (R-4).
- Nenhuma instância saudável de um target → 503.
- Upstream 5xx → 502 sem vazar corpo.

## Critérios de aceite (EARS)
- THE SYSTEM SHALL register with and resolve routes exclusively through the Service
  Registry (INV-4).
- THE SYSTEM SHALL validate caregiver JWTs against ACS's JWK set and tolerate a briefly
  unreachable JWK endpoint while a cached key is still valid (R-4).
- IF a target service has no healthy registered instance, THEN THE SYSTEM SHALL respond
  `503 /errors/upstream-unavailable`.
- WHEN an upstream returns an unhandled `5xx`, THE SYSTEM SHALL map it to `502
  /errors/upstream-error` without leaking the upstream body.
- THE SYSTEM SHALL NOT invoke any TechLab AI capability.
