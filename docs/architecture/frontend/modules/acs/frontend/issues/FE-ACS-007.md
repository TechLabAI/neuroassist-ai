## [FE-ACS-007] AcsTenantClient + AcsTenantFacade + Tenant DTOs

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/acs/data-access` exposes `AcsTenantClient` (HTTP client, 3 ops) and `AcsTenantFacade` (signals-first).
The facade handles the provisioning saga: `201 active` writes the empresa context; `202 provisioning` holds a
`TenantSaveState = 'provisioning'` and enables retry via `POST /tenants/{id}/provisioning:retry`.

**Context / Problem statement:**
- SPEC-04 §1 defines the tenant resource, endpoints, and idempotency requirements.
- SPEC-04 §3 defines the facade contract: `tenant = signal<TenantVM|null>`,
  `saveState = signal<TenantSaveState>`, `createTenant(nome, tipo)`, `retry()`.
- Tenant provisioning is NOT tenant-scoped (conductor authority) — no `X-Empresa-Id` on tenant endpoints.
- ACS is the sole writer of `@neuroassist/shared/empresa-context` (writes only on `active` state).
- Bloqueia: FE-ACS-020 (TenantSetupComponent depends on AcsTenantFacade), FE-ACS-005 (bootstrap hydrates tenant)

**Approach (execution path):**
1. Create `libs/acs/data-access/src/lib/tenant/acs-tenant.client.ts` implementing `create`, `get`,
   `retryProvisioning` using `HttpClient`.
2. Define `TenantCreate`, `Tenant`, `TenantVM` DTO interfaces in `tenant/tenant.dto.ts`.
3. Create `libs/acs/data-access/src/lib/tenant/acs-tenant.facade.ts` with signals:
   - `tenant = signal<TenantVM|null>(null).asReadonly()`
   - `saveState = signal<TenantSaveState>('idle').asReadonly()`
4. Implement `createTenant(nome, tipo)`: call client.create with generated `Idempotency-Key`; on `201` write
   empresa-context and set state `'active'`; on `202` set state `'provisioning'`.
5. Implement `retry()`: call client.retryProvisioning; on success write empresa-context + state `'active'`.
6. Error path: set state `'error'` + store `AppError` for `SaveStatusComponent`.
7. Unit tests: mock HttpClient; assert saga branches (201 vs 202 vs error); idempotency-key reuse on retry.

**Non-goals:**
- No auth/credential endpoints (those are auth-session layer).
- No directory endpoints (service-to-service, not FE).

**Layer(s):**
frontend / libs/acs/data-access

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-04-data-access.md` (§1 table row Tenant,
  §2 AcsTenantClient signatures, §3 AcsTenantFacade, §4 idempotency)
- `docs/architecture/frontend/modules/acs/frontend/contracts/backend-api-cross-link.md` (§2 Onboarding rows)

**Architecture alignment:**
- Signals-first; `HttpClient` + `RxJS`; toSignal() for async state
- Idempotency-Key: UUID generated per user-initiated create, reused on retry of same draft
- Tags Nx: `scope:acs`, `type:data-access`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/data-access/`
- 🔲 `src/lib/tenant/tenant.dto.ts` — TenantCreate, Tenant, TenantVM, TenantSaveState
- 🔲 `src/lib/tenant/acs-tenant.client.ts` — create / get / retryProvisioning (Observable)
- 🔲 `src/lib/tenant/acs-tenant.facade.ts` — signals + createTenant + retry
- 🔲 `src/index.ts` — re-exports

**Tests**
- 🔲 `src/lib/tenant/acs-tenant.facade.spec.ts` — 201 active, 202 provisioning, retry, error, idempotency-key
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
- Saga: 202 → `saveState = 'provisioning'`; empresa-context NOT written
- Saga: 201 → `saveState = 'active'`; empresa-context written
- Idempotency-Key reused on retry of same draft (same UUID across calls)
- E2E scenarios tenant-onboarding.feature 1-4 supported by this facade

**Dependencies:**
- FE-ACS-003 (lib skeleton)
- INT-FE-ACS-001 (HTTP interceptors must be in place for X-Correlation-Id)

**Data impact:**
- Escreve `@neuroassist/shared/empresa-context` apenas no estado `active`

**API / Contract impact:**
- `POST /api/v1/acs/tenants` (não tenant-scoped — sem X-Empresa-Id nesta chamada)
- `GET /api/v1/acs/tenants/{id}`
- `POST /api/v1/acs/tenants/{id}/provisioning:retry`

**Testing / Evidence required:**
- Testes de saga (201 vs 202 vs retry vs error) passando
- Mock de empresa-context: escrito só no active

**Risks / Notes:**
- O `Idempotency-Key` deve ser mantido em memória no facade durante a sessão de criação. Se o usuário
  recarregar a página, um novo ID é gerado (OK — o tenant já foi criado com sucesso no backend).
- Se o tenant retornar `suspended` (409), o facade deve mapear para `AppError` via SPEC-07.
