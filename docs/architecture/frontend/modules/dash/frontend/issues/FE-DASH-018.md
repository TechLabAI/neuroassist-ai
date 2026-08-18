## [FE-DASH-018] DashShellIntegrationService — registerWithShell + bindActiveEvent + bindDeviceBadge + releaseOutletRegion

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/dash/integration/src/lib/dash-shell-integration.service.ts` implementa `registerWithShell()`, `bindActiveEvent()`, `bindDeviceBadge()` e `releaseOutletRegion()`, orquestrando o seam cross-module sem que `scope:dash` importe `scope:evnt` ou `scope:edge`.

**Context / Problem statement:**
- É a costura entre DASH e o shell: registra nav, declara outlet region, recebe signals externos.
- `scope:dash` NUNCA importa `scope:evnt`, `scope:edge` ou `scope:hist` — wiring é feito pelo `scope:web` via token/input.
- Fonte: SPEC-06 §2; ADR-005; coverage gap G6 (bootstrap register nav+outlet).
- Bloqueia: INT-FE-DASH-001..003, FE-DASH-019 (bootstrap), FE-DASH-028 (e2e G6)

**Approach (execution path):**
1. Criar `DashShellIntegrationService` com `@Injectable({ providedIn: 'root' })`.
2. `registerWithShell(shell: ShellRegistry): void` — idempotente: registra item de menu "Status" (order 0) e declara a região `'dash.global-interrupt'` via `ShellOutletRegistry`.
3. `bindActiveEvent(source: Signal<ActiveEventVm | null>): void` — armazena a referência no `ACTIVE_EVENT_TOKEN` para consumo pela `DashHomeFacade`.
4. `bindDeviceBadge(source: Signal<DeviceBadgeVm>): void` — expõe via token ou signal compartilhado para `PatientHeaderComponent`.
5. `releaseOutletRegion(): void` — remove o registro de `'dash.global-interrupt'` do `ShellOutletRegistry` (destroy).
6. Exportar pelo barrel de `dash/integration`.

**Non-goals:**
- Não importar `AlarmOverlayComponent` (EVNT).
- Não inicializar SSE (responsabilidade de FE-DASH-019 — dash.bootstrap.ts).

**Layer(s):**
frontend / libs/dash/integration (type:domain)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-06-shell-integration.md` (§2)

**Architecture alignment:**
- Standalone; Signals injection tokens
- Tag Nx: `scope:dash`, `type:domain`

**Implementation checklist (by file):**

**Lib: libs/dash/integration**
- 🔲 `libs/dash/integration/src/lib/dash-shell-integration.service.ts`
- 🔲 `libs/dash/integration/src/index.ts` (barrel)

**Tests**
- 🔲 `npx nx test dash-integration` PASS (testar: registerWithShell idempotente; releaseOutletRegion remove registro; bindActiveEvent disponibiliza signal)
- 🔲 `npx nx lint dash-integration` PASS

**Milestone:**
Wave 6 / Shell Integration

**Labels:**
type:domain
layer:frontend
scope:dash
priority:P6

**Acceptance criteria:**
- `npx nx test dash-integration` PASS
- `npx nx lint dash-integration` PASS
- `npx nx build dash-integration` PASS
- `registerWithShell()` chamado duas vezes não duplica o registro (idempotente — coverage gap G6)
- `releaseOutletRegion()` deregistra `'dash.global-interrupt'`
- Não importa `scope:evnt`, `scope:edge`, `scope:hist`

**Dependencies:**
- FE-DASH-001 (DASH_MODULE_DESCRIPTOR — outlet regions)
- FE-DASH-006 (ACTIVE_EVENT_TOKEN)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Test verifica que `scope:evnt` não aparece nos imports de `dash/integration`

**Risks / Notes:**
- `ShellRegistry` e `ShellOutletRegistry` são contratos de `@neuroassist/shared/ui-components`; verificar que a interface está estabilizada antes de implementar.
