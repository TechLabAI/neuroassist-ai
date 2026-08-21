# HIST-019 -- Actuator health + readiness endpoints

## Contexto

`/actuator/health` (liveness) e `/actuator/health/readiness` (readiness com DB + ACS JWK).
ACS directory indisponível **não** derruba readiness — só afeta leituras de cuidadora
(SPEC-07). DB indisponível → `DOWN`.

## Spec de referencia

- SPEC-07 (Observability & Health — health e readiness, critérios de aceite)

## Escopo

**`application.yml`:**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,info
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
```

**`com.neuroassist.hist.infrastructure.health`:**
- `DatabaseHealthIndicator` (Spring padrão via `spring-boot-starter-data-jpa` — automático).
- `AcsJwkHealthIndicator` (`@Component HealthIndicator`): verifica que o JWK set URI do ACS é acessível (HEAD request ou cached state). Falha → `DOWN` apenas neste indicador, não derruba readiness total. Status do indicador não deve ser incluído em readiness probe (separar em grupo `liveness`).
- Configuração de grupos: `management.endpoint.health.group.readiness.include=db` (DB obrigatório); ACS JWK separado.

## Resultado esperado

`GET /actuator/health` retorna `200 {"status":"UP"}`. Com DB down: `503 {"status":"DOWN"}`.
Com ACS JWK inacessível: `200 {"status":"UP"}` (não afeta readiness).

## Criterios de aceite

- [ ] `GET /actuator/health` retorna `200 {"status":"UP"}` quando DB acessível.
- [ ] `GET /actuator/health/readiness` retorna `503` quando DB está indisponível.
- [ ] `GET /actuator/health/readiness` retorna `200` quando ACS JWK está inacessível (não obrigatório para readiness).
- [ ] `/actuator/prometheus` exposto (não requer auth, proteção via GWY).
- [ ] `/actuator/health` não expõe stack trace nem SQL.

## Dependencias

- HIST-001 (spring-boot-starter-actuator na build)
- HIST-011 (datasource precisa funcionar para health check)

## Estimativa

S

## Prioridade

P7
