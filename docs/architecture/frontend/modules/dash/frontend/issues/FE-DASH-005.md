## [FE-DASH-005] DashApiService + DTOs — wrapper HTTP sobre /panel/**

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/dash/data-access/src/lib/dash-api.service.ts` implementa todos os métodos HTTP do DASH sobre `access-http-base`, cobrindo os 8 recursos de `GET /panel/summary` a `POST /panel/events/{eventId}/confirm`. DTOs relevantes são declarados (ou referenciados) em `@neuroassist/shared/contracts`.

**Context / Problem statement:**
- Camada fina: um método por endpoint, retorna DTO bruto, sem mapeamento de ViewModel.
- Injeta `X-Empresa-Id` via `@neuroassist/shared/access-http-base` e JWT via interceptor compartilhado.
- Fonte: SPEC-04 §1/§2; backend-api-cross-link.md; api-contract.md.
- Bloqueia: FE-DASH-006..008

**Approach (execution path):**
1. Criar `libs/dash/data-access/src/lib/dash-api.service.ts` com `@Injectable({ providedIn: 'root' })`.
2. Injetar `HttpClient` via `inject(HttpClient)` e construir `baseUrl` via `access-http-base` (porta 8090 dev, GWY em prod).
3. Implementar métodos:
   - `getSummary(): Observable<SummaryDto>`  → `GET /panel/summary`
   - `getPatientOverview(patientId: string): Observable<PatientOverviewDto>` → `GET /panel/patients/{patientId}/overview`
   - `getHistory(patientId, filter): Observable<HistoryPageDto>` → `GET /panel/patients/{patientId}/history`
   - `getDevice(patientId): Observable<DeviceStatusDto>` → `GET /panel/patients/{patientId}/device`
   - `getUnacknowledged(): Observable<NotificationEnvelope[]>` → `GET /panel/notifications/unacknowledged`
   - `pollNotifications(cursor, wait): Observable<NotificationEnvelope[]>` → `GET /panel/notifications/poll`
   - `acknowledge(dispatchId: string): Observable<void>` → `POST /panel/notifications/{dispatchId}/acknowledge` (idempotente)
   - `saveCalibration(patientId, commandId, req: CalibrationRequest): Observable<void>` → `PUT /panel/patients/{patientId}/commands/{commandId}/calibration`
   - `confirmEvent(eventId, req: ConfirmRequest): Observable<void>` → `POST /panel/events/{eventId}/confirm`
4. Garantir que DTOs (`SummaryDto`, `NotificationEnvelope`, `CalibrationRequest`, `ConfirmRequest`) existem em `@neuroassist/shared/contracts`; se ausentes, declará-los.

**Non-goals:**
- Não mapear DTO → ViewModel (responsabilidade das facades FE-DASH-006/007).
- Não implementar SSE (DashStreamClient — FE-DASH-008).

**Layer(s):**
frontend / libs/dash/data-access (type:data-access)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-04-data-access.md` (§1/§2/§3)
- `docs/architecture/backend/services/dash-service/contracts/api-contract.md` (barrier)

**Architecture alignment:**
- Standalone + OnPush + Signals
- Tag Nx: `scope:dash`, `type:data-access`

**Implementation checklist (by file):**

**Lib: libs/dash/data-access**
- 🔲 `libs/dash/data-access/src/lib/dash-api.service.ts`
- 🔲 `libs/dash/data-access/src/index.ts` (barrel)

**Shared contracts (se necessário)**
- 🔲 `libs/shared/contracts/src/lib/dash/summary.dto.ts` (SummaryDto, PatientSummary, DeviceBadge, LatestEvent)
- 🔲 `libs/shared/contracts/src/lib/dash/notification.dto.ts` (NotificationEnvelope)
- 🔲 `libs/shared/contracts/src/lib/dash/calibration.dto.ts` (CalibrationRequest)
- 🔲 `libs/shared/contracts/src/lib/dash/confirm.dto.ts` (ConfirmRequest)

**Tests**
- 🔲 `npx nx test dash-data-access` PASS (HttpClientTestingModule; verificar que cada método chama o endpoint correto)
- 🔲 `npx nx lint dash-data-access` PASS

**Milestone:**
Wave 2 / Data Access

**Labels:**
type:data-access
layer:frontend
scope:dash
priority:P2

**Acceptance criteria:**
- `npx nx test dash-data-access` PASS
- `npx nx lint dash-data-access` PASS
- `npx nx build dash-data-access` PASS
- Cada método HTTP testa o endpoint correto com HttpTestingController
- `X-Empresa-Id` presente em todas as requisições (via interceptor — verificar no test)

**Dependencies:**
- FE-DASH-001 (contrato do módulo)

**Data impact:**
- Nenhum (FE não persiste)

**API / Contract impact:**
- Bind definitivo ao wire contract `/panel/**` (BE autoritativo — barrier é api-contract.md)

**Testing / Evidence required:**
- Tests com HttpClientTestingModule cobrindo todos os 9 métodos

**Risks / Notes:**
- `acknowledge` é idempotente por `dispatchId` — tratar 2xx como sucesso mesmo em retry.
- `saveCalibration` e `confirmEvent` são write-through — propagar o erro da upstream sem modificar.
