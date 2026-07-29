## [PAC-FE-009] TiposGestoReadClient — GEST cross-read (list, get, requestNew)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/data-access/src/lib/clients/tipos-gesto-read.client.ts` implementa o cliente read-only para o recurso `TipoGesto` (GEST-owned) via endpoint `GET /api/gest/v1/tipos-gesto` roteado pelo GWY, incluindo `requestNew(nome)` para o fluxo inline "+ novo tipo" do `GesturePicker`.

**Context / Problem statement:**
- PAC consome TipoGesto apenas para leitura (pick gesture primitives no editor); GEST é o owner do lifecycle de tipos. PAC **nunca autora** primitivos diretamente — `requestNew` apenas delega ao GEST.
- O client se conecta via GWY (não diretamente ao gest-service), portanto a base path é `/api/gest/v1/tipos-gesto` (SPEC-04 §1 nota).
- `requestNew(nome)` retorna o `TipoGesto` criado ou pending-training; o facade seleciona apenas o `id` retornado (SPEC-04 §3 — "select only the returned primitivo id").
- Feature flag `pac.inlineNewTipoGesto=off` oculta o affordance (PAC-FE-001); quando off, `requestNew` nunca é chamado.
- Bloqueia: PAC-FE-012 (TiposGestoReadFacade), INT-FE-PAC-006

**Approach (execution path):**
1. Criar `libs/pac/data-access/src/lib/clients/tipos-gesto-read.client.ts`.
2. `list(): Observable<TipoGesto[]>` → `GET /api/gest/v1/tipos-gesto`.
3. `get(id): Observable<TipoGesto>` → `GET /api/gest/v1/tipos-gesto/{id}`.
4. `requestNew(nome: string): Observable<TipoGesto>` → `POST /api/gest/v1/tipos-gesto` com `{ nome }`.
5. Criar interface `TipoGesto { id: string; nome: string; estado: 'supported' | 'pending-training' | string }` no DTO (SPEC-03 §2).
6. Unit tests com `HttpClientTestingModule`.

**Non-goals:**
- Sem write de `TipoGesto` além de `requestNew` (delegate ao GEST; PAC não cria primitivos de forma autônoma).
- Sem import de qualquer lib `scope:gest` — o client usa apenas a URL via GWY.

**Layer(s):**
frontend / libs/pac/data-access

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-04-data-access.md` (§1 tabela — TipoGesto row)

**Architecture alignment:**
- Tag Nx: `scope:pac`, `type:data-access`
- Sem import de `scope:gest`

**Implementation checklist (by file):**

**Lib: libs/pac/data-access**
- [ ] `libs/pac/data-access/src/lib/clients/tipos-gesto-read.client.ts`
- [ ] `libs/pac/data-access/src/lib/dtos/tipo-gesto.dto.ts` (interface TipoGesto)
- [ ] `libs/pac/data-access/src/lib/clients/tipos-gesto-read.client.spec.ts`

**Tests**
- [ ] `npx nx test pac-data-access` PASS
- [ ] `npx nx lint pac-data-access` PASS (no `scope:gest` import)

**Milestone:**
Wave 2 / P2 Data Access

**Labels:**
type:feature
layer:frontend
scope:pac
priority:P2

**Acceptance criteria:**
- `npx nx test pac-data-access` PASS: `list()` → `GET /api/gest/v1/tipos-gesto`.
- `npx nx test pac-data-access` PASS: `requestNew('Piscar')` → `POST /api/gest/v1/tipos-gesto` com body `{ nome: 'Piscar' }`.
- `npx nx lint pac-data-access` PASS (nenhum import `scope:gest`).

**Dependencies:**
- INT-FE-PAC-004 (interceptors — X-Empresa-Id, Authorization propagados via GWY)
- INT-FE-PAC-006 (wiring end-to-end com GEST via GWY)

**Data impact:**
Nenhum FE-side.

**API / Contract impact:**
- Consome `GET /api/gest/v1/tipos-gesto` e `POST /api/gest/v1/tipos-gesto` roteados via GWY → gest-service.

**Testing / Evidence required:**
- `HttpClientTestingModule`: URL e method assertados.

**Risks / Notes:**
- O GEST `tipos-gesto` endpoint pode não estar disponível no MVP inicial; o facade deve degradar graciosamente (lista vazia) se o GET falhar, permitindo ao editor continuar com tipos pré-existentes.
