## [INT-FE-ACS-005] PacientesReadClient + PacientesReadFacade — cross-module PAC read-only integration (via GWY)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`PacientesReadClient` (in `libs/acs/data-access`) provides a read-only client for `GET /api/pac/v1/pacientes`
(PAC-owned, routed via GWY). `PacientesReadFacade` exposes `pacientes = signal<Paciente[]>([])` consumed by
`CoverageFacade` to resolve patient names for the linkage panel. ACS NEVER authors a patient record.

**Context / Problem statement:**
- SPEC-04 §1 table row "Paciente": ACS reads patient names for the linkage panel; PAC owns the record.
- SPEC-04 §2 `PacientesReadClient.list()` → `Observable<Paciente[]>` (GET /api/pac/v1/pacientes via GWY).
- `CoverageFacade` (FE-ACS-012) uses paciente names in `PatientCoverageVM.pacienteNome`.
- The API path is cross-service (`/api/pac/v1/pacientes`) — GWY routes to PAC. ACS sends X-Empresa-Id.
- This is the ONLY ACS cross-module read; ACS reads from PAC but never writes to it.

**Approach (execution path):**
1. Create `libs/acs/data-access/src/lib/paciente/pacientes-read.client.ts`:
   - `list()` → `Observable<Paciente[]>` (GET /api/pac/v1/pacientes)
   - `get(id)` → `Observable<Paciente>` (GET /api/pac/v1/pacientes/{id})
2. Create `libs/acs/data-access/src/lib/paciente/paciente.dto.ts`: `Paciente { id, nome, tenantId }` (read-only shape).
3. Create `libs/acs/data-access/src/lib/paciente/pacientes-read.facade.ts`:
   - `private _pacientes = signal<Paciente[]>([])`.
   - `loadPacientes()`: call client.list → `_pacientes`.
   - `reset()`: clear signal.
4. Re-export from `src/index.ts`.
5. Unit tests: client path assertions; facade signal updates; reset clears.

**Non-goals:**
- No patient create/update/delete (PAC owns mutations).
- No SPEC-07 error mapping for PAC errors (treat as `upstream-unavailable` toast — SPEC-07 §1).

**Layer(s):**
frontend / libs/acs/data-access

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-04-data-access.md` (§1 table row Paciente, §2 PacientesReadClient)
- `docs/architecture/frontend/modules/acs/frontend/contracts/backend-api-cross-link.md` (§2 Linkage panel names)

**Architecture alignment:**
- Read-only PAC cross-module; `@neuroassist/shared/access-http-base` interceptors carry X-Empresa-Id
- Tags Nx: `scope:acs`, `type:data-access`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/data-access/`
- 🔲 `src/lib/paciente/paciente.dto.ts` — Paciente (id, nome, tenantId — read-only)
- 🔲 `src/lib/paciente/pacientes-read.client.ts` — list / get (GET /api/pac/v1/pacientes)
- 🔲 `src/lib/paciente/pacientes-read.facade.ts` — signal<Paciente[]> + loadPacientes + reset
- 🔲 `src/index.ts` — re-exports

**Tests**
- 🔲 `src/lib/paciente/pacientes-read.client.spec.ts` — GET /api/pac/v1/pacientes path
- 🔲 `src/lib/paciente/pacientes-read.facade.spec.ts` — loadPacientes populates signal; reset clears
- 🔲 `nx test acs-data-access` PASS

**Milestone:**
Wave 4 / P4 Integrations

**Labels:**
type:integration
layer:frontend
scope:acs
priority:P4

**Acceptance criteria:**
- `npx nx test acs-data-access` PASS
- `list()` emite `GET /api/pac/v1/pacientes`
- `loadPacientes()` popula o signal com a lista de pacientes
- `reset()` limpa o signal
- `PacientesReadFacade` não expõe nenhum método de criação/edição de paciente

**Dependencies:**
- FE-ACS-003 (lib skeleton)
- INT-FE-ACS-001 (interceptores de header — X-Empresa-Id presente na request para PAC)

**Data impact:**
- Nenhum escrito (read-only)

**API / Contract impact:**
- `GET /api/pac/v1/pacientes` (PAC-owned, via GWY)

**Testing / Evidence required:**
- Testes de path e signal passando
- Confirm: nenhum POST/PUT/DELETE para /api/pac endpoints em toda a lib acs/data-access

**Risks / Notes:**
- O shape do `Paciente` retornado pelo PAC pode diferir do schema do ACS. Usar apenas os campos necessários
  (id, nome) no DTO ACS para isolamento.
- Se PAC não estiver disponível (MVP sem stub), `loadPacientes()` deve falhar silenciosamente e retornar
  `[]` — o linkage panel nomes ficam em branco mas a cobertura ainda funciona por ids.
