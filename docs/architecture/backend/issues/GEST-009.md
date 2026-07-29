# GEST-009 -- Implement DetectionProfileService.publish() — snapshot supported primitives, monotonic version, deactivate prior active profile

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto
`POST /api/v1/gest/detection-profiles` constroi e publica uma nova versao de perfil imutavel. O servico extrai todos os primitivos SUPPORTED do tenant no momento do publish, congela seus `engineFeatureRef` em `DetectionProfilePrimitive`, aloca um `version` monotonicamente crescente (invariante #3), define `active=true` e desativa o perfil anterior (invariante: apenas um active por tenant). O evento `DetectionProfilePublished` e emitido pos-commit. Tenant sem primitivos SUPPORTED retorna 422.

## Spec de referencia
- SPEC-03, api-contract.md §4.1, data-model.md §2.3, §2.4, event-contracts.md §2

## Escopo
- [ ] implementar `DetectionProfileService.publish(ClassificationParams params)` em `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/service/DetectionProfileService.java`
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/event/DetectionProfilePublished.java` (record: profileId, version, contractVersion, supportedPrimitiveIds, publishedAt)
- [ ] adicionar `nextVersion(String tenantId)` em `DetectionProfileRepositoryPort` (SELECT MAX(version)+1 ou 1 se vazio)
- [ ] adicionar `deactivateAllExcept(UUID profileId, String tenantId)` em `DetectionProfileRepositoryPort`
- [ ] adicionar `findAllSupportedByTenant(String tenantId)` em `TipoGestoRepositoryPort`
- [ ] testes de integracao: publish com suportados, versao monotonica em publicacoes sucessivas, 422 sem suportados

## Resultado esperado
`POST /detection-profiles` cria perfil imutavel com snapshot de suportados, versao monotonica e desativa o anterior; `DetectionProfilePublished` e emitido pos-commit.

## Criterios de aceite
- [ ] dois publishes consecutivos resultam em `version=1` e `version=2` respectivamente (monotonicity)
- [ ] apos o segundo publish, o primeiro profile tem `active=false` e o segundo tem `active=true` (single-active)
- [ ] publicar com zero primitivos SUPPORTED retorna `422 /errors/empty-profile`
- [ ] conteudo do perfil (primitives snapshot) e imutavel apos criacao — tentativa de update no campo `primitives` do perfil nao altera BD
- [ ] `DetectionProfilePublished` publicado exatamente uma vez por publish bem-sucedido

## Dependencias
GEST-003, GEST-004, GEST-005, GEST-007 (GEST-007 gera primitivos SUPPORTED para testar).

## Estimativa
M

## Prioridade
P2
