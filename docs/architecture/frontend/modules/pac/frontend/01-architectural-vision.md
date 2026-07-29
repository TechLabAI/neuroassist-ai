# 01 — Architectural Vision — PAC (Frontend)

## 1. Intent

At the end, a non-technical caregiver can, on one screen, look at Márcio's full
list of commands — *"água", "dor", "chamar emergência"* — understand at a glance
what each gesture means and how sensitive it is, and add or adjust any of them in
plain language, **with confidence that what she sees on screen is what the device
will actually act on**. PAC-FE is the **authoring surface** of the command
catalog and the patient record: it turns "this movement means help" into a
curated, honestly-stated mapping — it never detects and never alarms.

## 2. Problem statement

**Current journey (without PAC-FE):** a patient's meanings live in the caregiver's
head and in ad-hoc notes. There is no single, editable place that says *"this
gesture = this need = this urgency"*, no way to tune one command without disturbing
another, and — worst for safety — no honest signal of whether a change she just
made is actually live on the device or still pending.

**New journey:** the command catalog is **first-class and self-describing**. Each
command is an independently calibrated entry (label, gesture, sensitivity, urgency,
timing). The caregiver edits in her own vocabulary; the surface stays honest about
each command's status — *tested / not tested*, *active on device / pending sync* —
so she never assumes a safety-critical command is armed when it is not (ADR-005).

## 3. Personas (job-to-be-done)

- **Jamilly Amaral (Cuidadora Familiar) — primary.** JTBD: "Let me describe and
  adjust what each of Márcio's gestures means, in my words, and always show me
  honestly whether the change is really active." Uses `calibração` repeatedly, in
  short interactions, as Márcio's abilities drift.
- **Danilo Amaral (technical conductor) — secondary, onboarding only.** JTBD:
  "During first setup, register the patient and create the first working command
  before I hand the panel to the family." Uses the patient step of onboarding and
  the command editor once.

## 4. MVP scope (in)

- **Command catalog** on `calibração`: list view (per command: label, gesture
  description, urgency badge, sensitivity, tested/sync status) + empty state.
- **Command editor** (new/edit): `rotulo`, `urgencia` (emergência/rotina),
  `primitivoPrincipal` + optional `primitivoSecundario` (composite), `sensibilidade`
  (baixa/média/alta), and the gesture-timing window — with a live **preview card**.
- **Per-command independent calibration** — editing one command never re-tunes
  others.
- **TipoGesto selection + inline "add new type"** — reads the GEST catalog; the
  new-type request is delegated to GEST (naming ≠ detectability).
- **Patient registration** — onboarding step 2 (create/read `Paciente` on the
  active tenant) and a patient read-model for Home/DASH.
- **Honest command state** — `não testado / reconhecido` and `ativo no
  dispositivo / pendente de sincronização` surfaced per command (ADR-005 local).
- **GEST live-test composition slot** — editor hosts `GestureLiveTestComponent`
  at the shell page level (PAC never imports `scope:gest`).

## 5. Out of scope (MVP)

- Live gesture recognition / webcam classification (GEST + edge agent).
- Event generation, alarm orchestration, false-positive feedback (EVNT).
- Tenant creation, caregiver invitation, roles, patient↔caregiver linkage (ACS —
  onboarding steps 1 and 5).
- Device pairing / heartbeat / edge status (EDGE — onboarding step 3).
- The `TipoGesto` primitive catalog *authoring* and its detectability lifecycle
  (GEST owns it; PAC reads and requests additions).
- Dashboard rendering, history browsing, notifications (DASH, HIST).

## 6. Architectural principles

- **Standalone-first** — every PAC component is `standalone: true`; the single
  `NgModule` is only the `pac.module.ts` lazy entry (ADR-006 / module-pattern).
- **Signals-first** — catalog and editor state live in `signal<T>` inside PAC
  facades, exposed via `.asReadonly()`; no NgRx, no manual `BehaviorSubject`.
- **Lazy-by-default** — the module is loaded via `@neuroassist/pac`; the command
  editor / patient forms are `loadComponent`-lazy.
- **OnPush mandatory** on every component.
- **Honest-state discipline** — the catalog never claims a command is active or
  recognized without evidence; edit ≠ live (ADR-005 local), mirroring GEST's
  degraded-mode honesty.
- **Boundary discipline** — `scope:pac` imports only `scope:pac` + `scope:shared`;
  the GEST live-test is composed at `scope:web`, never imported directly
  (nx-boundaries.md, ADR-006).
- **Author, don't detect** — PAC owns the meaning of a gesture; the recognition of
  it stays entirely in GEST/edge.

## 7. Success signals

- **Time-to-comprehension** of the catalog ≤ 2 s of reading — a caregiver can state
  what each command means and its urgency without opening the editor.
- **Calibration isolation**: editing/saving one command provably leaves every other
  command's parameters unchanged (asserted in e2e).
- **Sync honesty**: 100% of commands with an unsynced edit are visibly marked
  `pendente de sincronização`; no command is shown as active before the device
  acknowledges it (ADR-005).
- **Draft safety**: a live test can run on an unsaved draft, but no draft is ever
  presented as part of the active catalog until explicitly saved.
- **p95 catalog-load** ≤ 800 ms and **p95 save** ≤ 1 s against the API gateway.
