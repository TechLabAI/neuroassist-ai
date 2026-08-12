# GEST-002 -- Define TipoGesto and TipoGestoTransition domain models + JPA entities with detectability state

**Status:** EM ANDAMENTO -- reconciliado 2026-08-10
**Maturidade:** 2 -- Modelos de dominio presentes; testes ausentes
**Done/Total:** 5/10 itens

## Contexto
O agregado raiz `TipoGesto` carrega o vocabulario de primitivos faciais com a maquina de estados `DetectabilityState` (registered→pending_training→supported→deprecated). `TipoGestoTransition` e a tabela append-only de auditoria de cada transicao (invariante #4 — nenhuma regressao silenciosa). A arquitetura hexagonal (RULE-01/02) exige separacao: domain/model contem records puros (ZERO Spring/JPA) e infrastructure/persistence/entity contem os JPA @Entity correspondentes.

## Spec de referencia
- SPEC-02, SPEC-04, data-model.md §2.1, §2.2, ADR-006

## Escopo
- [x] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/model/TipoGesto.java` (Java record puro, ZERO Spring/JPA conforme RULE-01/02; campos: UUID id, String nome, DetectabilityState detectabilityState, String engineFeatureRef, String tenantId, Instant createdAt/updatedAt, String createdBy/updatedBy; metodos isTransitionAllowed() e withState()) + `infrastructure/persistence/entity/TipoGestoJpaEntity.java` (JPA @Entity para tabela tipo_gesto, campos audit, detectabilityState como varchar)
- [x] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/model/TipoGestoTransition.java` (Java record puro, ZERO Spring/JPA; append-only, factory TipoGestoTransition.of(); campos: id, tipoGestoId, fromState, toState, actor, reason, at) + `infrastructure/persistence/entity/TipoGestoTransitionJpaEntity.java` (JPA @Entity sem @Setter — imutabilidade garantida no nivel JPA)
- [x] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/model/DetectabilityState.java` (enum: REGISTERED, PENDING_TRAINING, SUPPORTED, DEPRECATED)
- [x] adicionar metodo `isTransitionAllowed(DetectabilityState to)` em `TipoGesto` (5 arcos ADR-006: REGISTERED→PENDING_TRAINING, REGISTERED→DEPRECATED, PENDING_TRAINING→SUPPORTED, PENDING_TRAINING→DEPRECATED, SUPPORTED→DEPRECATED)
- [x] adicionar metodo factory `TipoGesto.register(String nome, String tenantId)` retornando instancia em REGISTERED
- [ ] cobertura de testes unitarios para todas as 5 transicoes permitidas e 3+ proibidas (ex: SUPPORTED→REGISTERED, DEPRECATED→REGISTERED, PENDING_TRAINING→REGISTERED)

## Resultado esperado
Domain records puros em domain/model; JPA entities em infrastructure/persistence/entity; `isTransitionAllowed()` rejeita transicoes ilegais; `TipoGestoTransition` nao pode ser atualizada pela aplicacao.

## Criterios de aceite
- [ ] `TipoGesto.isTransitionAllowed(DEPRECATED)` retorna `true` quando estado atual e REGISTERED (arco direto REGISTERED→DEPRECATED permitido por ADR-006); retorna `false` para reversoes como SUPPORTED→REGISTERED (regressao proibida) -- NOTA: criterio original estava incorreto (dizia "false para DEPRECATED quando REGISTERED") -- teste unitario pendente
- [ ] `TipoGesto` com estado SUPPORTED e `engineFeatureRef=null` e rejeitado: validacao no dominio via `DetectionProfilePrimitive.fromSupported()` + constraint de BD `ck_tipo_gesto_supported_needs_ref` (em V1__create_initial_schema.sql); teste de integracao pendente (P3)
- [ ] imutabilidade de `TipoGestoTransitionJpaEntity`: sem `@Setter` (implementado no codigo); teste de UPDATE via JPA que verifica lancamento de excecao: pendente
- [ ] testes unitarios cobrem todos os 5 arcos do ADR-006 (permitidos) e pelo menos 3 arcos proibidos: pendente

## Dependencias
GEST-001 (precede).

## Estimativa
M

## Prioridade
P1
