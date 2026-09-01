# TREI-009 -- JPA write adapters para tabelas compartilhadas do CONV

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

TREI é o **único escritor** das tabelas `conv_*` compartilhadas com CONV (data-model §0):
insere nós na árvore de falas, cria conteúdo per-locale pendente, seta flags de aprovação
e faz o flip `pendente→ativo` quando a publicação completa. CONV nunca escreve nessas
colunas; as DDLs são de propriedade do CONV e chegam via MIG. TREI usa JPA sobre o mesmo
schema `t_<empresa>` — uma FK real é permitida (mesmo schema).

## Spec de referencia

- SPEC-04 (data-model §0 — TREI writes, ownership map)
- data-model.md §4 (publication state machine — flip conv_no_conteudo.status=ativo)

## Escopo

- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/persistence/ConvNoArvoreFalaRepository.java` — `JpaRepository<ConvNoArvoreFala, UUID>` com `findByPacienteIdAndParentIdIsNull` (raiz da árvore) + `existsByPacienteIdAndNoId`
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/persistence/ConvNoConteudoRepository.java` — `JpaRepository<ConvNoConteudo, UUID>` com `findByNoIdAndLocale`, `updateConfirmadoCuidadora`, `updateConfirmadoPaciente`, `updateStatus`
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/persistence/adapter/ConvTreeWriteAdapter.java` — implementa operações de escrita na árvore CONV: `insertNode(NoArvoreFala)`, `insertConteudo(NoConteudo)`, `setConfirmadoCuidadora(noId,locale)`, `setConfirmadoPaciente(noId,locale)`, `flipStatusAtivo(noId,locale)` — todas transacionais via application layer
- [ ] `ConvNoArvoreFala` e `ConvNoConteudo` mapeiam as colunas existentes declaradas pelo CONV (não re-criam DDL — apenas leem o schema existente): campos de aprovação (`confirmado_cuidadora`, `confirmado_paciente`), `status`, `audio_ref`
- [ ] criar `src/test/java/com/neuroassist/trei/infrastructure/persistence/ConvTreeWriteAdapterTest.java` — testes com `@DataJpaTest` usando schema H2 que simula as tabelas conv_* para verificar writes e flip de status

## Resultado esperado

Adapter compilando; `flipStatusAtivo` faz o flip atômico `pendente→ativo` apenas quando
outbox completa (chamado pelo PublicacaoService após todos os steps `ok`).

## Criterios de aceite

- [ ] `./gradlew :trei-service:test --tests "*ConvTreeWriteAdapterTest*"` sai 0
- [ ] `flipStatusAtivo` não é chamável pelo application layer enquanto qualquer outbox step está `pendente` (regra de invariante do PublicacaoService — testada em TREI-016)
- [ ] `ConvNoArvoreFala` e `ConvNoConteudo` NÃO têm anotação `@Column(columnDefinition="CREATE TABLE...")` — apenas mapeiam colunas existentes

## Dependencias

- TREI-001 (JPA entities do TREI — tipos de enums reutilizados)

## Estimativa

M

## Prioridade

P3
