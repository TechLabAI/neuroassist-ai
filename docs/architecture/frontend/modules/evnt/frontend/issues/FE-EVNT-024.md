## [FE-EVNT-024] DegradedModeBanner + ERR-03 rotina silenciosa + feature-flag routineNotices

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
O `EventFeedPageComponent` exibe o `DegradedModeBannerComponent` quando `deliveryMode='degraded'` com mensagem "Modo degradado — verificando a cada {n}s". Falhas de entrega de rotina são retentativas silenciosas (sem alarme). A feature flag `routineNotices=off` suprime silenciosamente as notificações de rotina.

**Context / Problem statement:**
- ERR-03 gap (M3.5): `routine delivery fails → retry silently on next poll` — sem surface de alarme, sem toast (SPEC-07 §3).
- FLAG-02 gap (M3.5): `routineNotices=off → suppress discreet notices` — integração completa na page (FE-EVNT-014 trata o componente; esta issue trata a lógica de flag na page e o silent retry).
- O banner degradado é `DegradedModeBannerComponent` de `@neuroassist/shared/ui-feedback` — não um componente EVNT.

**Approach (execution path):**
1) Na `EventFeedPageComponent` (FE-EVNT-013): `@if (deliveryMode() === 'degraded')` → renderizar `DegradedModeBannerComponent` com `input: pollIntervalMs`.
2) Mensagem do banner: "Modo degradado — verificando a cada {pollIntervalMs/1000}s" (SPEC-07 §3).
3) No Facade (FE-EVNT-008/010): erros de poll de rotina são silenciosos — não setar `_error` signal no façade; apenas logar (ERR-03).
4) Com `routineNotices=false`: a `EventFeedPageComponent` verifica a flag via `FeatureFlagService` e oculta a seção de `routineQueue` — o alarme de emergência não é afetado (FLAG-02).

**Non-goals:**
- Erros de confirmação de alarme (FE-EVNT-012).
- Implementação do `DegradedModeBannerComponent` (shared).

**Layer(s):**
frontend / libs/evnt/feature-routine (EventFeedPageComponent)

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-07-error-handling.md` (§3)
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-05-tool-contract.md` (§3)

**Architecture alignment:**
- Banner: `@neuroassist/shared/ui-feedback` (non-blocking persistent)
- Tag Nx: `scope:evnt`, `type:feature`

**Implementation checklist (by file):**

**Lib feature-routine (EventFeedPageComponent)**
- [ ] `@if (deliveryMode() === 'degraded')` → `DegradedModeBannerComponent` com `pollIntervalMs`
- [ ] Mensagem banner: "Modo degradado — verificando a cada {N}s"
- [ ] `routineNotices=false` → `@if (routineNoticesEnabled())` ao redor de `routineQueue` section (FLAG-02)
- [ ] Falha de poll rotina → sem erro no signal; log apenas (ERR-03)

**Lib data-access (EventDeliveryFacade)**
- [ ] Erros de polling são silenciosos (ERR-03): `catchError` → `EMPTY` + log; sem propagação para `_error`

**Tests**
- [ ] `nx test evnt-feature-routine` PASS
- [ ] Unit: `deliveryMode='degraded'` → banner com mensagem "Modo degradado" no DOM
- [ ] Unit: `routineNoticesEnabled=false` → `RoutineNoticeComponent` ausente no DOM
- [ ] `nx test evnt-data-access` PASS
- [ ] Unit: poll retorna erro → nenhum signal de erro no facade (ERR-03)
- [ ] `nx lint evnt-feature-routine` PASS

**Milestone:**
Wave P7 / UX polish

**Labels:**
type:feature
layer:frontend
scope:evnt
priority:P7

**Acceptance criteria:**
- `npx nx test evnt-feature-routine` PASS
- `npx nx test evnt-data-access` PASS
- Screenshot: banner "Modo degradado" visível com intervalo correto
- Erro de poll rotina → nenhum toast, nenhum banner de alarme

**Dependencies:**
- FE-EVNT-010 (polling + deliveryMode signal)
- FE-EVNT-013 (EventFeedPageComponent)
- FE-EVNT-014 (RoutineNoticeComponent — renderização condicional)
- INT-FE-EVNT-005 (FeatureFlagService — leitura da flag)

**Data impact:**
- Nenhum

**API / Contract impact:**
- `GET /api/v1/events/active` — chamado no polling; erros são silenciosos

**Testing / Evidence required:**
- Screenshot: banner degradado + feed de rotina oculto (routineNotices=false) + alarme emergência ainda disponível

**Risks / Notes:**
- ERR-03 gap (M3.5): "retry silently on next poll" significa que o próximo `setInterval` tick tentará novamente — sem lógica de backoff; suficiente para MVP.
