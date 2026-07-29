## [PAC-FE-016] feature-patient-register — PatientRegisterComponent + PatientFormComponent

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/feature-patient-register` implementa `PatientRegisterComponent` (smart, composto dentro do wizard ACS) e seus filhos apresentacionais (`PatientFormComponent`, `NomeFieldComponent`, `DispositivoEdgePickerComponent`, `CuidadorMultiSelectComponent`, `PatientSaveStatusComponent`) sem contribuir breadcrumb PAC e sem rota própria no shell.

**Context / Problem statement:**
- `PatientRegisterComponent` é montado pelo ACS onboarding wizard (step 2 "Paciente") — PAC não declara rota para ele (SPEC-01 §1 nota "Composed surface").
- Lê signals de `PacPacienteFacade`: `patient()` e `saveState()`; despacha `savePatient(nome, dispositivoEdgeId?, cuidadorResponsavelIds?)`.
- `DispositivoEdgePickerComponent` resolve referências ao EDGE module; `CuidadorMultiSelectComponent` resolve referências ao ACS directory — ambos via INT-FE-PAC-005.
- Validação: nome obrigatório (rótulo-field pattern). Referências inválidas geram erro inline (SPEC-07 §1 — `400 /errors/validation` field-level).
- O PAC **não contribui breadcrumb** enquanto composto dentro do wizard (SPEC-06 §2 / SPEC-01 nota).
- Bloqueia: PAC-FE-025 (e2e patient-register)

**Approach (execution path):**
1. Criar `patient-register.component.ts` (smart, standalone, OnPush). Sem `ngOnInit` que faz navigate — o onboarding wizard controla o step.
2. Criar `patient-form.component.ts` (presentacional): `@Input value: PacienteVM | null`, `@Output change: EventEmitter<PacienteFormDraft>`.
3. Criar `nome-field.component.ts`: obrigatório, trim, validação inline.
4. Criar `dispositivo-edge-picker.component.ts`: dropdown de dispositivos edge disponíveis (lê de INT-FE-PAC-005); referência opcional.
5. Criar `cuidador-multi-select.component.ts`: multi-select de cuidadores ACS (lê de INT-FE-PAC-005); opcional.
6. Criar `patient-save-status.component.ts`: `@Input saveState: PatientSaveState`, `@Output retry`.
7. Garantir que `PacBreadcrumbService.clear()` é chamado no contexto do wizard (via `@Input isOnboarding = false`); quando `true`, não contribui breadcrumb.
8. Unit tests: smart component mock facade, save success/error; nome vazio → save button disabled.

**Non-goals:**
- Sem rota de shell (`PAC_ROUTES` não inclui patient-register — é composed).
- Sem wizard navigation logic — o onboarding shell cuida disso.

**Layer(s):**
frontend / libs/pac/feature-patient-register

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§3)
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-03-view-models.md` (§3 PacienteVM)
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-06-shell-integration.md` (§2 breadcrumb)

**Architecture alignment:**
- Standalone + OnPush; composto, não routed
- Tag Nx: `scope:pac`, `type:feature`

**Implementation checklist (by file):**

**Lib: libs/pac/feature-patient-register**
- [ ] `libs/pac/feature-patient-register/src/lib/patient-register.component.ts`
- [ ] `libs/pac/feature-patient-register/src/lib/patient-form.component.ts`
- [ ] `libs/pac/feature-patient-register/src/lib/nome-field.component.ts`
- [ ] `libs/pac/feature-patient-register/src/lib/dispositivo-edge-picker.component.ts`
- [ ] `libs/pac/feature-patient-register/src/lib/cuidador-multi-select.component.ts`
- [ ] `libs/pac/feature-patient-register/src/lib/patient-save-status.component.ts`
- [ ] `libs/pac/feature-patient-register/src/lib/patient-register.component.spec.ts`

**Tests**
- [ ] `npx nx test pac-feature-patient-register` PASS
- [ ] `npx nx lint pac-feature-patient-register` PASS

**Milestone:**
Wave 3 / P3 Feature Libs

**Labels:**
type:feature
layer:frontend
scope:pac
priority:P3

**Acceptance criteria:**
- `npx nx test pac-feature-patient-register` PASS: nome vazio → save button disabled.
- `npx nx test pac-feature-patient-register` PASS: `save()` → `PacPacienteFacade.savePatient` chamado.
- `npx nx test pac-feature-patient-register` PASS: modo onboarding → `PacBreadcrumbService.clear()` chamado.
- `npx nx test pac-feature-patient-register` PASS: erro save → `PatientSaveStatusComponent` com retry.
- Nenhuma rota de shell declarada para `PatientRegisterComponent`.

**Dependencies:**
- PAC-FE-010 (PacPacienteFacade)
- PAC-FE-013 (PacienteVM, PatientSaveState)
- INT-FE-PAC-005 (ACS/EDGE cross-module reference pickers)

**Data impact:**
Empresa-context: paciente registrado fica no schema da empresa ativa.

**API / Contract impact:**
- `save` → `POST /api/pac/v1/pacientes` com `Idempotency-Key`.

**Testing / Evidence required:**
- EARS orphans mapeados: "Unknown dispositivo reference rejects with inline validation", "Unknown cuidador reference rejects with inline validation", "ACS unreachable → retryable message", "Patient register composed inside onboarding wizard contributes no PAC breadcrumb".

**Risks / Notes:**
- O `DispositivoEdgePickerComponent` e `CuidadorMultiSelectComponent` dependem do INT-FE-PAC-005; se não disponível, devem funcionar em modo degradado (sem pickers, apenas campos texto de ID opcional).
