# 06 — Risks, Gaps and Assumptions — HIST

## 1. Risks

| # | Risk | Impact | Mitigation |
|---|---|---|---|
| R1 | **Feedback ownership ambiguity** — the feedback verdict is owned by EVNT (system of record), but the UI to set it lives in HIST's history screen. Wrong wiring could double-write or drift the audit trail. | High | Resolve the open contract (§Gaps G1) before Specs; drive the write target via `feedbackTarget` feature-flag until decided. |
| R2 | **Label not self-explanatory** — the family's `rotulo` ("Sapo") may be opaque to a new caregiver (LBC §12, Emenda 1). | Medium (UX/trust) | Always pair label with urgency badge + timestamp + confirmation line; consider a tooltip mapping to the command later (post-MVP). |
| R3 | **List volume / performance** — a busy clinic patient can produce many events; a naive list hurts TTI and scroll. | Medium | Pagination + optional virtual scroll from the start; measure against the reference dataset. |
| R4 | **BE read model instability** — `hist-service` does not exist yet (M1); the query shape and whether PAC/ACS joins happen server-side are undefined. | High | Treat `hist-service` contract as a Specs deliverable; keep the facade DTO→VM mapping the single adaptation point. |
| R5 | **Non-optimistic write latency** — waiting for backend confirmation before showing the verdict may feel slow on a poor connection. | Low | Per-item marking spinner + clear disabled state; keep the write idempotent; do not switch to optimistic (R1/audit integrity). |
| R6 | **Timestamp / timezone correctness** — "14h32" must match the tenant's locale; a wrong offset undermines audit credibility. | Medium | Localize on render from ISO; never format server-side into ambiguous strings. |
| R7 | **Browser compat** — same Angular 21 baseline as siblings; virtual scroll edge cases on older browsers. | Low | Follow the project's supported-browser matrix; e2e on the reference set. |

## 2. Known gaps

- **G1 — Feedback write endpoint & owner (BLOCKER for Specs).** Does "Foi real?" POST
  to `evnt-service` (`/events/{id}/feedback`, EVNT owns the verdict + emits
  `FalsoPositivoMarcado`) with `hist-service` recording the audit entry, or is it
  proxied through `hist-service`? This must be decided in Specs. Captured as the
  `feedbackTarget` feature-flag in `hist.contract.ts`.
- **G2 — `hist-service` query contract.** Period filter semantics (`hoje`/`semana`/`mes`
  boundaries, timezone), pagination shape (offset vs cursor), and whether the read
  model returns pre-resolved `rotulo`/`urgencia`/`confirmadoPor` or requires client
  joins with PAC/ACS. Undefined at M1.
- **G3 — HIST vs DASH overlap.** DASH's description also says "consultar histórico".
  Assumed: HIST owns the history *feature/route*; DASH/Home compose the entry point.
  Confirm the boundary before Backlog so the route/menu isn't duplicated.
- **G4 — Audit-trail scope beyond events.** The module name includes "ações de
  confirmação/edição" and "trilha de auditoria". MVP renders the *event* timeline +
  feedback; a fuller audit view (edits to catalog/calibração, access changes) is not
  scoped. Confirm MVP boundary.
- **G5 — Detail route.** Whether an event-detail view (`/historico/:id`) is in MVP or
  post-MVP is open; the wireframe (§5) shows only the list.

## 3. Assumptions to validate

- **A1** — A single history screen with three period tabs (Hoje/Semana/Mês) covers
  the MVP; no custom date-range picker needed yet.
- **A2** — Event volume per patient/day is low-to-moderate in the family scenario;
  pagination (not heavy virtualization) suffices for the pilot, but the design leaves
  room for virtual scroll.
- **A3** — Target devices are desktop/tablet browsers used by the caregiver at home
  or in the clinic; no dedicated mobile-native surface in MVP.
- **A4** — Feedback frequency is low (a few marks per day); non-optimistic latency is
  acceptable.
- **A5** — The read model resolves `rotulo` + `urgencia` + confirmation metadata
  server-side (preferred); client-side PAC/ACS joins are a fallback only.

## 4. Critical dependencies

- **`hist-service`** — the audit read model + query API (not yet scaffolded; M1).
- **EVNT** — feedback verdict transition + `Evento` as system of record (§G1).
- **PAC** — `Comando.rotulo` + `urgencia` for each item.
- **ACS** — caregiver↔patient vínculo, tenant scoping, "quem confirmou".
- **Shared libs** — `@neuroassist/shared/access-http-base`, `access`, `error-model`,
  `ui-feedback`, `ui-tokens`, `observability`.
- **Design tokens** — urgency palette + typography from `@neuroassist/shared/ui-tokens`
  (shared with EVNT's alarm treatment).
- **Shell** — `neuroassist-ai-web` route/menu registration + Home deep-link wiring.
