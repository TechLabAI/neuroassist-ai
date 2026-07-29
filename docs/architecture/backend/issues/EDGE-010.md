# EDGE-010 -- Implement JPA adapters for all four EDGE repository ports (DispositivoEdge, Enrollment, Credential, Transition) with multi-tenant schema routing

**Status:** INTACT -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto
Os quatro adapters JPA implementam as interfaces de porta do dominio usando Spring Data JPA com schema routing por tenant. O routing e transparente ao dominio — o adapter escolhe o datasource correto baseado no `TenantContextHolder`. O adapter de Transition e append-only (sem update/delete).

## Spec de referencia
- SPEC-04

## Escopo
- [ ] criar `infrastructure/persistence/DispositivoEdgeJpaAdapter.java` implementando `DispositivoEdgeRepositoryPort` usando `DispositivoEdgeJpaRepository extends JpaRepository`; incluir query `findActivePairingForPatient` com WHERE `pairing_state = 'paired' AND paciente_id = :pacienteId`
- [ ] criar `infrastructure/persistence/DispositivoEnrollmentJpaAdapter.java` implementando `DispositivoEnrollmentRepositoryPort` com busca por `token_hash`
- [ ] criar `infrastructure/persistence/DispositivoCredentialJpaAdapter.java` implementando `DispositivoCredentialRepositoryPort` (inclui `revokeByDeviceId` via JPQL UPDATE status/revoked_at)
- [ ] criar `infrastructure/persistence/DispositivoEdgeTransitionJpaAdapter.java` implementando append-only `DispositivoEdgeTransitionRepositoryPort` (sem update/delete)
- [ ] criar `infrastructure/config/MultiTenantDataSourceConfig.java` (SchemaRoutingDataSource, TenantContextHolder, schema prefix `t_`)
- [ ] configurar 503 rapido quando schema do tenant nao existe (SchemaRoutingDataSource lanca `TenantSchemaNotFoundException`)

## Resultado esperado
Testes de integracao (Testcontainers) executam operacoes CRUD em todas as quatro tabelas com routing correto por tenant.

## Criterios de aceite
- [ ] CRUD em `dispositivo_edge` funciona com tenant isolado
- [ ] busca por token hash em `dispositivo_enrollment` usa index `ux_dispositivo_enrollment_token`
- [ ] revocacao de credential via `revokeByDeviceId` atualiza `status=revoked, revoked_at=now`
- [ ] insercao em `dispositivo_edge_transition` funciona; nenhum metodo de delete/update exposto
- [ ] 503 retornado quando o schema do tenant nao existe (SchemaRoutingDataSource falha rapido)

## Dependencias
- EDGE-003 (precede)

## Estimativa
M

## Prioridade
P3
