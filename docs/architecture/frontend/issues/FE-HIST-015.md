## [FE-HIST-015] hist feature-timeline: EventDetailComponent (post-MVP, flag detailRoute)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`EventDetailComponent` em `libs/hist/feature-timeline` — tela de detalhe de um registro de auditoria (`/historico/:eventId`), ativada apenas quando flag `detailRoute` é `true`.

**Context / Problem statement:**
- SPEC-01 §1: rota `/historico/:eventId` gated por `detailRoute` (default off no MVP).
- generator-config.yml: `EventDetailComponent` em feature `timeline`.
- Consome `GET /api/v1/hist/audit-facts/{id}` (SPEC-04 §2.2).
- Bloqueia: INT-FE-HIST-002 (integração com endpoint de detalhe).

**Approach (execution path):**
1. `EventDetailComponent` standalone, OnPush.
2. Ler `eventId` de `ActivatedRoute.params`.
3. Chamar `HistoryFacade.getRecord(id)` (ou método direto via `HistClient`).
4. Estados: loading (skeleton), loaded (campos do registro), error (inline + back), 404.
5. Registrar rota em `hist.routes.ts` condicionalmente via flag (já definido em FE-HIST-001).

**Non-goals:**
- Não implementar edição — ledger imutável (ADR-004).

**Layer(s):**
frontend / feature-timeline

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-01-route-map.md` (§1)
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-04-api-integration.md` (§2.2)

**Architecture alignment:**
- Standalone + OnPush · Tag Nx: `scope:hist`, `type:feature`

**Implementation checklist (by file):**
- 🔲 `libs/hist/feature-timeline/src/lib/event-detail/event-detail.component.ts`
- 🔲 `libs/hist/feature-timeline/src/lib/event-detail/event-detail.component.html`
- 🔲 `libs/hist/feature-timeline/src/lib/event-detail/event-detail.component.spec.ts`
- 🔲 Atualizar `libs/hist/feature-timeline/src/index.ts`
- 🔲 `nx test hist-feature-timeline` PASS
- 🔲 `nx lint hist-feature-timeline` PASS

**Milestone:** Wave P3 / Feature Timeline

**Labels:**
type:feature · layer:frontend · scope:hist · priority:P3

**Acceptance criteria:**
- Componente só é alcançável se `detailRoute=true`.
- Renderiza campos do `AuditRecordResponse` com label humano-legível.
- 404 → inline error + botão de volta ao timeline.

**Dependencies:**
- FE-HIST-001 (flag guard na rota), FE-HIST-008, INT-FE-HIST-002

**Risks / Notes:**
- Post-MVP: prioridade baixa; pode ser diferido se sprint pressionar.
