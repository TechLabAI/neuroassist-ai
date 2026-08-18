# 01 — Architectural Vision — DASH (Frontend)

## 1. Intent

At the end, Jamilly opens the panel and, in under two seconds, **knows Márcio is
fine** — a large green check, "Márcio está bem", the device shown online, and one
tap to today's history. DASH-FE is the product's **home and composition surface**:
it is the calm, always-truthful front door that answers the one question that
matters ("O Márcio está bem?"), frames the whole app (sidebar + topbar), hosts the
places where the live alarm and routine notifications appear, and keeps every other
destination (histórico, calibração) two clicks away.

## 2. Problem statement

**Current journey (without DASH-FE):** the pieces exist in isolation — an alarm can
fire (EVNT), history accumulates (HIST), the catalog can be tuned (PAC), a device
reports online/offline (EDGE) — but there is **no home**. Nothing gathers them,
nothing answers "está tudo bem?" at a glance, and there is no shell to navigate
between them or a consistent place for routine notifications to land. The caregiver
has capabilities but no dwelling.

**New journey:** DASH-FE is the panel's composition root. It renders the **shell**
(layout mestre §3 — sidebar navigation, topbar with the active patient and the
device-status slot) and the **home** (Tela 01): a `StatusCard` that is **calm and
green by default**, driven by the live `activeEvent` signal re-exported by EVNT and
the device badge from EDGE. Before the shell, the **Entrada** (Tela 00) sets the
brand tone. Routine notifications surface as a discreet toast (rendered by EVNT into
DASH's shell outlet) and are collected in a lightweight **notification center**. The
home never fabricates calm: an active emergency pre-empts the whole screen (EVNT's
overlay, mounted in DASH's global outlet) before the caregiver even reaches Home.

## 3. Personas (job-to-be-done)

- **Jamilly Amaral (Cuidadora Familiar) — primary.** JTBD: "Open the panel and know
  in one glance Márcio is fine, and reach anything in at most two clicks." Lives in
  Home + the sidebar.
- **Coordenador de clínica — secondary.** JTBD: "The same one-glance panel, per
  patient, inside the clinic tenant." Same shell/home, tenant = clínica.
- **Danilo Amaral (condutor técnico) — indirect.** JTBD: "Have a configuration entry
  (Onboarding) visible only to my role, without cluttering the caregiver's day."
  Reflected in role-gated navigation.

## 4. MVP scope (in)

- **App shell (Layout Mestre §3)** — the composition root: sidebar navigation
  (`Status` · `Histórico` · `Calibração` · `Onboarding`[admin-only]), topbar (active
  patient name + a device-status slot filled by EDGE), and the **global outlet host**
  where EVNT mounts the alarm overlay and routine toast (`ShellOutletRegistry`).
- **Entrada (Tela 00)** — brand entry / "Entrar no painel", full-bleed, outside the
  shell chrome; delegates the actual sign-in to the session layer (ACS/`auth-session`).
- **Home / Status (Tela 01)** — a `StatusCard` ("Márcio está bem", timestamp of last
  check) that is **green/calm by default** and reflects the live `activeEvent`; the
  device badge (from EDGE); and a quick link "Ver histórico de hoje" (into HIST).
- **Notification center** — a shell-level bell/inbox listing recent routine dispatches
  (the discreet toast itself is EVNT's; DASH provides the host and the review list).
- **Module registry composition** — DASH wires `MODULE_DESCRIPTORS` so every module's
  lazy route and menu item appears in the shell (`scope:web` composition).
- **State machine** surfaced for Home: `idle → loading → (ok | alarm-preempted | error)`,
  where `alarm-preempted` is owned by EVNT's overlay but observable at the shell.

## 5. Out of scope (MVP)

- Rendering the **emergency alarm** and the **routine toast**, and the confirm-attendance
  flow — **EVNT-FE** (DASH only provides the outlet host and consumes `activeEvent`).
- History timeline, filters and `Evento.feedback` — **HIST-FE** (DASH only links to it).
- Command catalog / calibração / urgência editing — **PAC-FE**.
- Device online/offline data, pairing and heartbeat — **EDGE-FE** (DASH composes the
  badge in the topbar; the data and its truth belong to EDGE).
- Caregiver/user identity, auth, roles and tenant provisioning — **ACS-FE**; DASH only
  reads the session and gates navigation by role.
- The recognition itself and its landmark schema — **GEST** / edge.
- Off-panel channels (SMS, e-mail, mobile push), dashboard personalization/widgets — post-MVP.
- Any AI / suggestion behaviour — no AI drawer (`ai-drawer: não`); deterministic
  rendering only (`ai.hasAi = false`).

## 6. Architectural principles

- **Standalone-first** — every DASH component is `standalone: true`; the single
  `NgModule` is only the `dash.module.ts` lazy entry (ADR-006 / module-pattern).
- **Signals-first** — home summary, active-status projection and notification list live
  in `signal<T>` inside the facade, exposed via `.asReadonly()`; no NgRx, no manual
  `BehaviorSubject`.
- **Lazy-by-default** — the module is loaded via `@neuroassist/dash`.
- **OnPush mandatory** on every component.
- **Composition-at-the-shell** — DASH *is* the composition root. Cross-module reads
  (EVNT `activeEvent`, EDGE device badge, HIST quick-link) happen at `scope:web`, never
  by `scope:dash` importing another module (nx-boundaries.md). See ADR-005.
- **Server-driven calm home** — the calm home reads a single `dash-service` summary
  aggregate rather than fanning out to N module APIs; live pre-emption stays with EVNT
  (ADR-005, the module's critical decision).
- **Calm by default, honest always** — green is the default; the screen only leaves calm
  when EVNT reports an active emergency. The UI never invents "está bem"; loading/error
  states are always shown.
- **Tenancy** — every backend call carries `X-Empresa-Id`; the panel is scoped to the
  caregiver↔patient vínculo (ACS), never crossing tenants.

## 7. Success signals

- **Time-to-answer** — from opening the panel to a rendered Home status ≤ 2 s on the
  reference dataset (Princípio 2.1); Entrada → Home in one CTA.
- **Two-click reach** — every MVP destination (Histórico, Calibração) is reachable in
  ≤ 2 clicks from Home (Princípio 2.7); asserted in e2e.
- **Calm honesty** — Home is green iff there is no active emergency; an active emergency
  always pre-empts before Home renders calm (no false "está bem"); asserted in e2e.
- **Shell integrity** — the device-status badge and the alarm/toast outlets are always
  mounted; no blank chrome, no orphan region; role-gated items (Onboarding) hidden for
  the caregiver role.
- **State honesty** — loading (StatusCard skeleton), error (clear non-technical message
  with retry) and offline-device (amber badge, screen stays calm) are always surfaced.
