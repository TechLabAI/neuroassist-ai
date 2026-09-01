# 01 — Architectural Vision — TREI (Frontend)

## 1. Intent

At the end, a **family caregiver** can sit down at the caregiver web app, open a
**"Treinamento"** screen, and *grow the patient's speech tree by talking to an
assistant*: she describes the everyday things the patient needs to say, the assistant
proposes new categories and phrases — **in each language, independently** — and she
approves the ones that belong in the tree, one side of a two-key approval. She never
types raw data, never edits a database, and never has to wait on a developer to add "I
want water with ice" to Márcio's world.

TREI-FE is the **authoring surface** of the speech tree. It is deliberately the *only*
screen in the whole product where an LLM produces new content — and it produces that
content **through `trei-service`**, never in the browser. TREI-FE renders a
conversation, renders draft proposals, and writes exactly one thing: the caregiver's
approval of her side of a proposal. Indexing (Elasticsearch), voice synthesis
(ElevenLabs) and the patient's confirmation all happen **elsewhere** — TREI-FE hands
off, it does not finish.

## 2. Problem statement

**Current journey (without TREI-FE):** CONV can serve a speech tree, but that tree can
only grow if someone edits `NoArvoreFala` data directly. There is no caregiver-facing
way to *author* new phrases, no language-aware review, and no safe place for an LLM to
propose content. The only AI in the product today is on-device computer vision (GEST);
there is no surface that says, unambiguously, "here — and only here — an assistant is
suggesting words for your family member." Adding a phrase means a developer and a
migration.

**New journey:** authoring is *first-class, caregiver-operated, and AI-assisted*. A
dedicated "Treinamento" screen in the caregiver shell hosts a conversation with an
assistant (served by `trei-service`, which owns the Claude/Anthropic call). The
assistant proposes categories/phrases per locale; each proposal appears as a card with
a **dual, per-language approval indicator** — caregiver side and patient side. The
caregiver approves her side; the proposal waits, as a draft, for the patient to confirm
his side asynchronously in CONV (Tela 09). Only when both sides agree for a locale does
the backend index that phrase and synthesize its audio. The tree grows without code, in
both languages, with two humans in the loop and a clear visual line between "the
assistant is generating here" and "the patient's app never generates anything."

## 3. Personas (job-to-be-done)

- **Jamilly Amaral (Family caregiver) — primary.** JTBD: "Help me add the exact
  everyday phrases Márcio needs — in Portuguese *and* English — by talking to an
  assistant that suggests them, and let me approve what goes into his tree, without
  editing raw data or waiting on a developer." Operates **Tela 08** in the caregiver
  shell: runs the LLM conversation (`ChatConversationPanel`), switches the active locale
  (`LocaleTabs`), reviews each `ProposedNodeCard`, **approves the caregiver side**
  (`confirmadoCuidadora`), edits a proposal before approving, and ends the session
  leaving pending proposals as drafts.
- **Márcio Amaral (Patient) — secondary, off-TREI.** JTBD (satisfied elsewhere):
  "Let me confirm the new phrases at my own pace, without talking to any AI directly."
  He **never operates TREI**; he confirms the *patient side* asynchronously in
  **CONV/Tela 09**. Inside TREI he is visible only as the *other* approver — the pending
  `confirmadoPaciente` badge on each card — so the caregiver can see what still needs his
  confirmation.

## 4. MVP scope (in)

- **AI authoring conversation (`ChatConversationPanel`)** — render the caregiver ↔
  assistant turns for the active training session; caregiver input by text. First-class
  **loading** (assistant responding) and **error** (`trei-service` / Anthropic failure)
  states. The FE never calls the Anthropic API and never generates content locally
  (ADR-005 local, TECH-007).
- **Per-locale authoring (`LocaleTabs`, PT/EN)** — the active locale drives which
  `conteudo.<locale>` is being proposed and reviewed; proposals and approval status are
  **independent per locale** (ADR-018). A node may be ready in one language, pending in
  the other.
- **Proposed-node review + caregiver approval (`ProposedNodeCard`)** — list draft
  `NoArvoreFala` (`status = rascunho`) with a **dual per-locale confirmation indicator**
  (`confirmadoCuidadora` / `confirmadoPaciente`); **Aprovar** writes the caregiver side,
  **Editar** adjusts a proposal before approval. TREI-FE writes **only** the caregiver
  side (ADR-007 local).
- **Session lifecycle (`EndSessionButton`)** — empty-state CTA "Iniciar treinamento";
  end a session without discarding pending proposals (they stay as drafts).
- **Explicit "AI generates here" affordance** — a persistent badge ("Treinamento — não é
  o app do paciente") marking TREI as the single generative surface, structurally
  distinct from the CONV runtime (TECH-007).
- **EN/PT bilingual UI + content authoring** — UI chrome bilingual (mechanism TBD at
  specs); authored content per active locale, independent approval per locale (ADR-018).
- **Caregiver-shell integration** — TREI registers as a normal caregiver-shell module
  ("Treinamento" sidebar item) in `neuroassist-ai-web`, guarded by the caregiver session
  + authoring authority.

## 5. Out of scope (MVP)

- **Patient-side confirmation (Tela 09)** — module **CONV**, patient shell (ADR-016);
  TREI writes `confirmadoCuidadora` only, never `confirmadoPaciente`.
- **Elasticsearch indexing + ElevenLabs synthesis** — `trei-service`/backend side
  effects on dual approval (ADR-015/017); TREI-FE neither indexes nor synthesizes.
- **The LLM integration itself** — prompt assembly, the Anthropic call, moderation — all
  `trei-service` (ADR-016, TECH-007/008); TREI-FE renders the conversation only.
- **Speech-tree runtime navigation / voice playback (Tela 06)** — module CONV.
- **Free-text patient editing** — patient interactions live in CONV; not a TREI concern.
- **Voice input for the caregiver conversation** — deferred; MVP is text input.
- **Angular i18n mechanism choice** (`@angular/localize` vs transloco) — specs decision
  (ADR-018 §5 TODO); TREI fixes the requirement, not the mechanism.
- Scaffolding of `libs/trei/*` internals — phase M-scaffold (`/fe-architect-scaffold`),
  since TREI has **no BA prototype** to promote from (D-493 promote path does not apply).

## 6. Architectural principles

- **Standalone-first** — every TREI component is `standalone: true`; the only `NgModule`
  is the lazy module entry, which lives as a **library** (`@neuroassist/trei`, shared
  **ADR-006**), not in `apps/`.
- **Signals-first** — conversation state, proposal list, active locale and session
  lifecycle live in private `signal<T>` inside facades, exposed via `.asReadonly()`;
  **no NgRx**, no manual `BehaviorSubject` (matches CONV/GEST FE and the prototype
  `data-access.ts` pattern).
- **Lazy-by-default** — TREI is loaded via `@neuroassist/trei`; features are
  `loadComponent`-lazy where composed.
- **OnPush mandatory** on every component.
- **Generation is server-mediated, never client-side** — the FE holds no Anthropic key,
  never calls the model, and never fabricates a proposal; every generated token arrives
  through `trei-service` (ADR-005 local, TECH-007). This is the single non-negotiable
  boundary that keeps the product's "runtime never generates" invariant (CONV) intact.
- **Two-key approval, one key here** — TREI-FE owns and writes exactly the *caregiver*
  side of approval; the patient side is CONV's. No TREI code path can set
  `confirmadoPaciente` (ADR-007 local).
- **Locale independence** — nothing in TREI cross-contaminates languages: a proposal
  approved in PT says nothing about EN; the active-locale signal is the single selector
  (ADR-018).
- **Authoring ≠ runtime, made visible** — the "Treinamento — não é o app do paciente"
  affordance is a *principle*, not decoration: TREI must never be mistakable for the
  patient's communication surface (TECH-007, wireframe §5.5).
- **Boundary discipline** — `scope:trei` imports only `scope:trei` + `scope:shared`;
  any cross-module composition happens at `scope:web` (`nx-boundaries.md`).

## 7. Success signals

- **First-token latency**: the assistant's first rendered token appears ≤ 2.0 s after
  the caregiver sends a turn (perceived responsiveness of the conversation), assuming
  `trei-service` is healthy; a visible loading state covers longer LLM latency.
- **Zero client-side generation**: 100% of proposed content originates from
  `trei-service`; the browser bundle contains **no** Anthropic SDK and **no** model
  credential (asserted by dependency/lint check + e2e network assertion — no direct
  `api.anthropic.com` call ever leaves the browser).
- **Approval-side integrity**: TREI writes `confirmadoCuidadora` only; **zero** writes to
  `confirmadoPaciente` from any TREI route (asserted in e2e).
- **Locale integrity**: switching the active locale switches which proposals/approvals
  are shown; approving PT never flips EN, and vice-versa (asserted in e2e).
- **No-loss session end**: ending a training session with pending proposals never
  discards them — they remain drafts and reappear on the next session (asserted in e2e).
- **Authoring-surface clarity**: the "not the patient app" affordance is present on every
  TREI route; no TREI route renders the patient speech-tree runtime (route-absence check).
- **p95** proposal-list render ≤ 300 ms on the target caregiver device once data is
  loaded.
