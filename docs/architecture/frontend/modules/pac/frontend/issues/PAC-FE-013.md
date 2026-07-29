## [PAC-FE-013] View Model type declarations — ComandoRowVM, EditorVM, PacienteVM, TipoGestoVM

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/data-access/src/lib/models/view-models.ts` declara todas as interfaces de view model usadas pelos facades e componentes PAC: `ComandoRowVM`, `CatalogKpiVM`, `CatalogLoadState`, `EditorVM`, `ComandoFormVM`, `EditorState`, `TipoGestoVM`, `PacienteVM`, `PatientSaveState` — exatamente como especificado em SPEC-03, como `readonly interface` records.

**Context / Problem statement:**
- As interfaces são a barreira de tipagem entre os facades (signals) e os componentes (leitura via `asReadonly()`).
- `syncState: 'synced' | 'pending'` em `ComandoRowVM` e `EditorVM` captura o ADR-005 honest state.
- `EditorState` inclui estados de operação (`saving`, `validating`, `publishing`, `recalibrating`, `retiring`, `error`) além dos de UI (`idle`, `editing`, `saved`).
- O mapeamento `ProblemDetail → AppError` (do `@neuroassist/shared/error-model`) é centralizado em `error-mapping.ts`.
- Bloqueia: todos os PAC-FE-010, 011, 012 (facades usam essas interfaces)

**Approach (execution path):**
1. Criar `libs/pac/data-access/src/lib/models/view-models.ts` com todas as interfaces de SPEC-03 §1/§2/§3.
2. Criar `libs/pac/data-access/src/lib/models/error-mapping.ts` com `mapProblemDetailToAppError(pd: ProblemDetail): AppError` — tabela de 12 mapeamentos do SPEC-07 §1.
3. Criar `libs/pac/data-access/src/lib/models/mapper.ts` com funções puras `mapComandoToRowVM(dto, syncAck)`, `mapComandoToEditorVM(dto)`, `mapPacienteToVM(dto)`, `mapTipoGestoToVM(dto)`.
4. Unit tests: cada mapper com dados fixture.

**Non-goals:**
- Sem lógica de negócio nos mappers — apenas transformação de campos.
- Não duplicar DTOs aqui — os DTOs ficam em `dtos/` (PAC-FE-007/008); os VMs são uma segunda camada de transformação.

**Layer(s):**
frontend / libs/pac/data-access

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-03-view-models.md` (§1/§2/§3)
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-07-error-handling.md` (§1 tabela)

**Architecture alignment:**
- `interface` readonly com `as const` onde aplicável
- Tag Nx: `scope:pac`, `type:data-access`

**Implementation checklist (by file):**

**Lib: libs/pac/data-access**
- [ ] `libs/pac/data-access/src/lib/models/view-models.ts`
- [ ] `libs/pac/data-access/src/lib/models/error-mapping.ts`
- [ ] `libs/pac/data-access/src/lib/models/mapper.ts`
- [ ] `libs/pac/data-access/src/lib/models/mapper.spec.ts`

**Tests**
- [ ] `npx nx test pac-data-access` PASS
- [ ] `npx nx lint pac-data-access` PASS
- [ ] `npx nx build pac-data-access` PASS (0 TS errors)

**Milestone:**
Wave 2 / P2 Data Access

**Labels:**
type:feature
layer:frontend
scope:pac
priority:P2

**Acceptance criteria:**
- `npx nx build pac-data-access` PASS com 0 erros TypeScript.
- `npx nx test pac-data-access` PASS: `mapComandoToRowVM(dto, null)` → `syncState === 'pending'` (null ack = honest pending).
- `npx nx test pac-data-access` PASS: `mapProblemDetailToAppError({ type: '/errors/emergency-not-detectable' })` → `kind === 'blocking-modal'`.
- `ComandoRowVM.syncState` é union `'synced' | 'pending'` (TS compilable).

**Dependencies:**
- Nenhuma (foundation de tipos — implementar antes dos facades)

**Data impact:**
Nenhum — tipos apenas.

**API / Contract impact:**
- `error-mapping.ts` deve cobrir exatamente os 12 tipos de `ProblemDetail` do BE api-contract §2.

**Testing / Evidence required:**
- Mapper unit tests com dados fixture alinhados ao BE api-contract.

**Risks / Notes:**
- `EditorVM.canSave` é derivado (boolean computed de vários campos); declarar como campo calculado em `computed()` no facade, não como campo fixo do VM — evita inconsistência entre o que o VM guarda e o que o `computed()` calcula.
