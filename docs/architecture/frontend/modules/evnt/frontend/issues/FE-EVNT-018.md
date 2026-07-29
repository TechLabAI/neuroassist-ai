## [FE-EVNT-018] libs/evnt/ui — UrgencyBadgeComponent + EventTimestampComponent

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/evnt/ui` exporta `UrgencyBadgeComponent` (badge com cor por urgência via design tokens) e `EventTimestampComponent` (formata timestamp ISO em "às 14h32" pt-BR), reutilizados em feature-alarm e feature-routine.

**Context / Problem statement:**
- SPEC-02 §3 define estes dois componentes como presentacionais compartilhados dentro de `libs/evnt/ui`.
- `UrgencyBadgeComponent`: `input: urgencia: 'emergencia' | 'rotina'` → token-driven color (`--color-alarm` para emergência, tom neutro para rotina).
- `EventTimestampComponent`: `input: recognizedAt: string` → "às 14h32" (pt-BR, via `Intl.DateTimeFormat`).
- Ambos são consumidos por `AlarmOverlayComponent`, `EventListComponent`, `EventDetailPageComponent`, `RoutineNoticeComponent`.

**Approach (execution path):**
1) Criar `libs/evnt/ui/src/lib/urgency-badge/urgency-badge.component.ts` (standalone, OnPush).
2) Lógica de cor: `urgencia === 'emergencia'` → aplicar classe com `--color-alarm`; `'rotina'` → `--color-routine` (neutral token).
3) Criar `libs/evnt/ui/src/lib/event-timestamp/event-timestamp.component.ts` (standalone, OnPush).
4) `formatTime(iso: string)`: `Intl.DateTimeFormat` com `timeStyle: 'short'`, locale `pt-BR` → "às 14h32".
5) Configurar `libs/evnt/ui/project.json` com `importPath: "@neuroassist/evnt/ui"` e `tags: ["scope:evnt","type:ui"]`.
6) Exportar ambos via `libs/evnt/ui/src/index.ts`.

**Non-goals:**
- AlarmMessageComponent (inline em feature-alarm).
- Componentes UI de outros módulos.

**Layer(s):**
frontend / libs/evnt/ui

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§3)

**Architecture alignment:**
- Apresentacional: zero DI de serviços
- Tag Nx: `scope:evnt`, `type:ui`
- Pode ser importado por `feature-alarm` e `feature-routine` (dentro do mesmo scope)

**Implementation checklist (by file):**

**Lib ui**
- [ ] `libs/evnt/ui/project.json` — `tags: ["scope:evnt","type:ui"]`, `importPath: "@neuroassist/evnt/ui"`
- [ ] `libs/evnt/ui/src/lib/urgency-badge/urgency-badge.component.ts` — `input: urgencia`
- [ ] `UrgencyBadgeComponent`: `--color-alarm` para emergência, neutral para rotina
- [ ] `libs/evnt/ui/src/lib/event-timestamp/event-timestamp.component.ts` — `input: recognizedAt`
- [ ] `EventTimestampComponent`: `Intl.DateTimeFormat` locale `pt-BR`, formato "às HH:mm"
- [ ] `libs/evnt/ui/src/index.ts` re-exporta ambos

**Tests**
- [ ] `nx test evnt-ui` PASS
- [ ] Unit: `urgencia='emergencia'` → classe com `--color-alarm` no DOM
- [ ] Unit: `urgencia='rotina'` → classe neutral
- [ ] Unit: `recognizedAt='2026-07-28T14:32:00Z'` → "às 14:32" (ou "às 14h32" conforme locale)
- [ ] `nx lint evnt-ui` PASS

**Milestone:**
Wave P3 / Feature libs

**Labels:**
type:feature
layer:frontend
scope:evnt
priority:P3

**Acceptance criteria:**
- `npx nx test evnt-ui` PASS
- `npx nx lint evnt-ui` PASS
- `npx nx build evnt-ui` PASS
- Badge emergência: fundo vermelho (--color-alarm); badge rotina: fundo neutro

**Dependencies:**
- (nenhuma de outras issues FE — apenas shared/ui-tokens)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Screenshot: UrgencyBadge com variante emergência e rotina
- Unit: `formatTime` snapshot para UTC ISO → local "às HH:mm"

**Risks / Notes:**
- `Intl.DateTimeFormat` com `timeStyle: 'short'` pode produzir "14:32" ou "14h32" dependendo do locale exato — testar com `pt-BR` explicitamente.
- Design token `--color-alarm` deve existir em `@neuroassist/shared/ui-tokens`.
