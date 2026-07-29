# GEST-011 -- Implement JPA adapters for TipoGestoRepositoryPort and TipoGestoTransitionRepository (Spring Data JPA, multi-tenant schema routing)

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto
Os adapters de persistencia do `TipoGesto` e `TipoGestoTransition` implementam os ports definidos em GEST-004. O `TipoGestoRepositoryAdapter` delega para Spring Data JPA repositories que operam no schema do tenant corrente (injetado via `TenantContextHolder`). A query de unicidade de nome usa `lower(nome)` para case-insensitive (indice parcial `ux_tipo_gesto_nome_active`). Queries de paginacao por estado devem filtrar corretamente.

## Spec de referencia
- SPEC-04, data-model.md §2.1, §2.2, hub-integration.md §3

## Escopo
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/persistence/TipoGestoJpaRepository.java` (interface Spring Data JPA com queries: findByIdAndTenantSchema, findAllByStateInWithPagination, existsByNomeLowerAndNotDeprecated)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/persistence/TipoGestoTransitionJpaRepository.java` (interface Spring Data JPA com saveAndFlush; SEM metodos de delete)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/persistence/TipoGestoRepositoryAdapter.java` (implementa `TipoGestoRepositoryPort`, delega para ambos os JPA repos, aplica schema do `TenantContextHolder`)
- [ ] testes de integracao com Testcontainers PostgreSQL aplicando V0400 migration antes dos testes
- [ ] asserção que `TipoGestoTransitionJpaRepository` nao expoe metodos `delete*` no classpath

## Resultado esperado
Persistencia e leitura de TipoGesto e TipoGestoTransition funcionando no schema de tenant correto; zero cross-tenant data leakage em testes.

## Criterios de aceite
- [ ] salvar `TipoGesto` no tenant `t_empresa_test` e consultar no tenant `t_empresa_other` retorna zero resultados
- [ ] `existsByNomeLower` retorna true para variante com caps diferentes do nome ja cadastrado
- [ ] `appendTransition()` cria exatamente um registro em `tipo_gesto_transition` e nao altera o `TipoGesto` pai
- [ ] chamada de delete em `TipoGestoTransitionJpaRepository` lanca erro em tempo de compilacao (metodo nao existe)

## Dependencias
GEST-002, GEST-004, GEST-013 (migration V0400 deve existir para testes de integracao), GEST-014 (schema routing).

## Estimativa
M

## Prioridade
P3
