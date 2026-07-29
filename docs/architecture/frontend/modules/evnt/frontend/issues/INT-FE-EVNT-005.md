## [INT-FE-EVNT-005] Integração com FeatureFlagService — liveChannel, pollIntervalMs, routineNotices

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`EventDeliveryFacade` e `EventFeedPageComponent` integram com `FeatureFlagService` de `@neuroassist/shared` para ler as 3 feature flags EVNT (`liveChannel`, `pollIntervalMs`, `routineNotices`) em runtime sem hardcode.

**Context / Problem statement:**
- SPEC-05 §1 define `EVNT_FEATURE_FLAGS` com defaults, mas os valores em runtime vêm do `FeatureFlagService`.
- FLAG-01 gap (M3.5): `liveChannel off → no SSE, poll only` — o Facade precisa ler a flag antes de `startLiveChannel()`.
- FLAG-02 gap (M3.5): `routineNotices off → suppress discreet notices` — a page lê esta flag.
- `pollIntervalMs` é usado no `setInterval` do polling (não hardcoded 15000).

**Approach (execution path):**
1) Verificar `FeatureFlagService` em `@neuroassist/shared` — interface `get<T>(key: string): T` ou `getBoolean(key: string): boolean`.
2) No `EventDeliveryFacade.startLiveChannel()`: `const liveChannel = flagService.getBoolean(EVNT_FEATURE_FLAGS.liveChannel.key, EVNT_FEATURE_FLAGS.liveChannel.default)`.
3) `pollIntervalMs = flagService.get<number>(EVNT_FEATURE_FLAGS.pollIntervalMs.key, 15000)`.
4) Na `EventFeedPageComponent`: `routineNoticesEnabled = computed(() => flagService.getBoolean(EVNT_FEATURE_FLAGS.routineNotices.key, true) && access.can('evnt.routine.view'))`.
5) Se `FeatureFlagService` não existir: usar os defaults de `EVNT_FEATURE_FLAGS` como fallback.

**Non-goals:**
- Implementação do `FeatureFlagService` (shared).
- Outras flags além das 3 EVNT.

**Layer(s):**
frontend / libs/evnt/data-access + libs/evnt/feature-routine → @neuroassist/shared

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-05-tool-contract.md` (§1, §3)

**Architecture alignment:**
- Fallback para defaults de `EVNT_FEATURE_FLAGS` se service indisponível
- Tag Nx: `scope:evnt`, `type:data-access`

**Implementation checklist (by file):**

**Lib data-access (EventDeliveryFacade)**
- [ ] `liveChannel`: ler de `FeatureFlagService` com fallback `true` (FLAG-01)
- [ ] `pollIntervalMs`: ler de `FeatureFlagService` com fallback `15000`

**Lib feature-routine (EventFeedPageComponent)**
- [ ] `routineNoticesEnabled`: ler de `FeatureFlagService` com fallback `true` (FLAG-02)

**Tests**
- [ ] `nx test evnt-data-access` PASS
- [ ] Unit: mock `liveChannel=false` → `openStream()` não chamado
- [ ] Unit: mock `pollIntervalMs=5000` → polling a cada 5s
- [ ] `nx lint evnt-data-access` PASS

**Milestone:**
Wave P4 / Hub integrations

**Labels:**
type:integration
layer:frontend
scope:evnt
priority:P4

**Acceptance criteria:**
- `npx nx test evnt-data-access` PASS
- `liveChannel=false` via flag → sem SSE, polling apenas
- `routineNotices=false` via flag → sem RoutineNoticeComponent

**Dependencies:**
- FE-EVNT-007 (EvntClient — openStream)
- FE-EVNT-010 (polling fallback)
- FE-EVNT-014 (RoutineNoticeComponent — renderização condicional)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Unit: mock do `FeatureFlagService` com `liveChannel=false` → spy `openStream` não chamado

**Risks / Notes:**
- Se o `FeatureFlagService` não existe ainda, usar uma implementação simples que lê de `localStorage` ou variáveis de ambiente — o importante é ter a abstração correta.
