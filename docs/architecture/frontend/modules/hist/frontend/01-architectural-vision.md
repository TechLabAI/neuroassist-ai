# 01 — Architectural Vision — HIST (Frontend)

## 1. Intent

At the end, when the immediate alarm is over, Jamilly opens **Histórico** and sees
Márcio's day, week or month laid out as a calm, human timeline — "14h32 — 'água' ·
Você confirmou às 14h33" — never a technical log. For each item she can answer one
question, **"Foi real?"**, and the system remembers her verdict forever. HIST-FE is
the product's **memory and trust surface**: it makes the past legible, lets the
caregiver correct the record (real / falso-positivo), and preserves an auditable
trail of who confirmed or edited what and when.

## 2. Problem statement

**Current journey (without HIST-FE):** events happen, alarms fire, confirmations are
pressed — and then they vanish. There is no way to look back over the day, no way to
tell the system "that one was a false positive", and no auditable trail of the
caregiver's actions. Trust erodes: the family cannot see whether the system is
getting better, and a clinic cannot audit who answered which emergency.

**New journey:** every event, alarm and confirmation/edit action lands in an
**immutable ledger** that HIST-FE renders as a period-filtered timeline (Hoje /
Semana / Mês, wireframe §5). Each item speaks the family's language — the command
label as *they* wrote it, an urgency badge, the timestamp and the confirmation line.
A single per-item action lets the caregiver record `real` / `falso_positivo`
(§4.7), which the ledger captures as an attributed, timestamped audit entry. Empty
periods are framed positively ("Nenhum evento neste período — ótimo sinal").

## 3. Personas (job-to-be-done)

- **Jamilly Amaral (Cuidadora Familiar) — primary.** JTBD: "Let me look back over
  Márcio's day in plain words and tell the system when it got one wrong." Lives in
  the timeline + the "Foi real?" action.
- **Coordenador de clínica — secondary.** JTBD: "Audit the history of this patient
  across the caregivers on shift — who confirmed what, and when." Same timeline,
  tenant = clínica, multiple linked caregivers.
- **Danilo Amaral (condutor técnico) — indirect.** JTBD: "Watch the false-positive
  rate over time during tuning." Reads the same history; never a day-to-day user.

## 4. MVP scope (in)

- **History timeline** — a period-filtered list (`Hoje` / `Semana` / `Mês` tabs)
  of past events, newest first, paginated for volume.
- **Legible event item** — command label (`Evento.comando.rotulo`, in quotes, the
  family's own word), urgency badge (emergência/rotina), recognized-at timestamp,
  and the confirmation line ("Você confirmou às 14h33").
- **Feedback action** — per-item "Foi real?" that transitions the verdict
  `nao_avaliado → real | falso_positivo` and renders the terminal state
  ("✓ Marcado real"); the action is disabled once terminal.
- **Audit-trail awareness** — the item reflects *who* confirmed/edited and *when*
  (attributed, from the immutable ledger); no destructive edit of the ledger from the UI.
- **List states** — empty ("Nenhum evento neste período — ótimo sinal"), loading
  (list skeleton), error (failure to fetch history), surfaced explicitly.
- **State machine** surfaced: `idle → loading → (loaded | empty | error)`, with a
  per-item `marking → marked | mark-error` sub-state for the feedback action.

## 5. Out of scope (MVP)

- Live emergency alarm overlay, routine toast, and the confirm-attendance flow —
  **EVNT-FE** (wireframe §4.2 / §4.6). HIST only *reads* the resulting events.
- Command catalog / urgência editing / calibração — **PAC-FE** (§5, Tela 04).
- Device online/offline badge and pairing — **EDGE-FE**.
- The recognition itself and its signal/landmark schema — **GEST** / edge.
- Export (CSV/PDF), advanced audit search/query builder, retention & redaction
  policy UI — post-MVP.
- Any AI / suggestion behaviour — the history screen has no AI drawer
  (`ai-drawer: não`); deterministic rendering only (`ai.hasAi = false`).

## 6. Architectural principles

- **Standalone-first** — every HIST component is `standalone: true`; the single
  `NgModule` is only the `hist.module.ts` lazy entry (ADR-006 / module-pattern).
- **Signals-first** — the timeline, active filter, pagination and per-item feedback
  state live in `signal<T>` inside the facade, exposed via `.asReadonly()`; no NgRx,
  no manual `BehaviorSubject`.
- **Lazy-by-default** — the module is loaded via `@neuroassist/hist`.
- **OnPush mandatory** on every component; long lists must repaint deterministically.
- **Read-first, immutable ledger** — HIST-FE never mutates history; the only write
  is the feedback verdict, which the backend records as a new attributed audit entry.
- **Human language over codes** — the item renders the family's `rotulo`, never an
  internal `event_type` (ADR-005, the module's critical decision).
- **Boundary discipline** — `scope:hist` imports only `scope:hist` + `scope:shared`;
  cross-module composition (Home "Ver histórico de hoje") happens only at `scope:web`
  (nx-boundaries.md).
- **Tenancy** — every backend call carries `X-Empresa-Id`; the history is scoped to
  the caregiver↔patient vínculo (ACS), never crossing tenants.

## 7. Success signals

- **Legibility** — 100% of history items render the family label and urgency badge;
  zero technical event codes surfaced (asserted in e2e).
- **Time-to-history** — from Home "Ver histórico de hoje" to a rendered day timeline
  ≤ 1 s on the reference dataset; list virtualized/paginated beyond N items.
- **Feedback integrity** — every "Foi real?" persists an attributed, timestamped
  verdict; the item never shows a verdict that was not confirmed by the backend
  (non-optimistic write); the action is idempotent and disabled once terminal.
- **State honesty** — empty/loading/error states are always shown; no blank screen,
  no spinner that never resolves; a fetch failure shows a retry affordance.
- **Audit fidelity** — the confirmation line ("quem/quando") matches the ledger;
  no client-side fabrication of confirmation metadata.
