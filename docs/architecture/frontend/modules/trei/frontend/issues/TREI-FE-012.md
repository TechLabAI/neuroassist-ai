## [TRÊS-FE-012] Error mapping ProblemDetail -> AppError (all SPEC-07 codes + correlationId attachment G5)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/trei/data-access/src/lib/trei-error-mapper.ts` implementa `TreiErrorMapper` que mapeia todos os `ProblemDetail` RFC 7807 do `trei-service` (tabela SPEC-07 §2) para `AppError` com mensagem product-level + `correlationId` anexado para observabilidade — o `correlationId` nunca é renderizado como id bruto ao utilizador (G5).

**Context / Problem statement:**
- O `trei-service` retorna `application/problem+json` com campos `type`, `title`, `status`, `detail`, `instance`, `correlationId` (api-contract §3).
- O FE mapeia o sufixo `type` para um `AppError` code e uma mensagem em pt-BR (SPEC-07 §2) — o detalhe interno de Anthropic/ElevenLabs/ES nunca aparece na UI (SPEC-07 §5).
- `correlationId` é echado pelo GWY via `X-Correlation-Id` (cross-link §3) e deve ser armazenado no `AppError` para logging/observabilidade — mas nunca exibido como texto cru (G5, SPEC-07 §4).
- A aprovação da cuidadora **não tem 409** (idempotente) — o mapper não deve tratar `409 already-published` como erro de aprovação (reconciliado em SPEC-04 §6).
- Bloqueia: TREI-FE-010/011 (facades usam este mapper no catch de HTTP errors).

**Approach (execution path):**
1. Criar `libs/trei/data-access/src/lib/trei-error-mapper.ts`.
2. Definir `TreiProblemType` enum/const com todos os sufixos de `api-contract §3`: `llm-unavailable`, `invalid-request`, `no-biometric-ingress`, `unauthenticated`, `session-revoked`, `forbidden-scope`, `patient-not-found`, `node-not-found`, `already-published`, `gate-not-satisfied`, `tts-unavailable`.
3. Implementar `TreiErrorMapper.map(httpError: HttpErrorResponse): AppError`:
   - Extrair `type` suffix, `correlationId` do body.
   - Mapear para `AppError` com `code`, `message` (pt-BR product-level), `retriable`, `correlationId`.
   - `correlationId` vai no `AppError.metadata.correlationId` — nunca no campo `message`.
4. Casos especiais:
   - `patient-not-found 404` → `AppError { code: 'PATIENT_NOT_FOUND', message: 'Paciente não encontrado nesta empresa.', retriable: false }` (G4).
   - `unauthenticated/session-revoked` → delegados para `@neuroassist/shared/access` (não mapeados como AppError de UI).
   - `gate-not-satisfied` e `tts-unavailable` → status informacional, não AppError (FE não actua sobre eles).
5. Exportar do barrel de `trei-data-access`.

**Non-goals:**
- Não renderizar mensagens de erro nos componentes (responsabilidade das features e do shared/feedback).
- Não tratar `unauthenticated` (delegado ao shared access layer).

**Layer(s):**
frontend / libs/trei/data-access

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-07-error-handling.md` (§1/§2/§4)
- `docs/architecture/frontend/modules/trei/frontend/contracts/backend-api-cross-link.md` (§4)

**Architecture alignment:**
- `AppError` de `@neuroassist/shared/error-model`
- Tag Nx: `scope:trei`, `type:data-access`

**Implementation checklist (by file)**

**libs/trei/data-access**
- [ ] `libs/trei/data-access/src/lib/trei-error-mapper.ts`
- [ ] Mapeamento de todos os tipos SPEC-07 §2
- [ ] `correlationId` em `AppError.metadata` (nunca em `message`)
- [ ] `patient-not-found` → `PATIENT_NOT_FOUND` (G4)
- [ ] `already-published` 409 → toast code (não como erro de aprovação)

**Tests**
- [ ] `nx test trei-data-access` PASS
- [ ] Mapper: `llm-unavailable 503` → `retriable: true`, `message` não contém "Anthropic"
- [ ] Mapper: `patient-not-found 404` → `code: 'PATIENT_NOT_FOUND'` (G4)
- [ ] Mapper: `correlationId` presente em `AppError.metadata.correlationId` (G5)
- [ ] Mapper: mensagem para qualquer tipo não contém "Anthropic", "ElevenLabs", ou "Elasticsearch"
- [ ] `nx lint trei-data-access` PASS

**Milestone:**
Wave 2 / Data Access

**Labels:**
type:feature
layer:frontend
scope:trei
priority:P2

**Acceptance criteria:**
- `npx nx test trei-data-access` PASS
- `npx nx lint trei-data-access` PASS
- Nenhuma mensagem mapeada contém nome de serviço interno (G5 isolation)
- `correlationId` disponível em `AppError.metadata` para logging
- Teste: `map(llmUnavailableError).retriable === true`

**Dependencies:**
- INT-FE-TREI-003 (`@neuroassist/shared/error-model` — AppError interface)
- INT-FE-TREI-002 (GWY — X-Correlation-Id echoing)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Consome o contrato de erros RFC 7807 de `trei-service` (api-contract §3)

**Testing / Evidence required:**
- Snapshot de todas as mensagens mapeadas em tabela (pt-BR, sem internals)
- Teste: `map(patientNotFoundError).code === 'PATIENT_NOT_FOUND'` (G4)

**Risks / Notes:**
- G5: confirmar que `@neuroassist/shared/error-model AppError` tem campo `metadata: Record<string,unknown>` para `correlationId` — senão, propor extensão antes de implementar.
- `session-revoked 401` pode ser tratado pelo shared HTTP interceptor antes de chegar ao mapper — coordenar com INT-FE-TREI-001.
