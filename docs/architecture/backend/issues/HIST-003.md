# HIST-003 -- Inbound ports (use-case interfaces)

## Contexto

Portas inbound definem os contratos da camada de aplicação visíveis para os adaptadores REST.
São interfaces puras sem dependência de framework — a inversão que permite testar aplicação
independente de HTTP.

## Spec de referencia

- SPEC-01 (API Surface — tabela de endpoints x casos de uso)
- SPEC-02 (RecordAuditFactUseCase.append)
- SPEC-03 (QueryHistoryUseCase, VerifyChainUseCase)
- `generator-config.yml` — `inbound_ports`

## Escopo

**Pacote `com.neuroassist.hist.application.port.inbound`:**
- `RecordAuditFactUseCase` (interface): `fun append(command: RecordAuditFactCommand): AuditRecordResult`
- `QueryHistoryUseCase` (interface):
  - `fun findById(id: UUID, tenantId: String): AuditRecordResult`
  - `fun queryTimeline(filter: TimelineFilter, page: PageRequest): PagedResult<AuditRecordResult>`
- `VerifyChainUseCase` (interface): `fun verifyStream(streamKey: String, window: TimeWindow?): ChainVerificationResult`
- DTOs de comando e resultado no mesmo pacote (ou sub-pacote `dto`):
  - `RecordAuditFactCommand` (campos de `AuditFactRequest`)
  - `TimelineFilter` (subjectType, subjectId, factType, actorCaregiverId, from, to, order)
  - `AuditRecordResult`
  - `ChainVerificationResult` (streamKey, status: ChainStatus, recordsVerified, brokenAtSequenceNo, verifiedAt)
- `FactTypeCatalogUseCase` (interface): `fun listActive(): List<String>` — exposto por GET /fact-types.

## Resultado esperado

Interfaces compilam sem dependência de Spring. Os DTOs são data classes imutáveis Kotlin
com `val` fields.

## Criterios de aceite

- [ ] Cada interface possui apenas métodos do contrato mapeado na tabela de SPEC-01.
- [ ] `ChainVerificationResult.status` é do tipo `ChainStatus` (intact/broken/empty).
- [ ] Nenhuma anotação `@Component`/`@Service` nas interfaces.
- [ ] Compila com `./gradlew :hist-service:compileKotlin`.

## Dependencias

- HIST-002 (domain types usados pelos DTOs)

## Estimativa

S

## Prioridade

P1
