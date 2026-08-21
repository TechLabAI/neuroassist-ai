# HIST-021 -- BDD step definitions: hash-chain-verification + history-query + security-and-auth features

## Contexto

Step definitions para as três features restantes do Cucumber. Cobrem os caminhos de
verificação de cadeia, consulta de histórico (filtros e paginação) e segurança (401/403,
tenant mismatch, ACS fail-closed). `security-and-auth.feature` foi adicionada na fase
de specs-validate (M2.5) e fecha os 5 BDD gaps identificados.

## Spec de referencia

- SPEC-08 (BDD Test Scenarios)
- SPEC-VAL-bdd-coverage-report (gaps fechados: occurredAt future, correctsFactId, GET by id cross-tenant, 401/403, X-Correlation-Id)
- `tests/cucumber/hash-chain-verification.feature`
- `tests/cucumber/history-query.feature`
- `tests/cucumber/security-and-auth.feature`

## Escopo

**`src/test/kotlin/com/neuroassist/hist/bdd/`:**
- `HashChainVerificationSteps`:
  - Given: N records inseridos em sequência para um streamKey.
  - Then: GET /history/verify retorna `status:"intact"`, `recordsVerified=N`.
  - Cenário: um record adulterado (hash manualmente alterado via SQL direto no Testcontainers) → `status:"broken"`, `brokenAtSequenceNo=K`.
  - Cenário: stream vazio → `status:"empty"`, `recordsVerified=0`.
- `HistoryQuerySteps`:
  - Cenários: filtro por factType, filtro por subjectId+subjectType, paginação, order=asc, feedback-verdict query (factType=evento.confirmado).
  - Cenário: `subjectId` sem `subjectType` → 400.
  - Cenário: `from > to` → 400.
- `SecurityAndAuthSteps`:
  - Sem token → 401.
  - Token com scope errado (hist:read em POST) → 403.
  - Token com tenant divergente do body → 403 /errors/tenant-mismatch.
  - X-Correlation-Id ausente → gerado e ecoado.
  - ACS indisponível (WireMock sem resposta) em leitura de cuidadora → 503.
  - GET /audit-facts/{id} de outro tenant → 404 (sem vazamento).

## Resultado esperado

Todos os cenários das 3 features passam. O relatório Cucumber mostra 100% dos cenários
passando, incluindo os 5 gaps fechados na M2.5.

## Criterios de aceite

- [ ] `hash-chain-verification.feature`: cenários intact, broken, empty todos passam.
- [ ] `history-query.feature`: filtros, paginação, order, feedback-verdict, erros 400 todos passam.
- [ ] `security-and-auth.feature`: 401, 403, tenant-mismatch, X-Correlation-Id, 503 ACS, cross-tenant 404 todos passam.
- [ ] Cenário de adulteração de hash usa SQL direto no Testcontainers (bypass de GRANT para o papel de teste).
- [ ] Zero cenários `@Pending` nos relatórios.

## Dependencias

- HIST-008 (ChainVerificationService + HistoryQueryService implementados)
- HIST-012 (ACS adapter com WireMock configurado)
- HIST-013 (history controller disponível)
- HIST-014, HIST-015, HIST-016 (security, tenant, LGPD)
- HIST-020 (CucumberSpringConfiguration + infraestrutura de teste compartilhada)

## Estimativa

M

## Prioridade

P8
