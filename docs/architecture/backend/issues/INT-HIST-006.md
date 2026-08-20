# INT-HIST-006 -- Producer end-to-end contract test: fact ingest → history query → chain verify

## Contexto

Teste de jornada completa (end-to-end dentro do HIST): um produtor simula ingestão de 5
facts → HIST os persiste e encadeia → DASH/FE consulta o histórico → coordenador verifica
a cadeia. Valida a integridade da pipeline write→read→verify sem mocking da lógica interna.

## Spec de referencia

- SPEC-02 (ingestão + chaining)
- SPEC-03 (history query + verify)
- `contracts/integration-contracts.md §1, §2`

## Escopo

**`src/test/kotlin/com/neuroassist/hist/integration/e2e/ProducerToVerifyE2ETest`:**
- Given: Testcontainers PostgreSQL, schema `t_empresa_teste`, WireMock para ACS (retorna lista de patients).
- Step 1: POST 5 audit facts com factTypes mistos (evento, comando, acesso) com JWT `hist:write`.
- Step 2: GET `/history?subjectType=paciente&subjectId=<id>` com JWT `hist:read:all` → retorna 3 records do paciente.
- Step 3: GET `/history?factType=evento.confirmado&factType=evento.descartado` → retorna 2 records.
- Step 4: GET `/history/verify?subjectType=paciente&subjectId=<id>` → `{status:"intact", recordsVerified:3}`.
- Step 5: GET `/audit-facts/{id}` → record individual correto com todos os campos.
- Step 6: segundo POST com mesmo factId do Step 1 → 200 idempotente, sem incremento de `recordsVerified`.
- Assert final: `hist_audit_fact_recorded_total` = 5; `hist_duplicate_fact_ignored_total` = 1; chain `recordsVerified` = 3 (por paciente).

## Resultado esperado

Jornada completa passa sem falhas. Chain verification retorna `intact` após 5 ingestões reais.

## Criterios de aceite

- [ ] 5 POST facts → 5 records persistidos (zero duplicatas).
- [ ] GET history filtra corretamente por subjectType e factType.
- [ ] GET verify → `status:intact` com `recordsVerified` correto.
- [ ] Replay (mesmo factId) → 200, chain NÃO avança.
- [ ] `hist_audit_fact_recorded_total` = 5 após o fluxo.
- [ ] `hist_duplicate_fact_ignored_total` = 1 após o replay.
- [ ] Teste roda em < 30s (Testcontainers já aquecido).

## Dependencias

- HIST-006, HIST-008 (services completos)
- HIST-009, HIST-010, HIST-011 (persistência)
- HIST-012 (ACS stub via WireMock)
- HIST-017 (métricas para assert final)
- INT-HIST-001, INT-HIST-002 (infra de teste já estabelecida)

## Estimativa

M

## Prioridade

P8
