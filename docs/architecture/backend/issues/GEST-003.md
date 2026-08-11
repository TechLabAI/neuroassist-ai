# GEST-003 -- Define DetectionProfile and DetectionProfilePrimitive domain models + JPA entities with monotonic-version and single-active constraints

**Status:** EM ANDAMENTO -- reconciliado 2026-08-10
**Maturidade:** 2 -- Modelos de dominio presentes; testes ausentes
**Done/Total:** 7/9 itens

## Contexto
`DetectionProfile` e o bundle imutavel e versionado que o edge puxa para se configurar. `version` e monotonicamente crescente por tenant e nunca reutilizado (invariante #3). Somente um perfil pode estar `active=true` por tenant (invariante: `ux_profile_single_active`). `DetectionProfilePrimitive` e a tabela de snapshot que congela os primitivos suportados no momento do publish, garantindo que o edge configure deterministicamente mesmo se o catalogo mudar depois. A arquitetura hexagonal (RULE-01/02) exige separacao: domain/model contem records puros (ZERO Spring/JPA) e infrastructure/persistence/entity contem os JPA @Entity.

## Spec de referencia
- SPEC-03, SPEC-04, data-model.md §2.3, §2.4, ADR-006

## Escopo
- [x] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/model/DetectionProfile.java` (Java record puro, ZERO Spring/JPA conforme RULE-01/02; campos: UUID id, long version, boolean active, String contractVersion, Map<String,Object> classificationParams, Instant publishedAt, String publishedBy, String tenantId; fabrica DetectionProfile.publish() com guard empty-profile) + `infrastructure/persistence/entity/DetectionProfileJpaEntity.java` (JPA @Entity; @Setter apenas em `active` — unico campo mutavel pos-criacao)
- [x] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/model/DetectionProfilePrimitive.java` (Java record puro, ZERO Spring/JPA; campos: UUID id, profileId, tipoGestoId, String nomeSnapshot, String engineFeatureRef; fabrica DetectionProfilePrimitive.fromSupported() que valida engineFeatureRef != null) + `infrastructure/persistence/entity/DetectionProfilePrimitiveJpaEntity.java` (JPA @Entity sem @Setter — imutavel; engineFeatureRef com @Column nullable=false)
- [x] adicionar metodo factory `DetectionProfile.publish(long version, String contractVersion, Map<String,Object> classificationParams, List<TipoGesto> supported, String tenantId, String publishedBy)` que define `active=true`
- [x] adicionar validacao que `supported` nao pode ser vazio (invariante: `empty-profile`) — lanca `IllegalArgumentException` antes de criar o perfil
- [ ] testes unitarios de factory e das invariantes de versao/active

## Resultado esperado
Domain records puros em domain/model; JPA entities em infrastructure/persistence/entity; publish() cria perfil ativo com snapshot correto dos primitivos suportados; versao nao pode ser reutilizada.

## Criterios de aceite
- [x] `DetectionProfile.publish(...)` com lista vazia de suportados lanca `IllegalArgumentException` (guard implementado no codigo; verificavel por inspecao de DetectionProfile.java)
- [ ] dois `DetectionProfile` com o mesmo `version` em testes de integracao violam `ux_profile_version` do BD (requer IT com Testcontainers — P3)
- [x] `DetectionProfilePrimitive.engineFeatureRef` e obrigatorio: NOT NULL em JPA (`@Column(nullable=false)` em DetectionProfilePrimitiveJpaEntity) e validado por `DetectionProfilePrimitive.fromSupported()` no dominio
- [x] `DetectionProfile` nao expoe setters que permitam mudar campos de conteudo apos criacao: implementado como Java record (imutavel por natureza); JPA entity com `@Setter` apenas em `active`

## Dependencias
GEST-001, GEST-002 (precede).

## Estimativa
M

## Prioridade
P1
