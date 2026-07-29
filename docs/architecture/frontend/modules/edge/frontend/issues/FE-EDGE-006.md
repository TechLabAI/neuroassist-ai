## [FE-EDGE-006] EdgeHttpService — HTTP transport layer (register, pair, unpair, retire, getDevice, resolve, pollLiveness)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/edge/data-access/src/lib/edge-http.service.ts` implements all 8 HTTP operations described in SPEC-04 §1.
All calls go through `@neuroassist/shared/access-http-base` (GWY base URL + Bearer JWT). ETag/304 is handled
for liveness polls. No device credential, heartbeat, or enroll call is present (browser never holds device cred).

**Context / Problem statement:**
- The service is the thin transport adapter between the facades and the network — no business logic here.
- `If-None-Match` header sent on liveness polls; a 304 response must return a `NotModified` marker so the
  facade can tick `_clock` without overwriting `_device` (SPEC-04 §3, api-contract §4.4).
- `tenantId` is NEVER sent as a request field; it is derived server-side from the Bearer JWT (api-contract §1.1).
- Error interception: HTTP interceptor (from shared/access-http-base) maps `ProblemDetail` responses to
  `AppError`; EDGE-specific error copy is injected via INT-FE-EDGE-006.
- Bloqueia: FE-EDGE-007, FE-EDGE-008

**Approach (execution path):**
1. Create `libs/edge/data-access/src/lib/edge-http.service.ts` as `@Injectable({ providedIn: 'root' })`.
2. Inject `HttpClient` (or the shared `AccessHttpClient` base class).
3. Implement the 8 methods from SPEC-04 §1 using the frozen endpoint paths from api-contract.md:
   - `register(body)` → `POST /api/v1/edge/devices`
   - `pairToPatient(deviceId, body)` → `POST /api/v1/edge/devices/{id}/pairings`
   - `unpair(deviceId, body)` → `DELETE /api/v1/edge/devices/{id}/pairings/current`
   - `retire(deviceId)` → `POST /api/v1/edge/devices/{id}:retire`
   - `getDevice(deviceId)` → `GET /api/v1/edge/devices/{deviceId}`
   - `resolveDeviceForPatient(pacienteId)` → `GET /api/v1/edge/patients/{pacienteId}/device`
   - `pollPatientLiveness(pacienteId, etag?)` → `GET /api/v1/edge/patients/{pacienteId}/liveness`
   - `pollDeviceLiveness(deviceId, etag?)` → `GET /api/v1/edge/devices/{deviceId}/liveness`
4. For liveness methods: send `If-None-Match: <etag>` when `etag` is provided; return `NotModified`
   discriminant on 304 (observe `HttpResponse` + status code).
5. Export from `libs/edge/data-access/src/index.ts`.

**Non-goals:**
- Does not implement retry logic (that belongs to the facade, SPEC-07 §3, covered in FE-EDGE-019 and data-access retry interceptor in shared).
- Does not call `POST /heartbeat` or `POST /devices/enroll` (agent-only endpoints).

**Layer(s):**
frontend / libs/edge/data-access

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-04-data-access.md` (§1, §5)
- `docs/architecture/frontend/modules/edge/frontend/contracts/backend-api-cross-link.md` (§1, §2)

**Architecture alignment:**
- Tag Nx: `scope:edge`, `type:data-access`
- No side-effects beyond HTTP; pure transport (no signals, no timers)

**Implementation checklist (by file):**

**Lib path**
- 🔲 `libs/edge/data-access/src/lib/edge-http.service.ts` (create)
- 🔲 `libs/edge/data-access/src/index.ts` (re-export EdgeHttpService)

**Tests**
- 🔲 `libs/edge/data-access/src/lib/edge-http.service.spec.ts`:
  - `register()` POSTs to `/api/v1/edge/devices` with correct body
  - `pairToPatient()` POSTs to `.../pairings`
  - `pollPatientLiveness()` with etag sends `If-None-Match` header
  - `pollPatientLiveness()` on 304 response returns `NotModified` discriminant
  - `retire()` uses POST with `:retire` suffix (not DELETE)
  - No call to `/heartbeat` or `/devices/enroll` exists
- 🔲 `nx test edge-data-access` PASS
- 🔲 `nx lint edge-data-access` PASS

**Milestone:**
Wave P2 / Data Access

**Labels:**
type:feature
layer:frontend
scope:edge
priority:P2

**Acceptance criteria:**
- `npx nx test edge-data-access` PASS
- `npx nx lint edge-data-access` PASS
- ETag/304 test passing: `pollPatientLiveness` with 304 returns `NotModified`
- No `tenantId` in any request body (confirmed by unit test)
- No heartbeat or enroll method exists

**Dependencies:**
- FE-EDGE-005 (DTOs — DispositivoEdgeView, RegisterDeviceResult, LivenessView, NotModified)

**Data impact:**
- Nenhum (FE não persiste dados; thin transport only)

**API / Contract impact:**
- Consumes `POST /api/v1/edge/devices`, `POST .../pairings`, `DELETE .../pairings/current`,
  `POST .../:retire`, `GET .../devices/{id}`, `GET .../patients/{id}/device`, `GET .../liveness` (×2)

**Testing / Evidence required:**
- HttpClientTestingModule spy confirming correct verb + path for each method
- `If-None-Match` header spy test for liveness polls

**Risks / Notes:**
- `POST /devices/{id}:retire` (colon-style action path) may need URL encoding — test with the actual
  `HttpClient` to confirm Angular's URL builder does not encode the colon.
