# EDGE-002 -- Define DispositivoEdge JPA aggregate root with pairing state machine and exclusive-pairing partial UNIQUE index

**Status:** PARTIAL
**Maturidade:** 85 -- Maduro
**Done/Total:** 4/5 itens

## Contexto
O agregado raiz `DispositivoEdge` representa a identidade backend de um agente edge. Sua maquina de estados de pareamento (registered, paired, unpaired, retired) e o indice parcial UNIQUE `ux_dispositivo_edge_active_pairing` sao os guardas primarios do invariante de pareamento exclusivo (ADR-005, invariante #1). `status` (online/offline) nao e coluna — e derivado em leitura (ADR-006).

## Spec de referencia
- SPEC-03, SPEC-04

## Escopo
- [x] criar `domain/model/DispositivoEdge.java` com todos os campos do data-model §2.1 (id, tenantId, label, pacienteId, pairingState, versao, ultimaSincronizacao, runningProfileVersion, rowVersion, timestamps, auditoria)
- [x] criar enum `domain/model/PairingState.java` com valores: registered, paired, unpaired, retired
- [x] anotar com JPA (`@Entity @Table`, `@Id`, `@Version` para row_version, `@Column` com constraints); usar `@Check` para `ck_dispositivo_edge_state` e `ck_dispositivo_edge_paired_has_patient`
- [x] criar `domain/port/DispositivoEdgeRepositoryPort.java` com: save, findById, findByIdAndTenantId, findActivePairingForPatient, findByPatient
- [ ] incluir metodo de dominio `validateTransition(PairingState target)` que lanca excecao para transicoes invalidas (apenas: registered|unpaired -> paired; paired -> unpaired; any -> retired)

## Resultado esperado
Entidade JPA `DispositivoEdge` compilavel com `PairingState` enum e `DispositivoEdgeRepositoryPort` interface no dominio.

## Criterios de aceite
- [ ] `compileJava` exit 0 com a entidade
- [ ] anotacao `@Version` presente em `rowVersion` para optimistic locking (SPEC-05)
- [ ] constraint `ck_dispositivo_edge_state` mapeada via `@Check` ou equivalente
- [ ] constraint de pareamento exclusivo (`pacienteId IS NOT NULL` quando `paired`) modelada no agregado
- [ ] nenhuma coluna `status` na entidade — campo nao existe no modelo (status e derivado)

## Dependencias
- EDGE-001 (precede)

## Estimativa
M

## Prioridade
P1
