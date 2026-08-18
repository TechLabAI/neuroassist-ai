<!-- OUTPUT-SCHEMA
- Riscos
- Gaps Conhecidos
- Suposicoes a Validar
- Dependencias Criticas
-->

# 06 — Risks, Gaps, Assumptions

## Riscos
- **R1 — Missed emergency when the caregiver is not connected.** DASH pushes over SSE only to an *open*
  panel; if the caregiver's browser is closed, the alarm cannot be rendered. DASH's job is to report
  "undelivered" so EVNT's fail-open retry persists it — but panel-only delivery means an inattentive or
  offline caregiver is a real safety gap. Mitigation: honest undelivered reporting + on-connect catch-up;
  out-of-band channels (SMS/push/phone) are the real fix and are deferred (inherited from EVNT ADR-005). This
  is the module's most serious risk.
- **R2 — SSE at scale / connection storms.** Holding one open connection per caregiver, plus reconnect storms
  after a DASH restart (every panel catches up at once, each pulling unacknowledged notifications from EVNT),
  can spike load on DASH and EVNT. For pilot volume (≈1 caregiver) this is negligible; a naive design would
  bottleneck later. Mitigation: bounded heartbeat, jittered reconnect, and EVNT catch-up pull that is cheap
  and idempotent; revisit transport (WebSocket/broker) if concurrency grows.
- **R3 — Fan-out latency and partial views.** Composing a screen from four upstreams means the slowest
  upstream sets the perceived latency, and any one being down degrades a section. A caregiver could read a
  stale/partial screen and misjudge the situation (e.g. an empty history looking like "no events"). Mitigation:
  parallel fan-out with per-upstream timeouts, explicit "section unavailable" markers (never a silent empty),
  and honest device-liveness staleness.
- **R4 — False sense of delivery / acknowledgement.** If DASH ever reported `delivered` without the panel
  actually rendering, or `acknowledged` without a real caregiver action, EVNT would stop retrying a
  still-unseen emergency. Mitigation: emit signals only on genuine render/ack; treat any fabricated delivery
  as a safety defect (invariant 2, doc 03).
- **R5 — Scope/authorization drift.** DASH enforces caregiver↔patient visibility using ACS; if that linkage
  is stale or DASH caches it too long, a caregiver could see a patient they no longer should (or be denied
  one they should). Mitigation: resolve scope against ACS per request (or with a very short TTL); no
  long-lived scope cache in the MVP.
- **R6 — Write-through divergence.** Proxying calibration to PAC and confirmations to EVNT means DASH must not
  optimistically show a value it hasn't confirmed the owner accepted, or the panel and the source of truth
  diverge. Mitigation: reflect the owner's returned result, not the optimistic input; DASH stores no copy
  (ADR-006 local).
- **R7 — Trusted internal endpoint abuse.** `POST /internal/notifications` must accept dispatches only from
  EVNT's service identity; if exposed to the browser or a spoofed caller, a fake full-screen alarm could be
  injected. Mitigation: strict service-to-service auth on the internal endpoint, never reachable with a
  caregiver token; routed/enforced at GWY + DASH.

## Gaps Conhecidos
- **G1 — Out-of-band escalation** (what happens to an unacknowledged `emergencia` when no panel is open) is
  unsolved at vision level and shared with EVNT. Whether DASH participates (e.g. surfaces escalation state) or
  it is purely EVNT/ACS routing must be decided in Specs/Backlog.
- **G2 — Transport choice & fallback** (SSE vs WebSocket vs long-poll, heartbeat interval, reconnect/backoff)
  is sketched (SSE default) but not specified; the exact contract and the browser fallback path need Specs.
- **G3 — View-model contracts** — the precise shape of each panel screen's view model and its degradation
  markers (which sections, what "unavailable" looks like) are not yet frozen; they depend on the panel's
  final screen inventory (BA reference: 6 screens).
- **G4 — On-connect catch-up semantics** — exactly which notifications are replayed (all unacknowledged? a
  bounded window? emergencies only?) and how duplicates are suppressed against a live dispatch that arrives
  mid-catch-up needs Specs, jointly with EVNT.
- **G5 — Caregiver scope resolution cost** — resolving ACS linkage per request may be too chatty; whether a
  short-TTL scope cache is acceptable (and its invalidation on ACS access changes) is an open performance/
  correctness tradeoff.
- **G6 — Read-model / cache** — whether DASH stays fully live-composing or introduces a short-lived cache/
  read-model for hot screens is deferred; the trigger is measured fan-out latency, not MVP scope.
- **G7 — UI preference need** — whether the optional `caregiver_ui_preference` table is built at all is
  undecided; the MVP assumes zero DASH tables until a concrete need appears.

## Suposicoes a Validar
- **A1** — EVNT dispatches notifications to DASH synchronously and owns the `queued → delivered →
  acknowledged → failed` lifecycle with fail-open retry (EVNT ADR-005); DASH only delivers and reports.
  *Must be confirmed against EVNT's Specs (dispatch endpoint + callback contract).*
- **A2** — Pilot concurrency is tiny (≈1 caregiver, 1 patient) → SSE per caregiver, per-request ACS scope
  resolution, and live fan-out (no cache, no read-model, no inbox) are sufficient. *Revisit if concurrency
  grows.*
- **A3** — The upstreams expose the reads DASH needs in a panel-composable shape (HIST timeline query, PAC
  catalog+calibration, EVNT event/notification state, EDGE liveness ETag/304 `DASH poll 15s`). *Confirm each
  upstream's Specs matches the panel's needs.*
- **A4** — Calibration and confirmation are write-through to PAC (PAC ADR-004) and EVNT (EVNT ADR-006)
  respectively; DASH is never the authoring source. *Assumed and enforced by contract.*
- **A5** — ACS is authoritative for caregiver identity and caregiver↔patient linkage, resolvable at request
  time; DASH performs no privilege elevation and always acts as the caregiver toward read/write upstreams.
- **A6** — The browser panel (Angular prototype-first, `frontend/design-reference`) is the client; DASH's
  view-model and SSE contracts are shaped to that panel, not a generic API surface.

## Dependencias Criticas
- **EVNT (`evnt-service`)** — the notification source (dispatch sink + unacknowledged pull + delivery/ack
  callbacks) and the confirmation write-through target. Without EVNT, DASH has nothing to deliver. **Hardest
  coupling** — the dispatch/callback contract is co-owned and must land in both Specs.
- **ACS (`acs-service`)** — identity, authentication and caregiver↔patient scope. Effectively a hard
  dependency: no scope ⇒ no authorized view.
- **PAC (`pac-service`)** — command catalog + calibration read and calibration write-through.
- **HIST (`hist-service`)** — history/audit timeline read for "Histórico".
- **EDGE (`edge-service`)** — device liveness read (ETag/304, `DASH poll 15s`).
- **GWY / REG** — routing and discovery for browser→DASH, EVNT→DASH, and every DASH→upstream call; the SSE
  channel is held through GWY.
- **MIG (`neuroassist-ai-migrations`)** — only relevant if the optional UI-preference table is ever built;
  in the MVP DASH needs no migrations.
- **Angular panel (`frontend/design-reference`)** — the human client whose screens define DASH's view-model
  and streaming contracts.
