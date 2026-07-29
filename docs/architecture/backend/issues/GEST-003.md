# GEST-003 -- Define DetectionProfile and DetectionProfilePrimitive JPA entities with monotonic-version and single-active constraints

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto
`DetectionProfile` e o bundle imutavel e versionado que o edge puxa para se configurar. `version` e monotonicamente crescente por tenant e nunca reutilizado (invariante #3). Somente um perfil pode estar `active=true` por tenant (invariante: `ux_profile_single_active`). `DetectionProfilePrimitive` e a tabela de snapshot que congela os primitivos suportados no momento do publish, garantindo que o edge configure deterministicamente mesmo se o catalogo mudar depois.

## Spec de referencia
- SPEC-03, SPEC-04, data-model.md §2.3, §2.4, ADR-006

## Escopo
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/model/DetectionProfile.java` (JPA `@Entity`, UUID pk, version bigint, active boolean, contract_version, classification_params jsonb via String/jsonb, published_at, published_by; campo `active` pode ser setado apenas para false por update; UNIQUE version, partial UNIQUE active)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/model/DetectionProfilePrimitive.java` (JPA `@Entity`, FK para DetectionProfile ON DELETE CASCADE, FK para TipoGesto ON DELETE RESTRICT, nome_snapshot, engine_feature_ref NOT NULL, UNIQUE profile+tipoGesto)
- [ ] adicionar metodo factory `DetectionProfile.publish(long version, String contractVersion, String classificationParams, List<TipoGesto> supported)` que popula o snapshot e define `active=true`
- [ ] adicionar validacao que `supported` nao pode ser vazio (invariante: `empty-profile`)
- [ ] testes unitarios de factory e das invariantes de versao/active

## Resultado esperado
Entidades mapeadas; publish() cria snapshot correto dos primitivos suportados; versao nao pode ser reutilizada.

## Criterios de aceite
- [ ] `DetectionProfile.publish(...)` com lista vazia de suportados lanca `IllegalArgumentException` (guard antes de persistir)
- [ ] dois `DetectionProfile` com o mesmo `version` em testes de integracao violam `ux_profile_version` do BD
- [ ] `DetectionProfilePrimitive.engineFeatureRef` e obrigatorio (NOT NULL em JPA e SQL)
- [ ] `DetectionProfile` nao expoe setters que permitam mudar campos de conteudo apos criacao (imutabilidade)

## Dependencias
GEST-001, GEST-002 (precede).

## Estimativa
M

## Prioridade
P1
