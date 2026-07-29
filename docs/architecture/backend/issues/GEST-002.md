# GEST-002 -- Define TipoGesto and TipoGestoTransition JPA entities with detectability state check constraints

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto
O agregado raiz `TipoGesto` carrega o vocabulario de primitivos faciais com a maquina de estados `DetectabilityState` (registered→pending_training→supported→deprecated). `TipoGestoTransition` e a tabela append-only de auditoria de cada transicao (invariante #4 — nenhuma regressao silenciosa). Ambas precisam das constraints de BD mapeadas via JPA (`ck_tipo_gesto_supported_needs_ref`, `ux_tipo_gesto_nome_active`) para que o guard seja enforcement no nivel do schema.

## Spec de referencia
- SPEC-02, SPEC-04, data-model.md §2.1, §2.2, ADR-006

## Escopo
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/model/TipoGesto.java` (JPA `@Entity`, UUID pk gen_random_uuid, `detectabilityState` enum, `engineFeatureRef` nullable, `@Check` constraints, audit fields created_by/updated_by)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/model/TipoGestoTransition.java` (JPA `@Entity`, append-only, FK para TipoGesto ON DELETE RESTRICT, campos from_state/to_state/actor/reason/at)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/model/DetectabilityState.java` (enum: REGISTERED, PENDING_TRAINING, SUPPORTED, DEPRECATED)
- [ ] adicionar metodo `isTransitionAllowed(DetectabilityState to)` em `TipoGesto` (allowed set do ADR-006)
- [ ] adicionar metodo factory `TipoGesto.register(String nome)` retornando instancia em REGISTERED
- [ ] cobertura de testes unitarios para todas as 5 transicoes permitidas e 3+ proibidas

## Resultado esperado
Entidades mapeadas com constraints; `isTransitionAllowed()` rejeita transicoes ilegais; `TipoGestoTransition` nao pode ser deletada ou atualizada pela aplicacao.

## Criterios de aceite
- [ ] `TipoGesto.isTransitionAllowed(DEPRECATED)` retorna false quando estado atual e REGISTERED (sem transicao direta)
- [ ] `TipoGesto` com estado SUPPORTED e `engineFeatureRef=null` e rejeitado pela constraint JPA antes de persistir (`ck_tipo_gesto_supported_needs_ref`)
- [ ] tentativa de `UPDATE` via JPA em `TipoGestoTransition` lanca excecao (imutabilidade verificada em teste)
- [ ] testes unitarios cobrem todos os 5 arcos do ADR-006 (permitidos) e pelo menos 3 arcos proibidos

## Dependencias
GEST-001 (precede).

## Estimativa
M

## Prioridade
P1
