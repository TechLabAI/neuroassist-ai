# TREI-006 -- Dual authorization gate — approveCaregiver + approvePatient + DualAuthorizationGateCleared

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/9 itens

## Contexto

O gate de dupla autorização (ADR-003) é o núcleo de segurança do TREI: nenhuma
publicação ocorre sem dois atores distintos (caregiver e patient-with-session) aprovarem
o mesmo `(noId, locale)` em momentos e shells diferentes. O `AutorizacaoService`:
(1) recebe aprovação da cuidadora → seta `confirmado_cuidadora=true`, audita para HIST;
(2) recebe aprovação do paciente → seta `confirmado_paciente=true`, audita para HIST;
(3) quando AMBOS `true` → dispara `DualAuthorizationGateCleared` (que cria outbox rows
e inicia pipeline). Re-aprovação do mesmo ator já aprovado → idempotente, sem 409.
Token errado por ator (caregiver no endpoint do paciente e vice-versa) → 403.

## Spec de referencia

- SPEC-03 (dual-authorization-gate — regras completas do gate)
- SPEC-05 (idempotency-concurrency — re-aprovação no-op)
- api-contract.md §2.4-2.5 (contratos dos dois endpoints de aprovação)

## Escopo

- [ ] criar `src/main/java/com/neuroassist/trei/application/service/AutorizacaoService.java` — interface `approveCaregiver(UUID noId, String locale, UUID cuidadoraId)` + `approvePatient(UUID noId, String locale, UUID pacienteSessaoSub)`
- [ ] criar `src/main/java/com/neuroassist/trei/application/service/AutorizacaoServiceImpl.java` — implementação usando `TreiConteudoAutoriaRepository` + `ConvNoConteudoRepository` + `TreiPublicacaoOutboxRepository`
- [ ] criar `src/main/java/com/neuroassist/trei/domain/event/NodeApprovedByCaregiver.java` — evento in-process com `noId`, `locale`, `cuidadoraId`, `at`
- [ ] criar `src/main/java/com/neuroassist/trei/domain/event/NodeApprovedByPatient.java` — evento in-process com `noId`, `locale`, `pacienteSessaoSub`, `at`
- [ ] criar `src/main/java/com/neuroassist/trei/domain/event/DualAuthorizationGateCleared.java` — evento in-process disparado quando ambas as flags são `true`
- [ ] lógica de re-aprovação idempotente: se `confirmado_cuidadora` já é `true` e cuidadora aprova de novo → no-op (sem 409, SPEC-05)
- [ ] quando gate dispara: criar exatamente 3 linhas em `TreiPublicacaoOutbox` (`embed`, `tts`, `index`) com `estado=PENDENTE` — idempotente via UNIQUE constraint (SPEC-05)
- [ ] auditar cada aprovação para HIST via `HistAuditPort.audit` (capturar exceção — HIST outage não bloqueia aprovação)
- [ ] criar `src/test/java/com/neuroassist/trei/application/service/AutorizacaoServiceTest.java` — testar gate dispara apenas quando ambos true; re-aprovação idempotente; exatamente 3 outbox rows criados

## Resultado esperado

Gate de dupla autorização correto: outbox criado exatamente uma vez quando ambos os flags
são `true`; re-aprovação não cria outbox duplicado (UNIQUE constraint garante).

## Criterios de aceite

- [ ] `./gradlew :trei-service:test --tests "*AutorizacaoServiceTest*"` sai 0
- [ ] Teste verifica que outbox NÃO é criado após apenas uma aprovação (caregiver ou paciente isolado)
- [ ] Teste verifica que outbox é criado com 3 rows (`EMBED`, `TTS`, `INDEX`) após ambas aprovações
- [ ] Teste verifica que re-aprovação do mesmo ator não lança exceção e não duplica outbox rows

## Dependencias

- TREI-001 (entidades + eventos)
- TREI-003 (ports — HistAuditPort)
- TREI-007 (NoAutoriaService — o nó deve existir antes de ser aprovado)

## Estimativa

L

## Prioridade

P2
