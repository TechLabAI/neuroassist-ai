## [TREI-FE-007] TrainingSessionHttpAdapter (POST /sessoes, POST /sessoes/{id}/propostas, GET /health)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/trei/data-access/src/lib/training-session-http.adapter.ts` implementa `TrainingSessionHttpAdapter` com `start(pacienteId, localeAutoria)`, `propose(sessaoId, instrucao, parentNoId?, tipo)`, e `readiness()` — todos os requests levam o JWT caregiver via o shared HTTP interceptor, nunca chamam Anthropic/ElevenLabs/ES directamente.

**Context / Problem statement:**
- O adapter é a camada de tradução entre a facade e o `HttpClient` de Angular; a facade nunca toca `HttpClient` directamente (doc 05 §2).
- `POST /sessoes` retorna `{ sessaoId, llmModelId, aiDisclosed }` — campos que alimentam o `TrainingSessionVM` (SPEC-04 §1).
- `POST /sessoes/{sessaoId}/propostas` retorna `{ propostas[], aiDisclosed }` — o array de propostas é o output da conversa (SPEC-04 §2).
- `GET /health` retorna `{ embedModelId, degradedCapabilities[] }` — usado para o G5 invariant readiness check (SPEC-04 §1, cross-link §3).
- A base URL é `/api/v1/trei` (via GWY); o JWT é injectado pelo shared `AuthInterceptor`.
- Bloqueia: TREI-FE-010 (TrainingSessionFacade usa este adapter).

**Approach (execution path):**
1. Criar `libs/trei/data-access/src/lib/training-session-http.adapter.ts`.
2. Injectable; inject `HttpClient`.
3. `start(pacienteId: string, localeAutoria: Locale): Observable<StartSessionResponseDto>`:
   - `POST /api/v1/trei/sessoes` com `{ pacienteId, localeAutoria }`.
   - Retorna `{ sessaoId, llmModelId, aiDisclosed }`.
4. `propose(sessaoId: string, instrucao: string, parentNoId?: string, tipo?: 'frase'|'categoria'): Observable<ProposeResponseDto>`:
   - `POST /api/v1/trei/sessoes/{sessaoId}/propostas` com `{ instrucao, parentNoId, tipo }`.
   - Retorna `{ propostas[], aiDisclosed }`.
5. `readiness(): Observable<HealthResponseDto>`:
   - `GET /api/v1/trei/health`.
   - Retorna `{ embedModelId, degradedCapabilities }`.
6. Criar os DTOs correspondentes em `training-session.dto.ts`.

**Non-goals:**
- Não mapear erros HTTP aqui — o mapeamento ProblemDetail→AppError é feito em TREI-FE-012 via HTTP interceptor ou operador RxJS na facade.
- Não incluir o adapter do paciente (`POST /aprovacoes/paciente` — INV-1).

**Layer(s):**
frontend / libs/trei/data-access

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-04-data-access.md` (§1/§2)
- `docs/architecture/frontend/modules/trei/frontend/contracts/backend-api-cross-link.md` (tabela §1)
- `docs/architecture/backend/services/trei-service/contracts/api-contract.md` (§2.1/§2.2/§2.9)

**Architecture alignment:**
- `Observable<T>` retornado; sem `subscribe` no adapter
- Tag Nx: `scope:trei`, `type:data-access`

**Implementation checklist (by file)**

**libs/trei/data-access**
- [ ] `libs/trei/data-access/src/lib/training-session-http.adapter.ts`
- [ ] `libs/trei/data-access/src/lib/dtos/training-session.dto.ts` (StartSessionResponseDto, ProposeResponseDto, HealthResponseDto)

**Tests**
- [ ] `nx test trei-data-access` PASS — testes com `HttpClientTestingModule`
- [ ] `start()` envia `POST /api/v1/trei/sessoes` com body correcto
- [ ] `propose()` envia `POST /api/v1/trei/sessoes/{sessaoId}/propostas`
- [ ] `readiness()` envia `GET /api/v1/trei/health`
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
- Nenhum import de `@anthropic-ai/*`, `elevenlabs`, ou `@elastic/elasticsearch` em `libs/trei/**`
- Nenhum `subscribe()` dentro do adapter

**Dependencies:**
- TREI-FE-009 (DTOs + tipos Locale)

**Data impact:**
- Nenhum (adapter HTTP não persiste)

**API / Contract impact:**
- `POST /api/v1/trei/sessoes`
- `POST /api/v1/trei/sessoes/{sessaoId}/propostas`
- `GET /api/v1/trei/health`

**Testing / Evidence required:**
- `HttpTestingController.expectOne('/api/v1/trei/sessoes')` no teste de `start()`
- Teste de `readiness()`: resposta com `{ degradedCapabilities: ['llm'] }` propagada correctamente

**Risks / Notes:**
- Confirmar o campo exacto de locale na request body de `/sessoes`: `localeAutoria` ou `locale` — verbatim do `api-contract.md §2.1`.
