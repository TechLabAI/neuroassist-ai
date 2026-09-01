# SPEC-02 — Speech Tree Navigation and Node Selection (core)

## Objetivo
Especificar o núcleo do CONV: navegar a árvore de falas (root → categoria → frase) e selecionar um nó
`frase` retornando texto + referência de áudio do locale ativo, emitindo `SpeechNodeSelected`. Este é o
ato central que o CONV existe para servir (doc 01 §Success Signals).

## Escopo
**In:** `GET /tree` (walk estático em PostgreSQL), `POST /nodes/{id}/select`; invariantes do agregado
`ArvoreFala`/`NoArvoreFala`; emissão do fato de auditoria.
**Out:** busca semântica (SPEC-03), regra de fallback de áudio detalhada (SPEC-12), locale (SPEC-11).

## Decisões técnicas
- Navegação é **sempre** servida por PostgreSQL (fonte autoritativa) — independe do Elasticsearch
  (`ADR-002`; degrada por construção).
- `@Transactional` na camada `application`; leitura read-only para navegação/seleção (CONV não muta a
  árvore em runtime — apenas escreve `conv_audit_outbox`).
- Estrutura da árvore é locale-invariante (`ADR-005`); só o `ConteudoLocalizado` varia.

## Implementação
- `NavigateTreeService`: carrega nós por `arvore_id`/`paciente_id`, monta a hierarquia por `parent_id`
  ordenada por `ordem`; anexa o `ConteudoLocalizado` do locale ativo (SPEC-11).
- `SelectNodeService`: valida `tipo = frase` e `status = ativo` no locale ativo; resolve `audio_ref`
  (`AudioRefPort`, SPEC-12); emite `SpeechNodeSelected` (`HistAuditPort`, best-effort).
- Invariantes do agregado no `domain` (sem Spring/JPA): root único, sem ciclos/órfãos, `frase` é folha.

## Casos de borda
- Selecionar `categoria` → `409 node-not-selectable`.
- Selecionar nó `pendente` no locale ativo → `409 content-pending-locale` (SPEC-11).
- HIST indisponível → seleção retorna `200`; fato vai para o outbox (SPEC-09).

## Critérios de aceite (EARS)
- WHEN a valid `GET /tree` is received, THE SYSTEM SHALL return the full tree for the JWT `pacienteId` scoped to its `empresa`, served from PostgreSQL.
- THE SYSTEM SHALL return node structure identically regardless of active locale, varying only localized content.
- WHEN a `frase` node with `ativo` active-locale content is selected, THE SYSTEM SHALL return its `texto` + `audioRef` and emit exactly one `SpeechNodeSelected`.
- IF the target node is a `categoria`, THEN THE SYSTEM SHALL reject with `409 node-not-selectable`.
- THE SYSTEM SHALL enforce exactly one root (`parent_id IS NULL`) per tree and SHALL reject navigation over a tree with cycles or orphans as a data-integrity error.
