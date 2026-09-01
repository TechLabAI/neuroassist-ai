# TREI-001 -- Domain entities + JPA mappings (TREI-private tables)

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/12 itens

## Contexto

Cria as quatro entidades JPA que são **privadas do TREI** (data-model §2) e os enums
correspondentes. TREI não re-declara as tabelas CONV — apenas referencia por id.
As entidades encapsulam o estado do pipeline de autoria: sessão LLM, proposta do LLM,
lifecycle por (no_id, locale) e outbox broker-less de publicação.
O CHECK DDL do G5 é mapeado via `@Column(columnDefinition)` para garantir que
`sub_estado='ativo'` nunca ocorra sem `embedding_model_id` não-nulo.

## Spec de referencia

- SPEC-04 (data-model-migrations §2.1-2.4 — DDL das 4 tabelas)
- SPEC-11 (embedding-model-parity-g5 — G5 CHECK em TreiConteudoAutoria)

## Escopo

- [ ] implementar `src/main/java/com/neuroassist/trei/domain/TreiSessaoTreinamento.java` — `@Entity`, `@Table(name="trei_sessao_treinamento")`, campos `id(uuid)`, `pacienteId`, `cuidadoraId`, `localeAutoria(LocaleAutoria)`, `estado(EstadoSessao)`, `llmModelId`, `llmTranscriptRef`, `iniciadaEm`, `encerradaEm`
- [ ] implementar `src/main/java/com/neuroassist/trei/domain/TreiPropostaLlm.java` — `@Entity`, FK `sessaoId` para `TreiSessaoTreinamento`, FK opcional `noId` (uuid — sem cross-service FK), `textoProposto`, `promptRef`, `responseRef`, `estado(EstadoProposta)`
- [ ] implementar `src/main/java/com/neuroassist/trei/domain/TreiConteudoAutoria.java` — `@Entity`, `@Table(uniqueConstraints=@UniqueConstraint({"noId","locale"}))`, `subEstado(SubEstado)`, `embeddingModelId`, `embeddingRef`, `audioRef`, `audioFormato(AudioFormato)`, `publicadoEm`; DDL CHECK G5 via `@Column(columnDefinition="text CHECK(sub_estado<>'ativo' OR (embedding_model_id IS NOT NULL AND embedding_ref IS NOT NULL))")`
- [ ] implementar `src/main/java/com/neuroassist/trei/domain/TreiPublicacaoOutbox.java` — `@Entity`, `@Table(uniqueConstraints=@UniqueConstraint({"noId","locale","passo"}))`, `passo(PassoOutbox)`, `estado(EstadoOutbox)`, `tentativas`, `proximaEm`, `ultimoErro`
- [ ] implementar `src/main/java/com/neuroassist/trei/domain/LocaleAutoria.java` — enum `{ PT("pt"), EN("en") }` com `@Converter`
- [ ] implementar `src/main/java/com/neuroassist/trei/domain/SubEstado.java` — enum `{ RASCUNHO, AGUARDANDO_APROVACAO, PUBLICANDO, ATIVO, DESCARTADO }`
- [ ] implementar `src/main/java/com/neuroassist/trei/domain/PassoOutbox.java` — enum `{ EMBED, TTS, INDEX }`
- [ ] implementar `src/main/java/com/neuroassist/trei/domain/EstadoOutbox.java` — enum `{ PENDENTE, OK, FALHA }`
- [ ] implementar `src/main/java/com/neuroassist/trei/domain/EstadoSessao.java` — enum `{ ATIVA, ENCERRADA }`
- [ ] implementar `src/main/java/com/neuroassist/trei/domain/EstadoProposta.java` — enum `{ PROPOSTA, MATERIALIZADA, DESCARTADA }`

## Resultado esperado

Classes JPA compilando sem erro; enums com `@Converter` configurados para persistência
como string (não ordinal); UNIQUE constraint em `TreiConteudoAutoria(noId,locale)` e
`TreiPublicacaoOutbox(noId,locale,passo)` mapeadas; testes de constraint JPA passando.

## Criterios de aceite

- [ ] `./gradlew :trei-service:compileJava` sai 0 sem warnings de deprecation
- [ ] Todos os enums persistem como string (não ordinal) — verificado via `@Enumerated(EnumType.STRING)` ou converter customizado
- [ ] `TreiConteudoAutoria` tem constraint `UNIQUE(noId, locale)` visível em `@Table(uniqueConstraints)`
- [ ] `TreiPublicacaoOutbox` tem constraint `UNIQUE(noId, locale, passo)` visível em `@Table(uniqueConstraints)`

## Dependencias

Nenhuma (é a fundação do módulo).

## Estimativa

M

## Prioridade

P1
