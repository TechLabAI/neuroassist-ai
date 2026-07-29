## [FE-EVNT-010] Degraded-mode polling fallback e feature-flag liveChannel

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`EventDeliveryFacade.startLiveChannel()` verifica a feature flag `evnt.liveChannel` antes de abrir o SSE: se `off`, inicia polling direto; se SSE cair, transiciona para polling com intervalo `evnt.pollIntervalMs`; ao reconectar, transiciona de volta para `'live'`. `deliveryMode` reflete o estado atual.

**Context / Problem statement:**
- SPEC-04 §4 define a degradação: stream error → `deliveryMode='degraded'` → polling `GET /events/active?since=<lastSeen>` a cada `pollIntervalMs` → recuperação silenciosa ao stream voltar.
- FLAG-01 gap (M3.5): `WHERE liveChannel off → no SSE, poll only` — a feature flag controla o canal primário.
- `pollIntervalMs` também é uma feature flag (default 15000ms) — não hardcoded.
- LIFECYCLE-01: `stopLiveChannel()` para o intervalo de polling também.

**Approach (execution path):**
1) Em `startLiveChannel()` (dentro do Facade FE-EVNT-008), ler `EVNT_FEATURE_FLAGS.liveChannel` via `FeatureFlagService` (ou injeção de flag).
2) Se `liveChannel=false`: iniciar polling diretamente via `setInterval(() => pollActive(), pollIntervalMs)`.
3) Se `liveChannel=true`: abrir `EvntClient.openStream()`. Em erro → setar `deliveryMode='degraded'` → iniciar polling.
4) Ao reconectar (stream OK após degradado) → parar polling → setar `deliveryMode='live'`.
5) `stopLiveChannel()`: `clearInterval` + fechar stream EventSource (LIFECYCLE-01).
6) Registrar timer ID em variável privada do Facade para poder cancelar.

**Non-goals:**
- Implementação do cliente SSE (FE-EVNT-007).
- Implementação do FeatureFlagService (shared).

**Layer(s):**
frontend / libs/evnt/data-access (método do EventDeliveryFacade)

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-04-data-access.md` (§3, §4)
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-05-tool-contract.md` (§3 — flag rationale)

**Architecture alignment:**
- `deliveryMode` signal observável por `EventFeedPageComponent` (FE-EVNT-013) para o banner degradado
- Tag Nx: `scope:evnt`, `type:data-access`

**Implementation checklist (by file):**

**Lib data-access (dentro do EventDeliveryFacade)**
- [ ] `startLiveChannel()`: ler flag `liveChannel` → se `false`, iniciar polling sem abrir SSE (FLAG-01)
- [ ] `startLiveChannel()`: se `liveChannel=true` → abrir SSE → em erro → `deliveryMode='degraded'` → iniciar polling
- [ ] `pollIntervalMs`: ler da flag `evnt.pollIntervalMs` (não hardcoded)
- [ ] Polling: `setInterval` guardado em `_pollTimer` privado
- [ ] Recuperação: stream reconecta → `clearInterval(_pollTimer)` → `deliveryMode='live'`
- [ ] `stopLiveChannel()`: `clearInterval(_pollTimer)` + fechar EventSource (LIFECYCLE-01)

**Tests**
- [ ] `nx test evnt-data-access` PASS
- [ ] Unit: `liveChannel=false` → `openStream()` NÃO chamado, polling iniciado
- [ ] Unit: stream error → `deliveryMode==='degraded'` setado
- [ ] Unit: `stopLiveChannel()` → `clearInterval` chamado (sem timer pendente)
- [ ] `nx lint evnt-data-access` PASS

**Milestone:**
Wave P2 / Data access

**Labels:**
type:feature
layer:frontend
scope:evnt
priority:P2

**Acceptance criteria:**
- `npx nx test evnt-data-access` PASS
- `npx nx lint evnt-data-access` PASS
- Com `liveChannel=false`: zero chamadas SSE; polling a cada 15s (default)
- Com stream drop: `deliveryMode` signal muda para `'degraded'` em < 1s

**Dependencies:**
- FE-EVNT-007 (EvntClient.openStream)
- FE-EVNT-008 (EventDeliveryFacade — onde esta lógica reside)
- INT-FE-EVNT-005 (FeatureFlagService integration)

**Data impact:**
- Nenhum

**API / Contract impact:**
- `GET /api/v1/events/active?since={lastSeen}` — chamado no polling

**Testing / Evidence required:**
- Unit com `fakeAsync/tick`: polling dispara exatamente a cada `pollIntervalMs`
- Unit: depois de `stopLiveChannel()`, nenhum `pollActive()` adicional chamado

**Risks / Notes:**
- FLAG-01 gap (M3.5): testar com flag off em ambiente onde SSE estaria disponível — garante que a flag realmente suprime o SSE.
