## [FE-EVNT-014] RoutineNoticeComponent — notificação discreta dismissível com feature flag

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/evnt/feature-routine/src/lib/routine-notice/routine-notice.component.ts` renderiza cada evento de rotina como uma notificação discreta dismissível, oculta quando a feature flag `routineNotices` está desligada.

**Context / Problem statement:**
- SPEC-02 §2: `RoutineNoticeComponent` é apresentacional, recebe `input: evento: EventoVm` e emite `output: dismiss(eventoId)` e `output: open(eventoId)`.
- FLAG-02 gap (M3.5): `WHERE routineNotices off → suppress discreet notices while still delivering emergency alarms` — a supressão deve acontecer no nível do componente ou do page (não do Facade).
- O dismiss é optimistic (client-only, sem server call).

**Approach (execution path):**
1) Criar `libs/evnt/feature-routine/src/lib/routine-notice/routine-notice.component.ts` (standalone, OnPush, apresentacional).
2) `input: evento: EventoVm` — renderiza `commandLabel`, `displayTime`, `urgencia` (badge).
3) `output: dismiss = new OutputEmitter<string>()` — emite `eventoId`.
4) `output: open = new OutputEmitter<string>()` — emite `eventoId`.
5) Na `EventFeedPageComponent` (FE-EVNT-013): verificar `EVNT_FEATURE_FLAGS.routineNotices` via `FeatureFlagService`; se `false`, não renderizar `RoutineNoticeComponent` (FLAG-02).
6) Garantir que com `routineNotices=false`, o `AlarmOverlayComponent` de emergência não é afetado.

**Non-goals:**
- Lógica de alarme de emergência (feature-alarm — diferente lib).
- Chamada de API no dismiss (optimistic — apenas emite output).

**Layer(s):**
frontend / libs/evnt/feature-routine

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§2)
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-05-tool-contract.md` (§3 — flag rationale)

**Architecture alignment:**
- Apresentacional: zero DI de serviços
- Standalone + OnPush + Signals
- Tag Nx: `scope:evnt`, `type:feature`

**Implementation checklist (by file):**

**Lib feature-routine**
- [ ] `libs/evnt/feature-routine/src/lib/routine-notice/routine-notice.component.ts` — apresentacional
- [ ] `input: evento: EventoVm`
- [ ] `output: dismiss: OutputEmitter<string>`
- [ ] `output: open: OutputEmitter<string>`
- [ ] Visual: discreta (não full-bleed), com `UrgencyBadgeComponent`, timestamp, label do comando
- [ ] Flag `routineNotices`: verificado na `EventFeedPageComponent` antes de renderizar este componente (FLAG-02)
- [ ] Com `routineNotices=false` → alarme emergência NÃO afetado (segurança)

**Tests**
- [ ] `nx test evnt-feature-routine` PASS
- [ ] Unit: `(dismiss)` emite o `eventoId` correto
- [ ] Unit: `(open)` emite o `eventoId` correto
- [ ] Unit: com `routineNotices=false` → `RoutineNoticeComponent` não renderizado na feed page
- [ ] `nx lint evnt-feature-routine` PASS

**Milestone:**
Wave P3 / Feature libs

**Labels:**
type:feature
layer:frontend
scope:evnt
priority:P3

**Acceptance criteria:**
- `npx nx test evnt-feature-routine` PASS
- `npx nx lint evnt-feature-routine` PASS
- Screenshot: notificação discreta (styling não full-bleed) com botão de fechar
- Com flag off: feed sem notificações de rotina, mas alarme emergência ainda ativo

**Dependencies:**
- FE-EVNT-013 (EventFeedPageComponent — host)
- FE-EVNT-018 (UrgencyBadgeComponent)
- INT-FE-EVNT-005 (FeatureFlagService — leitura da flag)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum (dismiss é client-only)

**Testing / Evidence required:**
- Screenshot: modo `routineNotices=false` → feed vazia, sem NoticeComponents, alarme overlay disponível

**Risks / Notes:**
- FLAG-02 gap (M3.5): a supressão pode ser na `EventFeedPageComponent` com `@if (routineNoticesEnabled())` envolvendo `@for (evento of routineQueue())` — mais simples que lógica no Facade.
