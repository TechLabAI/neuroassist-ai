## [FE-ACS-009] AcsLinkClient + Link DTOs

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/acs/data-access` provides `AcsLinkClient` implementing four operations on the caregiver sub-resource
`/caregivers/{cid}/links`. Linkage is modeled as a caregiver sub-resource (not a flat resource) per the BE
barrier (api-contract §5). The client handles idempotent `link` (200 = existing link, 201 = new) identically.

**Context / Problem statement:**
- SPEC-04 §2 defines `AcsLinkClient` signatures; `listForPatient` is a client-side join (no direct BE endpoint
  for per-patient link listing on the admin surface — per-patient routing lives in the directory, not FE).
- `revoke` is `DELETE /caregivers/{cid}/links/{linkId}` (soft; BE returns 200 with the revoked link).
- `link` returns `200` (existing) or `201` (new) — facade treats both as success (SPEC-04 §4).
- Bloqueia: FE-ACS-011 (AcsLinkFacade depends on this client)

**Approach (execution path):**
1. Create `libs/acs/data-access/src/lib/link/link.dto.ts`: `CaregiverPatientLink`, `LinkStatus`, `LinkCreate`.
2. Create `libs/acs/data-access/src/lib/link/acs-link.client.ts` with `HttpClient`:
   - `listForCaregiver(cid, status?)` → `Observable<CaregiverPatientLink[]>` (GET …/links)
   - `listForPatient(pid)` → calls `listForCaregiver` for all caregivers then filters by pid (client-side join)
   - `link(cid, pid)` → `Observable<HttpResponse<CaregiverPatientLink>>` (POST …/links)
   - `revoke(cid, linkId)` → `Observable<CaregiverPatientLink>` (DELETE …/links/{linkId})
3. Note: `listForPatient` requires the full roster to be available; in the facade it will receive the roster
   signal as input to join against.
4. Unit tests: assert path patterns, method types, and 200/201 distinction on `link`.

**Non-goals:**
- No facade logic (FE-ACS-011).
- No coverage derivation (CoverageFacade, FE-ACS-012).
- The directory surface `GET /directory/**` is NOT implemented here (service-to-service only).

**Layer(s):**
frontend / libs/acs/data-access

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-04-data-access.md` (§2 AcsLinkClient)
- `docs/architecture/frontend/modules/acs/frontend/contracts/backend-api-cross-link.md` (§2 Linkage rows)

**Architecture alignment:**
- Pure `HttpClient`; revoke = soft DELETE returning body; 200 ≡ 201 on link
- Tags Nx: `scope:acs`, `type:data-access`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/data-access/`
- 🔲 `src/lib/link/link.dto.ts` — CaregiverPatientLink, LinkStatus, LinkCreate
- 🔲 `src/lib/link/acs-link.client.ts` — listForCaregiver / listForPatient / link / revoke
- 🔲 `src/index.ts` — re-exports

**Tests**
- 🔲 `src/lib/link/acs-link.client.spec.ts` — path assertions; revoke = DELETE; link 200=201 identity
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
- `revoke(cid, linkId)` emite `DELETE /api/v1/acs/caregivers/{cid}/links/{linkId}`
- `link(cid, pid)` trata 200 e 201 como sucesso idêntico
- `listForPatient` não chama nenhum endpoint direto de "links por paciente" (faz client-side join)

**Dependencies:**
- FE-ACS-003 (lib skeleton)
- FE-ACS-008 (linkForCaregiver precisa do roster; coordenado via facade)
- INT-FE-ACS-001 (interceptores de header)

**Data impact:**
- Nenhum (FE não persiste)

**API / Contract impact:**
- `GET /api/v1/acs/caregivers/{cid}/links`
- `POST /api/v1/acs/caregivers/{cid}/links`
- `DELETE /api/v1/acs/caregivers/{cid}/links/{linkId}`

**Testing / Evidence required:**
- Testes de HTTP paths passando
- Teste de client-side join para `listForPatient`

**Risks / Notes:**
- A abordagem de client-side join para `listForPatient` pode ser lenta com muitos cuidadores (N calls).
  No MVP com poucos cuidadores por empresa, é aceitável. Sinalizar como gap técnico no JSDoc.
- O BE pode adicionar um endpoint por paciente no futuro (directory surface). Quando isso ocorrer,
  o `listForPatient` pode ser substituído por uma chamada direta.
