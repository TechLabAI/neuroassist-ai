# 01 — Architectural Vision — CONV (Frontend)

## 1. Intent

At the end, a patient with severely limited motor control can, on their own phone,
open a calm full-screen speech tree, move through **category → phrase** using
whatever they can still operate — an eye blink / eyebrow raise picked up by the
in-browser gesture engine, *or* a direct touch — and have the chosen phrase spoken
out loud **in their own cloned voice**. No caregiver has to guess, and no
credential of the caregiver's is reachable from that screen.

CONV-FE is the **patient's own voice surface**: it does not synthesize speech, does
not run the semantic search, and does not author content — it renders a curated
tree, makes one node "selected" from any input channel, and plays back a pre-made
audio clip (or a spoken fallback), inside a shell that exposes nothing but this.

## 2. Problem statement

**Current journey (without CONV-FE):** the platform can raise an alarm when a
configured emergency gesture fires (GEST→EVNT), but there is no channel for ordinary
day-to-day communication. If Márcio wants water with ice, to be repositioned, or to
call a specific person, he has no structured way to say *which* — the caregiver
guesses, or the need goes unmet. There is also no patient-safe surface: the only
Angular shell today is the caregiver's admin shell, full of routes (Histórico,
Calibração, Onboarding) a patient must never reach.

**New journey:** communication is *first-class and patient-operated*. A restricted
patient shell hosts a speech tree the patient navigates at their own pace, through
the input channel they can actually use, with the same movement engine already
proven for alarms. Selecting a leaf phrase speaks it in the patient's cloned voice
from cached audio; if that audio is missing, a browser-native voice still speaks the
text so communication never stops. Newly authored phrases (proposed in the separate
TREINAMENTO phase) surface here for the patient to confirm on their own schedule.

## 3. Personas (job-to-be-done)

- **Márcio Amaral (Patient) — primary.** JTBD: "Let me say the exact everyday thing
  I need, using the movement I can still make, and speak it in my own voice — without
  anyone guessing for me." Operates Tela 06 (speech tree) and Tela 09 (confirm new
  phrases) inside the restricted patient shell, in short, unhurried interactions,
  possibly with reduced fine motor control.
- **Jamilly Amaral (Family caregiver) — secondary, off-runtime.** JTBD: "Activate
  Márcio's phone as a patient device and keep the session alive; approve the phrases
  he wants." She provisions the session (Tela 07, **ACS-owned**, not CONV-FE) and
  approves the caregiver side of proposals (Tela 08, **TREI**, not CONV-FE). Inside
  CONV-FE she appears only as the *other* approver whose confirmation gates what
  Márcio sees on Tela 09.

## 4. MVP scope (in)

- **Speech Tree navigator (Tela 06)** — renders the active level (categories or leaf
  phrases), highlights the currently-selected node, back-navigation to the parent
  level. Empty / loading / error / TTS-unavailable states are all first-class.
- **Agnostic dual input channel** — one selected-node model fed by *either* a
  recognized gesture (in-browser GEST engine) *or* direct touch/mouse, with no manual
  channel pre-selection and a discreet indicator of which channel is live (ADR-005).
- **Voice output** — play the cached TTS audio referenced by the selected node; fall
  back to native Web Speech API (browser voice) when the audio is absent or the
  source is unavailable (ADR-007 / OPS-004).
- **Semantic-search assist (consume-only)** — render the closest-phrase result
  returned by `conv-service`; the FE never queries Elasticsearch directly (ADR-015).
- **Patient confirmation of proposals (Tela 09)** — list caregiver-approved pending
  nodes for the active locale; **confirm** / **adjust** (choose among short LLM
  variants, never free text) / **reject**, using the same agnostic input channel.
- **Restricted patient shell** — a separate, minimal shell hosting only Tela 06 +
  Tela 09; no admin sidebar, no caregiver routes, no caregiver credential reachable.
- **EN/PT bilingual UI + content** — the patient's active locale drives which phrase
  content and audio are shown/played; a node may be active in one locale and pending
  in the other (ADR-018).

## 5. Out of scope (MVP)

- Semantic search itself (Elasticsearch / kNN) — `conv-service` backend (ADR-015).
- TTS synthesis (ElevenLabs) — TREINAMENTO phase, backend; CONV-FE only *plays*
  cached audio (ADR-017).
- Speech-tree authoring / LLM conversation (Tela 08) — module **TREI**, caregiver
  shell (ADR-016).
- Patient-device activation and session **provisioning/revocation** (Tela 07) —
  **ACS** domain; CONV-FE only consumes the resulting session token (ADR-012).
- Caregiver login and server-side RBAC enforcement — ACS backend.
- The gesture *detection* model itself (landmark extraction / classification) — that
  is the GEST engine; CONV-FE consumes its recognized-gesture signal only.
- Free-text editing of phrases (patient may lack fine motor control — adjust = pick
  from short variants only).
- Choice of the Angular i18n mechanism (`@angular/localize` vs transloco) — decided
  at specs (ADR-018 TODO[arquiteto]).
- Promotion to `libs/conv/` — phase M6 (`/fe-architect-promote`).

## 6. Architectural principles

- **Standalone-first** — every CONV component is `standalone: true`; the only
  `NgModule` is the lazy module entry, which lives as a library (`@neuroassist/conv`,
  shared **ADR-006**), not in `apps/`.
- **Signals-first** — tree state, selected node, playback state and locale live in a
  private `signal<T>` inside a facade, exposed via `.asReadonly()`; **no NgRx**, no
  manual `BehaviorSubject` (matches the prototype's `data-access.ts` and GEST FE).
- **Lazy-by-default** — CONV is loaded via `@neuroassist/conv`; features are
  `loadComponent`-lazy where composed.
- **OnPush mandatory** on every component.
- **Channel-agnostic selection** — gesture and touch are two adapters onto one
  selected-node model; no feature may branch its core logic on which channel fired
  (ADR-005).
- **Voice never fails silently** — every phrase has a spoken path: cached audio, else
  Web Speech fallback, else a visually prominent text rendering (ADR-007).
- **Patient-shell isolation** — the restricted shell renders no caregiver route at
  all (not hidden by CSS/state); isolation is by *absence of route*, not by guard on
  a shared shell (BA wireframe §6, ADR-012/014).
- **Boundary discipline** — `scope:conv` imports only `scope:conv` + `scope:shared`;
  cross-module composition (e.g. embedding the GEST engine) happens at `scope:web`
  (`nx-boundaries.md`).
- **Retrieval, not generation, at runtime** — the runtime never generates or
  translates text; it only navigates/plays curated, dual-approved content (ADR-015/018).

## 7. Success signals

- **Time-to-speak** ≤ 1.0 s from node selection to audio start when cached audio is
  present; fallback (Web Speech) starts speaking ≤ 1.5 s.
- **Channel parity**: the same leaf node is reachable and selectable through gesture
  and through touch, producing an identical selected-node state and identical
  playback — no divergence between channels (asserted in e2e).
- **Zero caregiver-surface leakage**: no caregiver route (Histórico / Calibração /
  Onboarding / caregiver credential) is reachable from the patient shell — 100%, by
  route absence (asserted in e2e).
- **Voice-never-silent**: 100% of leaf selections produce *some* spoken or prominent
  visual output; missing cached audio degrades to Web Speech, never to nothing.
- **Locale integrity**: switching the patient locale switches phrase text and audio
  together; a node pending in the active locale is handled explicitly (never spoken
  in the wrong language).
- **p95** speech-tree level render ≤ 300 ms on the target Android/Chromium device.
- **Zero camera leaks** when the gesture channel is used: no `MediaStream` track
  remains active after leaving Tela 06 (reuses the GEST engine's teardown discipline).
