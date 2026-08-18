# DASH-002 -- Define hexagonal outbound ports for all upstream integrations

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto

O `dash-service` consome 5 servicos donos via REST: ACS (identidade + escopo), PAC (catalogo de
comandos + calibracao), EVNT (estado de eventos + callbacks de entrega), HIST (historico auditavel)
e EDGE (liveness de dispositivo). Cada integracao e isolada atras de um Port hexagonal no layer
`domain`, garantindo que nenhuma dependencia HTTP vaze para `application` ou `domain`.

## Spec de referencia

- SPEC-10 (integracoes upstream)
- SPEC-01 (superfície REST: write-throughs e reads)
- `contracts/integration-contracts.md §7` (Port <-> upstream map)

## Escopo

- [ ] `domain/port/IdentityDirectoryPort` — resolucao de escopo caregiver<->paciente e validacao JWT (interface)
- [ ] `domain/port/CommandCatalogPort` — leitura de catalogo PAC + write-through de calibracao (interface)
- [ ] `domain/port/EventStatePort` — leitura de eventos/notificacoes EVNT + write-through de confirmacao (interface)
- [ ] `domain/port/NotificationDispatchBackPort` — callbacks `delivered(dispatchId)` e `acknowledged(dispatchId)` para EVNT (interface)
- [ ] `domain/port/HistoryQueryPort` — consulta paginada de timeline auditavel no HIST (interface)
- [ ] `domain/port/DeviceLivenessPort` — poll condicional (ETag/304) de liveness no EDGE (interface)

## Resultado esperado

Seis interfaces em `com.neuroassist.dash.domain.port`, sem dependencias de HTTP no layer de dominio.
Cada interface define metodos com tipos de dominio (nao DTOs de HTTP).

## Criterios de aceite

- [ ] Seis interfaces presentes em `com.neuroassist.dash.domain.port`
- [ ] Nenhuma importacao `org.springframework.web.*` ou `feign.*` nas interfaces de dominio
- [ ] Cada interface possui Javadoc referenciando o upstream e o tipo de contrato
- [ ] `./gradlew :dash-service:compileJava` sem erros

## Dependencias

- DASH-001 (estrutura de pacotes)

## Estimativa

M

## Prioridade

P1
