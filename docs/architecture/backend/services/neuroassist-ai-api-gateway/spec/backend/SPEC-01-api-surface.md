# SPEC-01 — API / Route Surface

> EARS notation per `../../../standards/ears-notation.md`. See `contracts/api-contract.md`
> for the full route table, headers and error model.

## Objetivo
Definir a superfície pública do gateway: o conjunto de rotas proxied para os serviços de
domínio e os endpoints operacionais próprios (actuator). O gateway não expõe API de negócio
própria (ADR-002).

## Escopo
- **In:** tabela de rotas (`lb://<service>`), predicados de path/método, política de auth
  por rota, buckets de rate-limit por rota, endpoints `/actuator/*`, modelo de erro RFC 7807.
- **Out:** transformação de corpo, caching, versionamento de payload de negócio, qualquer
  regra de domínio.

## Decisões técnicas
- Rotas declarativas via `RouteLocator` (config), nunca host fixo (INV-4, ADR-002).
- Prefixo externo `/api/<module>/**`; o gateway remove o prefixo antes de encaminhar.
- Endpoints operacionais em `/actuator/*`; apenas `health` é público.

## Implementação (contratos internos)
- `config/RouteConfig` expõe um `RouteLocator` bean; cada rota liga predicado → `lb://`
  target → conjunto ordenado de filtros (ver SPEC-02).
- Rotas e políticas externalizadas em `application.yml` (`gateway.routes[]`,
  `gateway.ratelimit.*`, `gateway.cors.*`).

## Casos de borda
- Path que casa nenhum predicado → 404 `/errors/no-route`.
- Método não permitido no predicado → 405 (ou 404 conforme configuração de predicado).
- `OPTIONS` de CORS respondido localmente (SPEC-06).

## Critérios de aceite (EARS)
- WHEN a request matches a route predicate and is authorized, THE SYSTEM SHALL forward it
  to a discovered instance of the target logical service id.
- IF no route predicate matches, THEN THE SYSTEM SHALL respond `404`
  `application/problem+json` without contacting any service.
- THE SYSTEM SHALL expose `GET /actuator/health` without authentication.
- THE SYSTEM SHALL NOT expose any business endpoint of its own.
