## [PAC-INT-005] ACS Caregiver + DispositivoEdge cross-module reference resolution

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/data-access/src/lib/clients/acs-directory-read.client.ts` e `edge-device-read.client.ts` implementam leitura read-only de cuidadores ACS e dispositivos edge, consumidos pelos pickers do `PatientFormComponent` (PAC-FE-016) para resolver referências externas ao módulo PAC.

**Context / Problem statement:**
- `PatientRegisterComponent` (PAC-FE-016) inclui `DispositivoEdgePickerComponent` (opcional, referência por id ao EDGE module) e `CuidadorMultiSelectComponent` (opcional, referências por ids ao ACS directory).
- PAC apenas lê esses recursos (nunca autora); os módulos EDGE e ACS são owners.
- Se ACS estiver indisponível durante o save → `503` → retryable (patient-register.feature cenário "ACS unreachable").
- Se referência desconhecida → `400 /errors/validation` field-level (SPEC-07 §1).

**Approach (execution path):**
1. Criar `libs/pac/data-access/src/lib/clients/acs-directory-read.client.ts`: `listCuidadores(): Observable<CuidadorRef[]>` → `GET /api/acs/v1/cuidadores?size=50` (via GWY).
2. Criar `libs/pac/data-access/src/lib/clients/edge-device-read.client.ts`: `listDevices(): Observable<EdgeDeviceRef[]>` → `GET /api/edge/v1/dispositivos` (via GWY).
3. Criar interfaces `CuidadorRef { id, nome }` e `EdgeDeviceRef { id, apelido, status }` (DTOs mínimos para os pickers).
4. Criar facades mínimas ou injetar os clients diretamente nos pickers.
5. Unit test com `HttpClientTestingModule`.

**Non-goals:**
- Sem write para ACS ou EDGE — apenas read para resolver referências.
- Sem paginação avançada nos pickers (limit razoável, ex: 50).

**Layer(s):**
frontend / libs/pac/data-access

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§3 DispositivoEdgePickerComponent + CuidadorMultiSelectComponent)
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-07-error-handling.md` (§1 — validation inline para refs inválidas)

**Architecture alignment:**
- Tag Nx: `scope:pac`, `type:data-access`
- Sem imports de `scope:acs` ou `scope:edge` — usa a API via GWY

**Implementation checklist (by file):**

**Lib: libs/pac/data-access**
- [ ] `libs/pac/data-access/src/lib/clients/acs-directory-read.client.ts`
- [ ] `libs/pac/data-access/src/lib/clients/edge-device-read.client.ts`
- [ ] `libs/pac/data-access/src/lib/dtos/cross-module-refs.dto.ts` (CuidadorRef, EdgeDeviceRef)
- [ ] `libs/pac/data-access/src/lib/clients/acs-directory-read.client.spec.ts`

**Tests**
- [ ] `npx nx test pac-data-access` PASS
- [ ] `npx nx lint pac-data-access` PASS

**Milestone:**
Wave 4 / P4 Hub Integrations

**Labels:**
type:integration
layer:frontend
scope:pac
priority:P4

**Acceptance criteria:**
- `npx nx test pac-data-access` PASS: `listCuidadores()` → `GET /api/acs/v1/cuidadores`.
- `npx nx test pac-data-access` PASS: `listDevices()` → `GET /api/edge/v1/dispositivos`.
- e2e: referência inválida de dispositivo → erro inline no campo (não bloqueia o save da issue principal).

**Dependencies:**
- INT-FE-PAC-004 (interceptors — X-Empresa-Id propagado)

**Data impact:**
Nenhum (read-only).

**API / Contract impact:**
- Consome `GET /api/acs/v1/cuidadores` e `GET /api/edge/v1/dispositivos` via GWY.
- Depende de ACS e EDGE services estarem disponíveis; se não, os pickers ficam vazios (modo degradado).

**Testing / Evidence required:**
- Unit: URLs assertadas para cada client.
- e2e: patient-register cenários de referência inválida passam.

**Risks / Notes:**
- ACS e EDGE podem não estar no MVP; pickers devem funcionar em modo degradado (campo de texto manual para o ID).
- Os contratos `GET /api/acs/v1/cuidadores` e `GET /api/edge/v1/dispositivos` ainda podem não estar formalizados — usar mocks via `page.route` nos e2e e placeholder DTOs até os módulos ACS/EDGE produzirem seus specs.
