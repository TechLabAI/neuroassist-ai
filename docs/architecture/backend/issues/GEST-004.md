# GEST-004 -- Define repository outbound port interfaces (TipoGestoRepositoryPort, TipoGestoTransitionRepositoryPort, DetectionProfileRepositoryPort, DetectionProfilePrimitiveRepositoryPort)

**Status:** EM ANDAMENTO -- reconciliado 2026-08-10
**Maturidade:** 2 -- Ports implementados; EdgePairingPort deferido para P4
**Done/Total:** 7/9 itens

## Contexto
A camada hexagonal exige que as application services dependam de ports (interfaces), nao de adapters concretos. Esta issue define os ports de saida do `gest-service`. NOTA DE RECONCILIACAO: os ports de persistencia estao em `application/port/out/` (nao `domain/port/` como descrito originalmente) — essa e a localizacao correta no padrao hexagonal do projeto (domain.* nao pode depender de nada de persistencia; application.* pode depender de port interfaces). O plano vigente (plan-GEST.md) reflete a localizacao correta. A issue original descrevia `domain/port/` incorretamente. O `EdgePairingPort` foi deferido para P4 (GEST-015); o stub `EdgePairingClient.java` existe em `infrastructure/clients/`.

## Spec de referencia
- SPEC-01, SPEC-03, SPEC-10, api-contract.md §4.4, integration-contracts.md §1

## Escopo
- [x] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/port/out/TipoGestoRepositoryPort.java` (metodos: save, findById, findAllByTenant, existsByNomeIgnoreCaseAndStateNotDeprecated) + `TipoGestoTransitionRepositoryPort.java` (metodos: save, findByTipoGestoId)
- [x] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/port/out/DetectionProfileRepositoryPort.java` (metodos: save, findActive, nextVersion, deactivateCurrent) + `DetectionProfilePrimitiveRepositoryPort.java` (metodos: saveAll, findByProfileId)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/port/out/EdgePairingPort.java` (metodo: verifyDevicePairing(deviceId) -> Optional<String> tenantRef) -- DEFERIDO para P4 (GEST-015); `EdgePairingClient.java` existe em `infrastructure/clients/` como stub @FeignClient
- [x] criar `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/port/out/package-info.java` descrevendo a politica hexagonal dos ports de saida
- [x] ArchUnit test que verifica que `application.*` nao importa `infrastructure.*` e que `domain.*` nao importa `infrastructure.*` -- implementado em `ArchitectureTest.java` (rule_application_not_depends_infra, RULE-01, RULE-02)

## Resultado esperado
Quatro interfaces compilaveis sem dependencia de Spring Data ou Feign; application services compilam dependendo apenas desses ports; ArchUnit confirma fronteira hexagonal.

## Criterios de aceite
- [x] `TipoGestoService` compila injetando apenas `TipoGestoRepositoryPort` e `TipoGestoTransitionRepositoryPort` (ambos de `com.neuroassist.gest.application.port.out`, nao Spring Data repository diretamente)
- [ ] `EdgePairingPort` nao importa nenhuma classe de `infrastructure.*`: PENDENTE -- EdgePairingPort nao foi implementado em P1 (deferido para P4 GEST-015)
- [x] ArchUnit rule verifica que `com.neuroassist.gest.domain..` nao importa `com.neuroassist.gest.infrastructure..` (RULE-01/02 em ArchitectureTest.java)
- [x] ArchUnit rule verifica que `com.neuroassist.gest.application..` nao importa `com.neuroassist.gest.infrastructure..` (rule_application_not_depends_infra em ArchitectureTest.java)

## Dependencias
GEST-001, GEST-002, GEST-003 (precede).

## Estimativa
S

## Prioridade
P1
