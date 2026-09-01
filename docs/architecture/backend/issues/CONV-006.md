# CONV-006 -- Criar NodeSelectionService — selectNode (POST /nodes/{id}/select, texto+audioRef, emissão de SpeechNodeSelected)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/8 itens

## Contexto

O ato âncora do CONV: paciente seleciona um nó da árvore para "falar". `NodeSelectionService`
valida o nó (deve ser `frase` com conteúdo `ativo` no locale ativo), resolve o áudio via
`AudioRefPort`, retorna `texto` + `audioRef`, e emite `SpeechNodeSelected` para HIST via
`HistAuditPort`. Se HIST falhar, persiste o fato em `conv_audit_outbox` (best-effort, SPEC-09).
Idempotência por `clientEventId`: um `POST /select` com o mesmo `clientEventId` não re-emite o
evento HIST nem re-insere no outbox (índice único parcial `uq_conv_outbox_client_event`).
`playbackMode` = `audio-cacheado` se áudio presente; `web-speech` se ausente (`degraded: true`).

## Spec de referencia

- SPEC-02 (speech-tree-navigation-and-selection — seleção e falhas)
- SPEC-05 (idempotency-concurrency — clientEventId)
- SPEC-09 (messaging-architecture — outbox best-effort)
- `contracts/api-contract.md` §2.3

## Escopo

- [ ] Criar `application/service/NodeSelectionService.java` com operação `selectNode(UUID noId, SelectRequest req, UUID pacienteId, String locale): SelectResponse`
- [ ] Validações: nó existe → 404 `node-not-found`; nó é `categoria` → 409 `node-not-selectable`; conteúdo locale ativo ausente → 409 `content-pending-locale`
- [ ] Resolver áudio: chamar `AudioRefPort.resolveAudioUri(noId, locale)` → se presente: `playbackMode: audio-cacheado`; se vazio: `playbackMode: web-speech`, `degraded: true`
- [ ] Emitir `SpeechNodeSelected`: construir `AuditFact` com `clientEventId`, `noId`, `pacienteId`, `locale`, `via`, `playbackMode`, `degraded`, `correlationId`; chamar `HistAuditPort.send`
- [ ] Idempotência: antes de inserir no outbox ou emitir, checar se `conv_audit_outbox` já tem linha com esse `clientEventId` (índice único parcial do DDL); se sim, retornar resposta idêntica sem nova emissão
- [ ] Tratamento de falha HIST: capturar `HistUnavailableException`, inserir em `conv_audit_outbox` com `status: PENDENTE`, retornar `200` ao paciente
- [ ] Criar `api/controller/ConvSelectionController.java` (`POST /conv/nodes/{id}/select`, scope `conv.no.selecionar`)
- [ ] Testes unitários: seleção normal, áudio ausente (degraded), nó categoria (409), pending_locale (409), clientEventId duplicado (idempotente)

## Resultado esperado

`POST /api/v1/conv/nodes/{id}/select` retorna `200` com texto e audioRef; emite `SpeechNodeSelected`; falha HIST não bloqueia; `clientEventId` duplicado não re-emite.

## Criterios de aceite

- [ ] `./gradlew :conv-service:test` BUILD SUCCESS
- [ ] Teste unitário: seleção de `frase` ativo, áudio presente → `playbackMode: audio-cacheado`, `degraded: false`
- [ ] Teste unitário: seleção com áudio ausente → `playbackMode: web-speech`, `degraded: true`, `SpeechNodeSelected.degraded: true`
- [ ] Teste unitário: seleção de `categoria` → `409 node-not-selectable`
- [ ] Teste unitário: seleção com `content-pending-locale` → `409 content-pending-locale`
- [ ] Teste unitário: `HistAuditPort.send` lança `HistUnavailableException` → fato inserido no outbox, resposta `200` retornada
- [ ] Teste unitário: segunda chamada com mesmo `clientEventId` → `SpeechNodeSelected` emitido apenas uma vez (idempotente)
- [ ] Controller rejeita token sem scope `conv.no.selecionar` com `403`

## Dependencias

- CONV-004 (ports)
- CONV-005 (navigateTree — reutiliza lógica de resolução de nó)

## Estimativa

L

## Prioridade

P2
