# TREI-017 -- Idempotency guards (SPEC-05)

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

Consolida os guards de idempotência do TREI (SPEC-05): (1) re-aprovação da cuidadora em
nó já aprovado → no-op sem 409; (2) re-aprovação do paciente em nó já aprovado → no-op;
(3) step `tts` em arquivo já sintetizado → reusa sem chamar ElevenLabs (FIN-002);
(4) gate `DualAuthorizationGateCleared` chamado com ambas flags já `true` → UNIQUE
constraint `(no_id, locale, passo)` em `TreiPublicacaoOutbox` impede duplicação.

## Spec de referencia

- SPEC-05 (idempotency-concurrency — todos os cenários de idempotência)
- api-contract.md §2.4-2.5 (aprovação idempotente — sem 409 em re-aprovação)

## Escopo

- [ ] criar `src/main/java/com/neuroassist/trei/application/service/IdempotencyGuard.java` — utilitário que encapsula as verificações: `isAlreadyApprovedByCaregiver(noId,locale)`, `isAlreadyApprovedByPatient(noId,locale)`, `isGateAlreadyFired(noId,locale)` — consultando `TreiConteudoAutoriaRepository`
- [ ] integrar `IdempotencyGuard` no `AutorizacaoServiceImpl`: antes de mutar flags, verificar e fazer early-return se já está setado (no-op)
- [ ] integrar na lógica de criação do outbox: usar `TreiPublicacaoOutboxRepository.save` em `try/catch DataIntegrityViolationException` para absorver duplicatas (a UNIQUE constraint é o guard definitivo)
- [ ] criar `src/test/java/com/neuroassist/trei/application/service/IdempotencyGuardTest.java` — testar re-aprovação não lança exceção; testar outbox não duplica rows mesmo com chamada concorrente simulada

## Resultado esperado

Re-aprovação de caregiver já aprovada: 200 com `publicacaoDisparada: false` (sem efeito
colateral). Gate re-disparado: UNIQUE constraint absorve silenciosamente.

## Criterios de aceite

- [ ] `./gradlew :trei-service:test --tests "*IdempotencyGuardTest*"` sai 0
- [ ] Teste verifica que `approveCaregiver` chamado duas vezes para mesmo `(noId, locale)` retorna no segundo chamada sem exception e sem nova linha de outbox criada
- [ ] Teste verifica que `DataIntegrityViolationException` em `TreiPublicacaoOutbox.save` é tratada como no-op (não como erro 500)

## Dependencias

- TREI-006 (AutorizacaoService — IdempotencyGuard é integrado aqui)
- TREI-008 (repositories)

## Estimativa

S

## Prioridade

P6
