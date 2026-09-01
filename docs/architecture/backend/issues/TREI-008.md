# TREI-008 -- JPA repositories + adapters para tabelas privadas do TREI

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto

Cria os repositórios Spring Data JPA para as 4 tabelas privadas do TREI e os adapters que
implementam as interfaces de domain. Os adapters ficam em `infrastructure.persistence.adapter`
e apenas os repositórios têm dependência em Spring Data. Multitenant: cada query opera
no schema correto via `TenantContextHolder` (TREI-014).

## Spec de referencia

- SPEC-04 (data-model §2.1-2.4 — as 4 tabelas privadas)
- ADR-*-hexagonal-architecture.md (adapters em infrastructure, ports em domain)

## Escopo

- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/persistence/TreiSessaoTreinamentoRepository.java` — `JpaRepository<TreiSessaoTreinamento, UUID>` com `findByIdAndEstado`, `findByPacienteIdAndEstado`
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/persistence/TreiPropostaLlmRepository.java` — `JpaRepository<TreiPropostaLlm, UUID>` com `findBySessaoId`, `findByNoIdAndLocale`
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/persistence/TreiConteudoAutoriaRepository.java` — `JpaRepository<TreiConteudoAutoria, UUID>` com `findByNoIdAndLocale`, `findByNoIdAndSubEstadoIn`
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/persistence/TreiPublicacaoOutboxRepository.java` — `JpaRepository<TreiPublicacaoOutbox, UUID>` com `findByEstadoAndProximaEmLessThanEqual` (para o OutboxDrainer)
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/persistence/adapter/TreiSessaoRepositoryAdapter.java` — adapter que implementa qualquer port de repositório da sessão (se existir) via `TreiSessaoTreinamentoRepository`
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/persistence/adapter/TreiConteudoAutoriaRepositoryAdapter.java` — adapter para queries de lifecycle do conteúdo de autoria

## Resultado esperado

Repositórios JPA compilando; adapters injetáveis nos services de application; queries
customizadas cobertas por métodos derivados ou `@Query`.

## Criterios de aceite

- [ ] `./gradlew :trei-service:compileJava` sai 0
- [ ] `TreiPublicacaoOutboxRepository` tem método `findByEstadoAndProximaEmLessThanEqual(EstadoOutbox, Instant, Pageable)` para uso pelo OutboxDrainer (TREI-016)
- [ ] Nenhum repositório Spring Data é injetado diretamente em classes de `application.*` ou `domain.*` (apenas via adapter)

## Dependencias

- TREI-001 (entidades JPA — devem existir para os repositórios compilarem)

## Estimativa

M

## Prioridade

P3
