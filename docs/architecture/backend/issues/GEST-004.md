# GEST-004 -- Define repository and outbound port interfaces (TipoGestoRepositoryPort, DetectionProfileRepositoryPort, EdgePairingPort)

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto
A camada hexagonal exige que as application services dependam de ports (interfaces), nao de adapters concretos. Esta issue define os tres ports de saida do `gest-service`: dois de persistencia e um de integracao externa (EDGE). Eles ficam em `domain/port/` para que a camada de dominio nao importe nada de `infrastructure`.

## Spec de referencia
- SPEC-01, SPEC-03, SPEC-10, api-contract.md §4.4, integration-contracts.md §1

## Escopo
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/port/TipoGestoRepositoryPort.java` (metodos: findById, findAllByTenant(state?), save, existsByNomeActiveInTenant, appendTransition)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/port/DetectionProfileRepositoryPort.java` (metodos: save, findActiveByTenant, findByVersion, nextVersion, deactivateAllExcept)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/port/EdgePairingPort.java` (metodo: verifyDevicePairing(deviceId) -> Optional<String> tenantRef)
- [ ] criar `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/port/package-info.java` descrevendo a politica hexagonal
- [ ] ArchUnit test que assert que `application.*` e `domain.*` nao importam `infrastructure.*`

## Resultado esperado
Tres interfaces compileveis sem dependencia de Spring Data ou Feign; application services compilam dependendo apenas desses ports.

## Criterios de aceite
- [ ] `TipoGestoService` (GEST-006/007) compila injetando apenas `TipoGestoRepositoryPort` (nao Spring Data repository)
- [ ] `EdgePairingPort` nao importa nenhuma classe de `infrastructure.*` na sua definicao
- [ ] ArchUnit rule verifica que `com.neuroassist.gest.domain..` nao importa `com.neuroassist.gest.infrastructure..`
- [ ] ArchUnit rule verifica que `com.neuroassist.gest.application..` nao importa `com.neuroassist.gest.infrastructure..`

## Dependencias
GEST-001, GEST-002, GEST-003 (precede).

## Estimativa
S

## Prioridade
P1
