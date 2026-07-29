## [PAC-FE-008] PacComandoClient — lifecycle HTTP client completo (create, updateDraft, validate, publish, recalibrate, retire)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/data-access/src/lib/clients/pac-comando.client.ts` implementa todos os 8 métodos do cliente HTTP para o recurso `Comando` (list, get, create, updateDraft, validate, publish, recalibrate, retire) alinhados ao lifecycle history-preserving ADR-006, sem DELETE, com If-Match em todas as mutações.

**Context / Problem statement:**
- O ciclo de vida é: `drafted → validated → published → (recalibrated ↻) → retired`. Não há DELETE.
- Cada transição usa um endpoint de ação (`:validate`, `:publish`, `:recalibrate`, `:retire`) e requer `If-Match: etag`.
- `publish` tem semântica especial: fail-closed para `urgencia=emergencia` (422 emergency-not-detectable, 503 eligibility-gate-unavailable); advisory para `rotina` (200 com `warnings[]`). A UI trata a diferença (SPEC-07); o client apenas retorna o `Observable<Comando>`.
- `updateDraft` só é válido enquanto o estado é `drafted`; a UI enforça isso antes de chamar (SPEC-04 §2 nota).
- Bloqueia: PAC-FE-011 (PacComandoFacade)

**Approach (execution path):**
1. Criar `libs/pac/data-access/src/lib/clients/pac-comando.client.ts`.
2. Base path: `/api/pac/v1/pacientes/{pid}/comandos`.
3. `list(pid, estado?): Observable<Comando[]>` → `GET ...?estado=X,Y` (default excluindo retired).
4. `get(pid, cid): Observable<Comando>` → `GET .../comandos/{cid}`.
5. `create(pid, draft, idemKey?): Observable<HttpResponse<Comando>>` → `POST .../comandos` com `Idempotency-Key`; `observe: 'response'` para ETag.
6. `updateDraft(pid, cid, draft, ifMatch): Observable<Comando>` → `PUT .../comandos/{cid}` com `If-Match`.
7. `validate(pid, cid, ifMatch): Observable<Comando>` → `POST .../comandos/{cid}:validate` com `If-Match`.
8. `publish(pid, cid, ifMatch): Observable<Comando>` → `POST .../comandos/{cid}:publish` com `If-Match`.
9. `recalibrate(pid, cid, tuning, ifMatch): Observable<Comando>` → `POST .../comandos/{cid}:recalibrate` com `If-Match` + body `ComandoTuning`.
10. `retire(pid, cid, ifMatch): Observable<Comando>` → `POST .../comandos/{cid}:retire` com `If-Match`.
11. Criar `libs/pac/data-access/src/lib/dtos/comando.dto.ts` com `ComandoCreate`, `ComandoIdentityEdit`, `ComandoTuning`, `Comando` (DTO alinhado ao BE api-contract §3/§4).
12. Unit tests com `HttpClientTestingModule` para todos os 8 métodos.

**Non-goals:**
- Sem lógica de retry, cache ou signals — tudo no facade.
- Sem lógica de seleção de endpoint por `estado` — o facade verifica `estado` antes de chamar o método correto.

**Layer(s):**
frontend / libs/pac/data-access

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-04-data-access.md` (§2)
- `docs/architecture/frontend/modules/pac/frontend/contracts/backend-api-cross-link.md` (§2)
- `docs/architecture/backend/services/pac-service/contracts/api-contract.md` (§4 lifecycle)

**Architecture alignment:**
- RxJS `HttpClient`
- Tag Nx: `scope:pac`, `type:data-access`
- Sem `DELETE` (ADR-006 history-preserving)

**Implementation checklist (by file):**

**Lib: libs/pac/data-access**
- [ ] `libs/pac/data-access/src/lib/clients/pac-comando.client.ts`
- [ ] `libs/pac/data-access/src/lib/dtos/comando.dto.ts`
- [ ] `libs/pac/data-access/src/lib/clients/pac-comando.client.spec.ts`

**Tests**
- [ ] `npx nx test pac-data-access` PASS (8 métodos unitizados)
- [ ] `npx nx lint pac-data-access` PASS

**Milestone:**
Wave 2 / P2 Data Access

**Labels:**
type:feature
layer:frontend
scope:pac
priority:P2

**Acceptance criteria:**
- `npx nx test pac-data-access` PASS: `publish(pid, cid, etag)` dispara `POST .../comandos/{cid}:publish` com `If-Match: etag`.
- `npx nx test pac-data-access` PASS: `retire(pid, cid, etag)` NÃO dispara DELETE.
- `npx nx test pac-data-access` PASS: `create(pid, draft, 'key')` envia `Idempotency-Key: key`.
- Nenhum método expõe DELETE endpoint (lint check ou assert em teste).
- `npx nx build pac-data-access` PASS.

**Dependencies:**
- PAC-FE-013 (DTOs e View Models — interfaces Comando, ComandoCreate, etc.)
- INT-FE-PAC-004 (interceptors HTTP)

**Data impact:**
Nenhum FE-side.

**API / Contract impact:**
- Consome 10 endpoints de `pac-service`: GET/POST/PUT `.../comandos` + 4 lifecycle POST actions.

**Testing / Evidence required:**
- `HttpClientTestingModule`: URL e method exatos para cada operação, If-Match header em mutações.

**Risks / Notes:**
- O DTO `Comando.estado` deve ser union type exato: `'drafted' | 'validated' | 'published' | 'retired'`; o facade verifica antes de routing de call.
- `publish` pode retornar `warnings: []` no body (rotina advisory) — o DTO deve incluir o campo opcional.
