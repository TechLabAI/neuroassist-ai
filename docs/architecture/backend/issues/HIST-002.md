# HIST-002 -- Domain: AuditRecord aggregate + value objects (SubjectRef, ActorRef, HashChain, FactType)

## Contexto

O único agregado de HIST é `AuditRecord`: uma entrada write-once no ledger imutável (ADR-004).
O domínio não expressa nenhuma operação de mutação — somente `append` (factory estática).
Value objects encapsulam invariantes: SubjectRef (tipo+id opaco), ActorRef (snapshot de cuidador),
HashChain (prevHash+recordHash), FactType (membro do vocabulário controlado v1).

## Spec de referencia

- SPEC-02 (Audit Fact Ingestion & Hash Chaining — seção "Decisões técnicas")
- SPEC-04 (Data Model — §2.1 audit_record invariants)
- ADR-004 (Immutable Append-Only Ledger)
- ADR-006 (Hash-Chained Tamper-Evidence)
- `contracts/data-model.md §2.1`

## Escopo

**Pacote `com.neuroassist.hist.domain`:**
- `AuditRecord` (data class Kotlin): campos mapeados de `audit_record`; construtor privado;
  factory `AuditRecord.create(factId, factType, subject, actor, occurredAt, payload,
  sequenceNo, prevHash, recordHash, correctsRecordId?)`.
- `SubjectRef` (value object): `type: SubjectType`, `id: UUID`.
- `ActorRef` (value object): `caregiverId: UUID`, `displayNameSnapshot: String(≤200)`.
- `HashChain` (value object): `prevHash: String(64)`, `recordHash: String(64)`.
- `FactType` (value object / sealed class): encapsula o vocabulário v1; `of(raw: String)` lança
  `UnknownFactTypeException` se não reconhecido.
- `SubjectType` (enum): `PACIENTE, COMANDO, CUIDADOR, DISPOSITIVO`.
- Exceções de domínio: `UnknownFactTypeException`, `ForbiddenPayloadKeyException`,
  `FutureOccurredAtException`, `CorrectsTargetNotFoundException`.
- Constante de genesis: `HashChain.GENESIS = "0".repeat(64)`.

## Resultado esperado

Classes de domínio cobertas por testes unitários (sem Spring). `AuditRecord.create()` valida
campos obrigatórios e lança as exceções de domínio apropriadas.

## Criterios de aceite

- [ ] `AuditRecord.create()` compila e os campos são não-nulos onde obrigatório.
- [ ] `FactType.of("evento.confirmado")` retorna instância; `FactType.of("desconhecido")` lança `UnknownFactTypeException`.
- [ ] `HashChain.GENESIS` = 64 zeros.
- [ ] `SubjectType` enum contém exatamente `PACIENTE, COMANDO, CUIDADOR, DISPOSITIVO`.
- [ ] Teste unitário de `AuditRecord.create()` cobre todos os campos obrigatórios.
- [ ] Nenhuma dependência de Spring/JPA no pacote `domain`.

## Dependencias

- HIST-001 (scaffold)

## Estimativa

M

## Prioridade

P1
