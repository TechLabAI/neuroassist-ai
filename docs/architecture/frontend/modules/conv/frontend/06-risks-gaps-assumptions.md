# 06 — Risks, Gaps, Assumptions — CONV (Frontend)

## 1. Risks

- **UX / motor control (high).** CONV's whole point is a patient with severely
  reduced motor control operating it unaided. Hit targets, dwell times, selection
  tolerance and the gesture-vs-touch fallback must all be tuned for that; a design
  that feels fine to a developer can be unusable for the patient. Mitigation: large
  targets, no free text, no time-pressure; validate with the real patient early.
- **Gesture-channel reliability on a phone (high, from ADR-014).** The in-browser
  detection engine's "always-on" only holds under Chromium + visible tab + Wake Lock.
  A phone's aggressive screen-lock/app-switching makes "detection stopped silently"
  the worst failure mode. Mitigation: touch is always a full peer channel; the channel
  indicator must show gesture off honestly; reuse GEST's watchdog discipline.
- **iOS excluded (medium, from ADR-014).** Android/Chromium only until Safari supports
  background capture. If the family only has an iPhone for the patient, the gesture
  channel is unavailable (touch-only) — a scope constraint to communicate, not a bug.
- **Voice fallback fidelity (medium, from ADR-017).** A phrase not yet synthesized (new
  since the last TREINAMENTO) speaks in a generic browser voice, not the patient's
  cloned voice. Acceptable per OPS-004, but frequent fallback would be jarring —
  monitor fallback rate.
- **Prototype-to-production has NO CONV prototype (high — see §2).** Unlike DASH/GEST,
  there is no built CONV prototype to promote; M6 starts from the BA wireframe + design
  language. Higher design/estimation risk than a normal prototype-first module.
- **M6 promote — design-system theme not vendorized (high, measured on THIS project).**
  Learning-layer signal `fe-architect-promote:app-theme:design-system-not-vendorized`:
  on `neuroassist-ai` WEB, 369 utility-class uses resolved only in the prototype theme,
  46 only in the Estágio-0 theme, 86 in neither — a silent failure (build stays green,
  CSS is absent). CONV's patient shell is a **new shell**, so it is especially exposed.
  Mitigation (for M6): the promote must copy `design-system/{theme.css,fonts.html}`
  over the generated fallback, derive `fonts.html` from the `--font-*` tokens, and run
  a token-parity gate. Do not trust a green build as proof of correct UI.
- **Empty e2e suites read as green (medium).** Learning signals
  `fe-nx-register-module:e2e-stub:empty-describe-passes-as-green` and
  `…generated_e2e_specs_remain_empty_on_disk`: because CONV has no prototype, its BDD
  features risk being generated without scenarios and passing silently. Mitigation
  (specs/scaffold): every CONV feature must carry real scenarios or emit an explicit
  `test.fixme()`; enable `--strict` at the autopilot preflight.

## 2. Known gaps

- **CONV patient screens absent from the prototype clone.** `apps/neuro-assist-ai-web`
  contains only admin screens (entrada, home, alarme, historico, calibracao,
  onboarding). Tela 06 (árvore), Tela 07 (ativação), Tela 09 (confirmação) and
  `patient-shell.component.ts` referenced in the intake **do not exist** in the clone;
  they live as *design intent* in the BA source wireframe (`techlab-ba/projects/
  neuro-assist-ai/design/wireframe.md`, Telas 06–09 + §6/§7). The clone is stale vs.
  the BA source — a SYNC gap, not a design gap.
- **`NoArvoreFala` per-locale content schema (ADR-018 open).** JSON column vs.
  normalized `NoArvoreFalaConteudo` is undecided — the FE VMs assume a `conteudo.<locale>`
  map but the concrete contract comes from BE specs.
- **Locale-pending UX undecided (ADR-018 open TODO).** What the patient sees for a node
  active in PT but pending in EN when EN is the active locale (show PT with a notice, or
  hide the node) is not decided — flagged for specs.
- **Node → `Comando` → `Evento` mapping (ADR-013 §12/6 open).** Whether/how selecting a
  tree node also emits a structured `Evento` is undecided; the FE keeps it optional.
- **i18n mechanism unchosen (ADR-018 open TODO).** `@angular/localize` vs
  transloco/ngx-translate for UI chrome — a specs decision.
- **Patient shell: separate app vs guarded root (open).** Whether the restricted shell
  is its own Nx app (`apps/patient-web`) or a guarded root inside the web app is a
  specs/scaffold decision; the invariant (no caregiver route reachable) holds either way.
- **Semantic-assist trigger UX undefined.** How the patient *invokes* assist (a
  dedicated gesture, a category, an idle-timeout suggestion) is not specified.
- **GEST-engine input contract for CONV.** GEST exposes a recognized-gesture signal;
  the exact abstraction CONV's input bridge consumes at the composition root must be
  defined with the GEST module (avoid a `scope:conv → scope:gest` import).

## 3. Assumptions to validate

- Tree volume stays small (dozens of nodes, 1 patient) — the per-navigation fetch +
  light session cache is adequate; revisit only for institutional tenants.
- The patient can reliably operate at least one channel (gesture or touch) on the
  target device; a patient who can operate neither is out of MVP scope.
- `conv-service` returns cached-audio references and semantic-search results already
  scoped to the patient and locale — the FE does not filter/rank.
- The patient-session token (ACS) carries exactly `conv.arvore.navegar` /
  `conv.no.selecionar` and nothing caregiver-level (ADR-012) — the FE trusts but the
  BE enforces.
- Cached audio clips are small (MP3/Opus, voice bitrate) and safe to hold in-memory for
  a session without a persistent client store.
- The BA wireframe (Telas 06/09) is a faithful-enough starting point that M6 promote
  can build production screens from it without a separate prototype pass.

## 4. Critical dependencies

- **`conv-service` (BE)** — speech-tree, semantic-search, and audio-reference endpoints
  behind GWY (ADR-013/015/017). CONV-FE cannot function without these contracts.
- **ACS (`acs-service`)** — patient-session JWT + RBAC (ADR-012); provisioning/
  revocation (Tela 07) is ACS-owned; the patient shell must reflect a revoked session
  (session ends → shell exits) — coordinate the revocation-reflection contract.
- **GEST in-browser engine** — the recognized-gesture signal as an input channel
  (ADR-014), plus its camera-lifecycle/Wake-Lock discipline.
- **Web Speech API** — voice fallback availability on the target device.
- **Shared libs** — `@neuroassist/shared/{access, ui-*, error-model, observability}`
  and the design-system tokens (`design-system-tokens.md`).
- **BA source wireframe** — the only source of CONV screen design until a prototype or
  M6 promote exists.
