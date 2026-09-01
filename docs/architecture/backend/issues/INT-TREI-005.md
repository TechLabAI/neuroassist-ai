# INT-TREI-005 -- INT: TREI <-> CONV shared state (conv tables write, audio store, G6)

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto

TREI e CONV são acoplados apenas através de **estado compartilhado** (nunca chamada
síncrona entre eles). TREI escreve; CONV lê. O contrato cobre: (1) DDL das tabelas
`conv_*` (owned by CONV, TREI escreve via JPA no mesmo schema); (2) flip atômico
`conv_no_conteudo.status = 'ativo'` após publicação completa; (3) path de áudio compartilhado
`TREI_AUDIO_ROOT == CONV_AUDIO_ROOT` (G6); (4) G5 model parity (coberta em INT-TREI-004).
CONV nunca vê locale parcialmente publicado.

## Spec de referencia

- data-model.md §0 (ownership map — TREI writes, CONV reads)
- data-model.md §4 (state machine — flip conv_no_conteudo.status=ativo apenas após todos steps ok)
- integration-contracts.md §5 (AudioStorePort — TREI_AUDIO_ROOT==CONV_AUDIO_ROOT, G6)

## Escopo

- [ ] verificar que DDL das tabelas CONV (`conv_no_arvore_fala`, `conv_no_conteudo`) está disponível no schema local antes de rodar TREI (dependência MIG ordering)
- [ ] criar `src/test/java/com/neuroassist/trei/integration/ConvSharedStateIntegrationTest.java` — testa escritas do TREI nas tabelas CONV: insert de nó, insert de conteudo pendente, flip de flags, flip de status para ativo
- [ ] testar invariante: `conv_no_conteudo.status` permanece `pendente` enquanto qualquer outbox step NÃO é `ok`; flip apenas quando todos os 3 são `ok`
- [ ] testar invariante G6: `audioRef` gravado em `conv_no_conteudo.audio_ref` usa o mesmo path relativo ao root que CONV lê (`<empresa>/<paciente>/<locale>/<no_id>.<ext>`)
- [ ] documentar gap G6 no `setup-local.md`: `TREI_AUDIO_ROOT` e `CONV_AUDIO_ROOT` devem apontar para o mesmo volume em produção
- [ ] verificar que CONV não tem endpoint chamado por TREI (boundary: apenas shared state, hub-integration.md §5)

## Resultado esperado

`conv_no_conteudo.status` nunca é `ativo` antes da publicação completa; `audioRef`
no formato correto para CONV; nenhuma chamada HTTP entre TREI e CONV.

## Criterios de aceite

- [ ] `./gradlew :trei-service:test --tests "*ConvSharedStateIntegrationTest*"` sai 0
- [ ] Teste verifica que `conv_no_conteudo.status` é `pendente` após step `embed` ok mas `tts` ainda `pendente`
- [ ] Teste verifica que `audio_ref` é `<empresa>/<paciente>/<locale>/<no_id>.opus` (ou `.mp3`) sem o prefix do root
- [ ] Nenhuma classe de TREI importa nenhum client HTTP para `conv-service` (grep deve retornar vazio)

## Dependencias

- TREI-009 (ConvTreeWriteAdapter)
- TREI-016 (PublicacaoService — flip atômico)

## Estimativa

M

## Prioridade

P6
