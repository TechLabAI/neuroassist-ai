# HIST-004 -- Outbound ports (repository + infrastructure interfaces)

## Contexto

Portas outbound isolam a aplicação de JPA, SHA-256 e Feign. São interfaces puras
consumidas pelos services de aplicação e implementadas pela camada de infraestrutura.
A postura de imutabilidade (ADR-004) é refletida aqui: `AuditLedgerPort` expõe apenas
`insert`/`findById`/`findByFactId` — sem update/delete.

## Spec de referencia

- SPEC-02 (fluxo append: portas usadas em sequência)
- SPEC-05 (IdempotencyPort + StreamHeadPort para serialização de tip)
- SPEC-06 (ActorDirectoryPort — fail-closed)
- ADR-004 (write-once — sem mutação nos ports)
- `generator-config.yml` — `outbound_ports`

## Escopo

**Pacote `com.neuroassist.hist.application.port.outbound`:**
- `AuditLedgerPort` (interface): `fun insert(record: AuditRecord): AuditRecord`, `fun findById(id: UUID): AuditRecord?`, `fun findPageByFilter(filter: TimelineFilter, page: PageRequest): PagedResult<AuditRecord>`. **Sem** update/delete.
- `IdempotencyPort` (interface): `fun findByFactId(factId: UUID): UUID?` (retorna recordId se já visto), `fun register(factId: UUID, recordId: UUID)`.
- `StreamHeadPort` (interface): `fun lockAndFetchTip(streamKey: String): StreamTip?` (SELECT FOR UPDATE), `fun upsertTip(streamKey: String, sequenceNo: Long, recordHash: String)`.
- `HashChainPort` (interface): `fun computeHash(prevHash: String, record: AuditRecord): String` (SHA-256 sobre canonical v1).
- `ActorDirectoryPort` (interface): `fun resolvePatientScope(caregiverId: UUID): List<UUID>` — pode lançar `ActorDirectoryUnavailableException` (fail-closed).
- Data class `StreamTip(lastSequenceNo: Long, lastRecordHash: String)`.

## Resultado esperado

Interfaces compilam sem dependência de Spring, JPA ou Feign.

## Criterios de aceite

- [ ] `AuditLedgerPort` não possui método `update`, `delete`, `save` com semântica de overwrite.
- [ ] `StreamHeadPort.lockAndFetchTip` sinaliza via nome que o contrato impõe lock (documentado em KDoc).
- [ ] `ActorDirectoryPort.resolvePatientScope` está documentado como podendo lançar `ActorDirectoryUnavailableException`.
- [ ] Nenhuma anotação de Spring/JPA/Feign nas interfaces.
- [ ] Compila com `./gradlew :hist-service:compileKotlin`.

## Dependencias

- HIST-002 (domain types)
- HIST-003 (DTOs compartilhados como TimelineFilter, PagedResult)

## Estimativa

S

## Prioridade

P1
