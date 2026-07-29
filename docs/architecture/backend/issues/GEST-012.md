# GEST-012 -- Implement JPA adapters for DetectionProfileRepositoryPort (Spring Data JPA, monotonic sequence, single-active constraint)

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto
O adapter de persistencia do `DetectionProfile` e `DetectionProfilePrimitive` implementa `DetectionProfileRepositoryPort`. O maior desafio e garantir a versao monotonica por tenant — `nextVersion()` usa `SELECT MAX(version)+1 FOR UPDATE` (ou sequence) para evitar colisao em concurrent publish. O `deactivateAllExcept()` faz um UPDATE em batch para setar `active=false` em todos os outros perfis do tenant.

## Spec de referencia
- SPEC-03, SPEC-04, SPEC-05, data-model.md §2.3, §2.4

## Escopo
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/persistence/DetectionProfileJpaRepository.java` (queries: findActiveByTenantId, findByVersionAndTenantId, deactivateAllByTenantIdExcept, maxVersionByTenantId)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/persistence/DetectionProfilePrimitiveJpaRepository.java` (saveAll, findAllByDetectionProfileId)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/persistence/DetectionProfileRepositoryAdapter.java` (implementa `DetectionProfileRepositoryPort`; `nextVersion()` usa pessimistic lock ou SERIALIZABLE isolation para monotonicity)
- [ ] testes de integracao: publicacoes concorrentes nao repetem versao (teste com dois threads)
- [ ] assercao que `findActiveByTenantId` retorna no maximo um perfil

## Resultado esperado
Publish concorrente de dois perfis gera versoes distintas (1, 2); nunca dois `active=true` simultaneos no mesmo tenant.

## Criterios de aceite
- [ ] dois publishes sequenciais retornam versoes distintas e monotonicamente crescentes
- [ ] `findActiveByTenantId` retorna `Optional.empty()` para tenant sem perfil publicado
- [ ] apos segundo publish, `findAllByTenantId` retorna exatamente um com `active=true` e os demais com `active=false`
- [ ] tentativa de inserir dois perfis com mesma `version` falha com `DataIntegrityViolationException` (`ux_profile_version`)

## Dependencias
GEST-003, GEST-004, GEST-013, GEST-014.

## Estimativa
M

## Prioridade
P3
