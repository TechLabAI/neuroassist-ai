## [FE-HIST-002] hist shell-entry: permission-map e feature-flags

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`hist.permission-map.ts` e `hist.feature-flags.ts` definidos em `libs/hist/shell-entry`, mapeando escopos (`hist:read`, `hist:read:all`) e flags (`detailRoute`, `defaultPeriod`, `feedbackTarget`, `virtualScroll`).

**Context / Problem statement:**
- `histGuard` (FE-HIST-016) depende do permission-map.
- Scope `hist:read:all` ativa o badge de verificação de cadeia (SPEC-05 §6).
- Flags: `detailRoute` (default off), `defaultPeriod` (`hoje`), `feedbackTarget` (`evnt`), `virtualScroll` (off).
- `feedbackTarget` é o mecanismo de fallback para gap G1 (evnt-service vs hist-service proxy).
- Bloqueia: FE-HIST-016, FE-HIST-013, INT-FE-HIST-005.

**Approach (execution path):**
1. `hist.permission-map.ts` — mapear `hist:read` (caregiver) e `hist:read:all` (coordinator/DPO).
2. `hist.feature-flags.ts` — `HistFeatureFlags` com defaults; `InjectionToken HIST_FEATURE_FLAGS`.
3. Exportar no barrel `src/index.ts`.
4. `nx lint hist-shell-entry` PASS.

**Non-goals:**
- Não implementar a lógica de guarda — FE-HIST-016.

**Layer(s):**
frontend / shell-entry

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-01-route-map.md` (§3)
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-04-api-integration.md` (§2.4)

**Architecture alignment:**
- Tag Nx: `scope:hist`, `type:app`

**Implementation checklist (by file):**
- 🔲 `libs/hist/shell-entry/src/lib/hist.permission-map.ts`
- 🔲 `libs/hist/shell-entry/src/lib/hist.feature-flags.ts`
- 🔲 Atualizar `libs/hist/shell-entry/src/index.ts`
- 🔲 `nx test hist-shell-entry` PASS
- 🔲 `nx lint hist-shell-entry` PASS

**Milestone:** Wave P1 / Foundation

**Labels:**
type:feature · layer:frontend · scope:hist · priority:P1

**Acceptance criteria:**
- `feedbackTarget` default é `'evnt'`; `detailRoute` default é `false`.
- Override via `HIST_FEATURE_FLAGS` token funciona em teste.

**Dependencies:**
- FE-HIST-001

**API / Contract impact:**
- `feedbackTarget` switch muda base_path do `FeedbackClient` (INT-FE-HIST-005).

**Risks / Notes:**
- Documentar `feedbackTarget` no JSDoc — mecanismo de fallback G1.
