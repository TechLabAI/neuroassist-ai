# 02 — Architecture Blueprint — PAC (Frontend)

## 1. System boundary

PAC-FE is a **client of the backend** through
`@neuroassist/shared/access-http-base` → API Gateway (GWY), always tenant-scoped
by the `X-Empresa-Id` interceptor (schema-per-empresa). It authors two resources
it owns — `Paciente` and the `Comando` catalog — and reads one it does not:
the `TipoGesto` catalog owned by GEST.

The browser holds **no detection logic and does not own the camera**. When the
command editor needs a live gesture test, it composes GEST's
`GestureLiveTestComponent` at the shell page level; GEST owns the webcam and the
edge bridge (GEST ADR-005). PAC contributes only the `Comando` draft as context.

## 2. External actors and systems

| Actor / system | Role | Channel |
|---|---|---|
| Caregiver / conductor (authenticated) | Authors patient + command catalog | Browser UI |
| API Gateway (GWY) | Patient + command CRUD, tipo-gesto read/request | REST via `access-http-base` |
| GEST module | `TipoGesto` catalog source; new-type request target; live-test component | shared contracts + `scope:web` composition |
| EDGE module | Device sync ack (a saved command becomes active only after the device pulls it) | read via shared status (ADR-005 local) |
| ACS module | Active tenant + patient↔caregiver context | shared session/state |
| DASH / Home | Consumes the `Paciente` read-model PAC owns | shared contracts |

## 3. End-to-end user flows

### Golden path — add / calibrate a command (calibração)

```
[Catalog list open]
   caregiver presses "Novo comando"  ------------> editor opens with a draft
        |
        v
   fills rótulo / urgência / gesto(s) / sensibilidade
        |
        |  (optional) presses "Testar agora"
        |     -> shell page mounts GestureLiveTestComponent(draft)  [GEST owns camera+verdict]
        v
   presses "Salvar comando"  --------------------> PacComandoFacade.save(draft)
        |                                            POST/PUT /comandos (X-Empresa-Id)
   state: saving                                     |
        |                                            v
   state: saved (pendente de sincronização)  <-------+
        |
        v
   edge device pulls catalog on next sync  --------> command marked "ativo no dispositivo"
```

Alternative / edge cases:

```
empty catalog                    -> empty state: "Nenhum comando configurado" + [Adicionar primeiro]
rótulo vazio                     -> "Salvar" disabled (rótulo is required, doc 03 §5)
tipo de gesto não está na lista  -> inline "add new type" -> delegated to GEST; naming ≠ detectability
composite command (2 primitivos) -> per-command higher false-negative risk; test-before-save encouraged
save fails (ProblemDetail)       -> AppError toast (ui-feedback); draft preserved, nothing marked saved
device offline / not yet synced  -> command stays "pendente de sincronização"; never shown as active
```

### Secondary path — register patient (onboarding step 2)

```
[Onboarding wizard, step "Paciente"]
   conductor types patient name  ---------------> PacPacienteFacade.save({nome, tenantId})
                                                   POST /pacientes (X-Empresa-Id)
   step marked done; catalog step (4) unlocked
```

## 4. Internal boundaries (module slicing per module-pattern.md)

PAC follows the canonical NeuroAssist module slicing. The entry is a **library**
(ADR-006), not an app folder.

```
libs/pac/shell-entry/         (type:app, scope:pac)   # @neuroassist/pac — lazy entry
└── src/lib/
    ├── pac.module.ts                  # the module's only NgModule
    ├── pac.contract.ts                # MODULE_KEY + metadata + permissions + flags
    ├── pac.bootstrap.ts               # init/destroy lifecycle
    ├── pac.routes.ts                  # PAC_ROUTES[]  (catalog, editor, patient)
    ├── pac.permission-map.ts
    ├── pac.feature-flags.ts           # e.g. inlineNewTipoGesto on/off
    ├── pac.nav.config.ts
    └── pac.descriptor.ts              # PAC_MODULE_DESCRIPTOR (consumed by Shell)

apps/pac/                     (type:app, scope:pac)   # thin host: loads @neuroassist/pac
apps/pac-e2e/                 (type:e2e)              # Playwright specs

libs/pac/
├── data-access/  (type:data-access) # PacComandoClient + PacPacienteClient + facades (signals)
├── feature-command-catalog/ (type:feature) # list + empty state + KPIs
├── feature-command-editor/  (type:feature) # editor form + preview + GEST live-test slot
├── feature-patient-register/ (type:feature) # onboarding patient step surface
├── access/       (type:domain)       # pac-specific guard (needs active tenant + patient)
├── integration/  (type:domain)       # PacShellIntegrationService
├── navigation/   (type:domain)       # breadcrumb/menu state
├── ui/ (opcional, type:ui)           # CommandRow, UrgencyBadge, SensitivitySelector
└── styles/ (opcional)                # Tailwind utilities + tokens (no SCSS tokens)
```

**Composition rule (critical).** `scope:pac` **must not** import `scope:gest`.
The command editor's live-test is `GestureLiveTestComponent`, composed into PAC's
command-editor experience only at the **`scope:web` shell-page level** (the sole
scope allowed to depend on every module). The shell page renders the PAC editor
*and* the GEST live-test side by side, wiring the current `Comando` draft into GEST
via `@Input`/signal. This is why the prototype's single `calibracao` screen
decomposes, in production, into a PAC command-editor feature that hosts the GEST
live-test through page-level composition. See ADR-006, doc 04 §5, and GEST doc 02 §4.

## 5. Cross-cutting concerns

- **Auth / entitlement** — routes guarded by shared `AccessDecisionService`; a
  pac-local guard additionally requires an active tenant + a registered patient
  before the catalog is editable.
- **Tenancy** — every backend call carries `X-Empresa-Id` via the shared
  `access-http-base` interceptor (schema-per-empresa).
- **Observability** — command create/edit/remove and save/sync outcomes emitted via
  the shared observability lib (ids and parameters only; no PII beyond the family
  label, no video).
- **Design tokens** — colors/typography from `@neuroassist/shared/ui-tokens`
  (`--color-alarm`, `brand`, `success`, `danger`); no ad-hoc colors.
- **i18n** — UI copy pt-BR (human-facing); code/identifiers en-US.
- **LGPD** — the `Paciente` is a data subject; PAC collects the minimum (name +
  tenant binding) and never stores health-condition free text beyond the command
  label the family chooses.
