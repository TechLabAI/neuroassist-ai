# SPEC-08 — BDD Test Scenarios

## Objetivo
Descrever a estratégia BDD de REG, mapear as features Gherkin para SPECs e listar
steps reutilizáveis. Os cenários executáveis vivem em `tests/cucumber/*.feature`
(Gherkin puro); este documento é a narrativa e a cola entre features e specs.

## Features Gherkin (em `tests/cucumber/`)

| Feature | Cobre | SPEC fonte |
|---|---|---|
| `service-registration.feature` | registro, renovação de lease, idempotência, de-registro, status não-UP | SPEC-02, SPEC-05, SPEC-01 |
| `eviction-and-self-preservation.feature` | eviction de stale, self-preservation, recuperação, não esvaziar em blip | SPEC-02, SPEC-03 |
| `discovery-resolution.feature` | resolução lb://, só UP, scale-up sem reconfig, ids por serviço | SPEC-02, SPEC-07 |

## Steps reutilizáveis (catálogo)

- `Given the neuroassist-ai-service-registry (REG) is running as a single Eureka node`
- `Given an instance of "<service>" is registered and UP`
- `When the instance renews its lease within the lease expiration window`
- `When the instance stops renewing its lease beyond the expiration window`
- `When the eviction loop runs while self-preservation is not active`
- `When a consumer queries discovery for "<service>"`
- `Then the registry advertises that instance as UP`
- `Then the registry removes the instance from discovery`
- `Then only the UP instance is returned`

## Dados de exemplo

- Logical ids estáveis usados nos cenários: `GEST-SERVICE`, `EVNT-SERVICE`,
  `PAC-SERVICE`, `HIST-SERVICE`, `NEUROASSIST-AI-API-GATEWAY`.
- Janela de lease de referência: renovação 30s / expiração 90s (defaults Eureka).

## Critérios de aceite (EARS)
`THE SYSTEM SHALL have at least one executable Gherkin scenario for registration, eviction, and discovery.`
`THE SYSTEM SHALL keep every executable scenario in a .feature file (pure Gherkin), with prose narrative confined to this SPEC.`
`THE SYSTEM SHALL map each feature file to the SPEC that authorizes its invariants.`
