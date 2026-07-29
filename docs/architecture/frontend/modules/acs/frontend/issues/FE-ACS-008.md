## [FE-ACS-008] AcsCuidadorClient + Cuidador DTOs

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/acs/data-access` provides `AcsCuidadorClient` implementing four operations (list, get, create, deactivate)
aligned to the BE barrier (`/api/v1/acs/caregivers`). All calls carry `X-Empresa-Id` via interceptor.
DTOs match the BE `api-contract.md §4` shapes; `senha` is write-only (never echoed from response).

**Context / Problem statement:**
- SPEC-04 §2 defines all four client signatures; the BE barrier (`api-contract.md §4`) is authoritative.
- `lista` is paginated (`Page<Cuidador>`); the FE maps to a flat `CuidadorRowVM[]` in the facade.
- `create` carries `Idempotency-Key` per user-initiated invite — the key is managed by `AcsCuidadorFacade`
  (FE-ACS-010), not the client.
- `deactivate` is `POST /caregivers/{id}/deactivation` (soft, idempotent) — NOT a PATCH or DELETE.
- Bloqueia: FE-ACS-010 (facade depends on this client)

**Approach (execution path):**
1. Create `libs/acs/data-access/src/lib/cuidador/cuidador.dto.ts`: `CuidadorCreate`, `Cuidador`,
   `Papel`, `CuidadorStatus`, `Page<T>` generic.
2. Create `libs/acs/data-access/src/lib/cuidador/acs-cuidador.client.ts` with `HttpClient`:
   - `list(papel?, status?, page?, size?)` → `Observable<Page<Cuidador>>`
   - `get(id)` → `Observable<Cuidador>`
   - `create(dto, idemKey?)` → `Observable<HttpResponse<Cuidador>>` (reads `Location` header)
   - `deactivate(id)` → `Observable<Cuidador>` (POST …/deactivation)
3. Ensure `senha` is only in `CuidadorCreate`; the `Cuidador` response DTO never has a `senha` field.
4. Unit tests with `HttpClientTestingModule`: assert correct paths, methods, and headers.

**Non-goals:**
- No facade logic (FE-ACS-010).
- No idempotency-key generation (facade responsibility).

**Layer(s):**
frontend / libs/acs/data-access

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-04-data-access.md` (§2 AcsCuidadorClient)
- `docs/architecture/frontend/modules/acs/frontend/contracts/backend-api-cross-link.md` (§2 Roster + Editor rows)

**Architecture alignment:**
- Pure `HttpClient` calls; no caching; no retry logic (facade handles error + retry UI)
- Tags Nx: `scope:acs`, `type:data-access`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/data-access/`
- 🔲 `src/lib/cuidador/cuidador.dto.ts` — CuidadorCreate, Cuidador, Papel, CuidadorStatus, Page<T>
- 🔲 `src/lib/cuidador/acs-cuidador.client.ts` — list / get / create / deactivate
- 🔲 `src/index.ts` — re-exports

**Tests**
- 🔲 `src/lib/cuidador/acs-cuidador.client.spec.ts` — 4 operations; path/method assertions; deactivate = POST …/deactivation
- 🔲 `nx test acs-data-access` PASS
- 🔲 `nx lint acs-data-access` PASS

**Milestone:**
Wave 2 / P2 Data Access

**Labels:**
type:feature
layer:frontend
scope:acs
priority:P2

**Acceptance criteria:**
- `npx nx test acs-data-access` PASS
- `npx nx lint acs-data-access` PASS
- `deactivate(id)` emite `POST /api/v1/acs/caregivers/{id}/deactivation`
- `create()` retorna `HttpResponse<Cuidador>` (preserva `Location` header)
- `Cuidador` response DTO sem campo `senha`

**Dependencies:**
- FE-ACS-003 (lib skeleton)
- INT-FE-ACS-001 (interceptores de header)

**Data impact:**
- Nenhum (FE não persiste)

**API / Contract impact:**
- `GET /api/v1/acs/caregivers` (paginado, X-Empresa-Id)
- `POST /api/v1/acs/caregivers` (cria, Idempotency-Key)
- `POST /api/v1/acs/caregivers/{id}/deactivation` (soft)

**Testing / Evidence required:**
- Testes de HTTP paths e métodos passando

**Risks / Notes:**
- A paginação (`Page<T>`) pode ter um shape diferente do padrão Spring (`content[]` + `totalElements`).
  Validar contra `api-contract.md §4.1` antes de definir o DTO.
