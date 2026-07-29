## [INT-FE-EVNT-003] Integração com evnt-service — POST /events/{id}/confirmation

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`EvntClient.confirm()` integra corretamente com `POST /api/v1/events/{eventoId}/confirmation`, tratando todos os status codes esperados (200, 409, 403, 502) e validando o contrato de request/response.

**Context / Problem statement:**
- Path reconciliado em M1.5: o endpoint é `/confirmation` (não `/confirm`).
- O body deve ser `{ verdict: 'real' | 'falso_positivo' }` (`ConfirmationRequestDto`).
- Response 200: `ConfirmationResponseDto` com `confirmadoPor`, `confirmadoAt` — usado para atualizar a trail de confirmação.
- 409: body RFC 7807 com `type: '/errors/feedback-terminal'`, `confirmadoPor`, `confirmadoAt`.

**Approach (execution path):**
1) Confirmar path `/api/v1/events/{eventoId}/confirmation` em `EvntClient.confirm()`.
2) Testar com mocks de contrato fixado do `api-contract.md` (não divergir do BE).
3) Mapear 409 para `AppError({ kind: 'Conflict', ... })` com campos de `confirmadoPor`/`confirmadoAt` preservados.
4) Confirmar que `access-http-base` adiciona `X-Empresa-Id` nesta chamada.

**Non-goals:**
- Tratamento de UI dos erros (FE-EVNT-012).

**Layer(s):**
frontend / libs/evnt/data-access → evnt-service

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-04-data-access.md` (§1, §5)
- `docs/architecture/backend/services/evnt-service/contracts/api-contract.md` (§3.4)

**Architecture alignment:**
- Path: `/api/v1/events/{eventoId}/confirmation` (not `/confirm`)
- Tag Nx: `scope:evnt`, `type:data-access`

**Implementation checklist (by file):**

**Lib data-access**
- [ ] `EvntClient.confirm(id, verdict)`: path correto `/confirmation`
- [ ] Body: `{ verdict }` (`ConfirmationRequestDto`)
- [ ] 200 → `ConfirmationResponseDto` com `confirmadoPor` + `confirmadoAt`
- [ ] 409 → `AppError({ kind: 'Conflict' })` com campos de `confirmadoPor`/`confirmadoAt`
- [ ] `X-Empresa-Id` presente (via `access-http-base`)

**Tests**
- [ ] `nx test evnt-data-access` PASS
- [ ] Unit: `confirm('evt-1', 'real')` → POST para `/api/v1/events/evt-1/confirmation`
- [ ] Unit: 409 → `AppError.kind === 'Conflict'` com `confirmadoPor` preservado
- [ ] `nx lint evnt-data-access` PASS

**Milestone:**
Wave P4 / Hub integrations

**Labels:**
type:integration
layer:frontend
scope:evnt
priority:P4

**Acceptance criteria:**
- `npx nx test evnt-data-access` PASS
- Path `/confirmation` (não `/confirm`) — regressão de naming prevenida

**Dependencies:**
- FE-EVNT-007 (EvntClient)
- INT-FE-EVNT-006 (ProblemDetail → AppError mapper)

**Data impact:**
- Nenhum

**API / Contract impact:**
- `POST /api/v1/events/{eventoId}/confirmation` — contrato autoritativo BE

**Testing / Evidence required:**
- Unit: mock 409 com body → `AppError` com `confirmadoPor='Ana'` preservado

**Risks / Notes:**
- Path `/confirm` vs `/confirmation` foi um gap real do M1/M1.5 — teste explícito previne regressão.
