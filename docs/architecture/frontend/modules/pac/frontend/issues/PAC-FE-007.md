## [PAC-FE-007] PacPacienteClient — HTTP client (list, get, create, update)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/data-access/src/lib/clients/pac-paciente.client.ts` implementa os quatro métodos do cliente HTTP para o recurso `Paciente` (list, get, create, update) com os headers corretos (`X-Empresa-Id`, `Authorization`, `X-Correlation-Id`, `If-Match`, `Idempotency-Key`) conforme SPEC-04 §2 e backend-api-cross-link.md §1/§2.

**Context / Problem statement:**
- Base path `/api/pac/v1/pacientes` (roteado via GWY, descoberto via REG).
- `create` retorna `HttpResponse<Paciente>` (201 com Location + ETag); `update` requer `If-Match: etag`.
- `Idempotency-Key` gerado pelo facade (UUID) e passado para `create` — o client apenas transmite, não gera.
- A interceptor de `X-Empresa-Id` é global (INT-FE-PAC-004); o client não adiciona manualmente.
- Bloqueia: PAC-FE-010 (PacPacienteFacade)

**Approach (execution path):**
1. Criar `libs/pac/data-access/src/lib/clients/pac-paciente.client.ts` com `@Injectable({ providedIn: 'root' })`.
2. Injetar `HttpClient`.
3. Implementar: `list(page?, size?): Observable<Page<Paciente>>` → `GET /api/pac/v1/pacientes?page=N&size=N`.
4. `get(id): Observable<Paciente>` → `GET /api/pac/v1/pacientes/{id}`.
5. `create(dto, idemKey?): Observable<HttpResponse<Paciente>>` → `POST /api/pac/v1/pacientes` com header `Idempotency-Key` se fornecido; `observe: 'response'` para capturar ETag do header.
6. `update(id, dto, ifMatch): Observable<Paciente>` → `PUT /api/pac/v1/pacientes/{id}` com `If-Match: ifMatch`.
7. Criar `libs/pac/data-access/src/lib/dtos/paciente.dto.ts` com `PacienteCreate`, `PacienteUpdate`, `Paciente`, `Page<T>`.
8. Unit tests com `HttpClientTestingModule`.

**Non-goals:**
- Sem lógica de estado (signals, cache) — responsabilidade do facade (PAC-FE-010).
- Sem retry automático — o facade decide quando retry.
- Sem delete — API não tem DELETE (ADR-006).

**Layer(s):**
frontend / libs/pac/data-access

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-04-data-access.md` (§1/§2)
- `docs/architecture/frontend/modules/pac/frontend/contracts/backend-api-cross-link.md` (§2)

**Architecture alignment:**
- RxJS `HttpClient` (sem signals no client)
- Tag Nx: `scope:pac`, `type:data-access`

**Implementation checklist (by file):**

**Lib: libs/pac/data-access**
- [ ] `libs/pac/data-access/src/lib/clients/pac-paciente.client.ts`
- [ ] `libs/pac/data-access/src/lib/dtos/paciente.dto.ts`
- [ ] `libs/pac/data-access/src/lib/clients/pac-paciente.client.spec.ts`

**Tests**
- [ ] `npx nx test pac-data-access` PASS
- [ ] `npx nx lint pac-data-access` PASS

**Milestone:**
Wave 2 / P2 Data Access

**Labels:**
type:feature
layer:frontend
scope:pac
priority:P2

**Acceptance criteria:**
- `npx nx test pac-data-access` PASS: `list()` dispara `GET /api/pac/v1/pacientes`.
- `npx nx test pac-data-access` PASS: `create(dto, 'key-123')` envia header `Idempotency-Key: key-123`.
- `npx nx test pac-data-access` PASS: `update(id, dto, 'etag-abc')` envia `If-Match: etag-abc`.
- `npx nx build pac-data-access` PASS.

**Dependencies:**
- INT-FE-PAC-004 (interceptors HTTP — X-Empresa-Id, Authorization, X-Correlation-Id já adicionados globalmente)

**Data impact:**
Nenhum FE-side (write vai para BE).

**API / Contract impact:**
- Consome `POST /api/pac/v1/pacientes` (201), `GET /api/pac/v1/pacientes/{id}`, `PUT /api/pac/v1/pacientes/{id}` (If-Match).

**Testing / Evidence required:**
- `HttpClientTestingModule` mock: verify URL, headers, method em cada operação.

**Risks / Notes:**
- `Page<T>` deve ser compatível com o schema BE (`content[]`, `totalElements`, `number`, `size`); verificar no barrier `api-contract.md §3.1` antes de implementar o DTO.
