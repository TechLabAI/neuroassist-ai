## [TREI-FE-008] ProposalHttpAdapter (POST /nos, GET /nos/{id}, DELETE /nos/{id}, POST /nos/{id}/aprovacoes/cuidadora, GET /nos/{id}/publicacao)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/trei/data-access/src/lib/proposal-http.adapter.ts` implementa `ProposalHttpAdapter` com as 5 operações sobre nós rascunho: `materialize`, `inspect`, `discard`, `approveCuidadora`, e `publicationStatus` — sem nenhum método para o endpoint do paciente (INV-1).

**Context / Problem statement:**
- `POST /nos` (materialize) converte uma proposta inerte (`propostaId`) num nó rascunho (`noId`) — é obrigatório antes de qualquer aprovação (SPEC-04 §3).
- `POST /nos/{noId}/aprovacoes/cuidadora { locale }` é idempotente (api-contract §2.4): re-submeter a mesma aprovação é um no-op no BE — o FE não deve tratar isso como erro (G3, SPEC-04 §4).
- `DELETE /nos/{noId}?locale=` descarta um rascunho/aguardando — retorna `409 already-published` se o locale já está activo (SPEC-07 §2).
- A ausência estrutural de `POST /nos/{noId}/aprovacoes/paciente` neste adapter é INV-1 (ADR-007).
- Bloqueia: TREI-FE-011 (ProposalReviewFacade usa este adapter).

**Approach (execution path):**
1. Criar `libs/trei/data-access/src/lib/proposal-http.adapter.ts`.
2. `materialize(input: MaterializeInput): Observable<MaterializeResponseDto>`: `POST /api/v1/trei/nos`.
3. `inspect(noId: string): Observable<DraftNodeResponseDto>`: `GET /api/v1/trei/nos/{noId}`.
4. `discard(noId: string, locale: Locale): Observable<void>`: `DELETE /api/v1/trei/nos/{noId}?locale=<locale>`.
5. `approveCuidadora(noId: string, locale: Locale): Observable<ApprovalResponseDto>`: `POST /api/v1/trei/nos/{noId}/aprovacoes/cuidadora` com body `{ locale }`. Retorna `{ confirmadoCuidadora, confirmadoPaciente, subEstado, publicacaoDisparada }`.
6. `publicationStatus(noId: string): Observable<PublicationStatusDto>`: `GET /api/v1/trei/nos/{noId}/publicacao`.
7. Criar DTOs em `proposal.dto.ts`.
8. **Comentário explícito** no arquivo: `// POST /api/v1/trei/nos/{noId}/aprovacoes/paciente is deliberately NOT implemented (INV-1, ADR-007)`.

**Non-goals:**
- Não implementar a lógica de rollback de aprovação aqui (pertence à facade TREI-FE-011).
- Não implementar o endpoint do paciente sob nenhuma circunstância.

**Layer(s):**
frontend / libs/trei/data-access

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-04-data-access.md` (§1/§3/§4)
- `docs/architecture/frontend/modules/trei/frontend/contracts/backend-api-cross-link.md` (§1)
- `docs/architecture/backend/services/trei-service/contracts/api-contract.md` (§2.3..§2.8)

**Architecture alignment:**
- `Observable<T>` sem subscribe
- Tag Nx: `scope:trei`, `type:data-access`

**Implementation checklist (by file)**

**libs/trei/data-access**
- [ ] `libs/trei/data-access/src/lib/proposal-http.adapter.ts`
- [ ] `libs/trei/data-access/src/lib/dtos/proposal.dto.ts` (MaterializeInput, MaterializeResponseDto, DraftNodeResponseDto, ApprovalResponseDto, PublicationStatusDto)
- [ ] Comentário `// POST …/aprovacoes/paciente deliberately NOT implemented (INV-1, ADR-007)`

**Tests**
- [ ] `nx test trei-data-access` PASS
- [ ] `approveCuidadora()` envia `POST /api/v1/trei/nos/{noId}/aprovacoes/cuidadora` com `{ locale }`
- [ ] `discard()` envia `DELETE /api/v1/trei/nos/{noId}?locale=pt`
- [ ] Nenhum método `approvePatient` ou similar existe no adapter
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
- `grep -r 'aprovacoes/paciente' libs/trei/` retorna vazio
- `approveCuidadora()` envia apenas `{ locale }` no body (sem campos de paciente)

**Dependencies:**
- TREI-FE-009 (tipos: Locale, NodeSubEstado)

**Data impact:**
- Nenhum

**API / Contract impact:**
- `POST /api/v1/trei/nos`
- `GET /api/v1/trei/nos/{noId}`
- `DELETE /api/v1/trei/nos/{noId}?locale=`
- `POST /api/v1/trei/nos/{noId}/aprovacoes/cuidadora`
- `GET /api/v1/trei/nos/{noId}/publicacao`

**Testing / Evidence required:**
- Teste: `approveCuidadora('node-1', 'pt')` → request body `{ locale: 'pt' }` (sem `side`, sem `patientId`)
- Teste: `discard('node-1', 'pt')` → `DELETE .../nos/node-1?locale=pt`

**Risks / Notes:**
- Confirmar se `MaterializeInput` requer `propostaId` (obrigatório) e outros campos opcionais no `api-contract §2.3`.
- O `DELETE` retorna 204 (void) — garantir que o `Observable<void>` não emite o body.
