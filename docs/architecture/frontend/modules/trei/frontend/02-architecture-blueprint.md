# 02 — Architecture Blueprint — TREI (Frontend)

## 1. System boundary

TREI-FE is a **client of the API gateway** (`neuroassist-ai-api-gateway`, GWY) via the
shared access layer (`@neuroassist/shared/access` HTTP base). It talks to **exactly one**
backend surface — `trei-service` — and to **nothing else**: it never calls Anthropic,
never calls ElevenLabs, never talks to Elasticsearch, and never touches `conv-service`.
Everything TREI needs — the training conversation (LLM-proxied), the draft
`NoArvoreFala[]`, and the caregiver-side approval write — arrives through `trei-service`
behind the gateway. There is **no on-device AI** in TREI (unlike GEST/CONV, which run
MediaPipe / Web Speech on-device): TREI's browser code is purely presentational over a
server-mediated conversation.

## 2. External actors and systems

- **Caregiver (Jamilly)** — authenticated by the **caregiver session** (JWT issued by
  ACS), carrying the authoring authority (`trei.treinamento.*`; final scope names fixed at
  specs). TREI-FE *consumes* this token; it does not perform login.
- **`trei-service` (BE)** — the single backend collaborator. Owns: the Anthropic
  (Claude) integration and prompt assembly; the training-session and conversation store;
  the draft `NoArvoreFala` proposals; the caregiver-side approval write; and the
  post-approval side effects (ES index + ElevenLabs synth) that TREI-FE never sees.
- **Anthropic (Claude) API** — reached **only** by `trei-service` (ADR-016, TECH-007).
  Never reachable from the browser.
- **Elasticsearch (`arvore-fala`) / ElevenLabs** — reached **only** by `trei-service` as
  post-dual-approval side effects (ADR-015/017). No FE dependency.
- **ACS (`acs-service`)** — caregiver JWT issuer/validator; consumed by reference.
- **CONV (Tela 09)** — a *sibling* surface, not a TREI collaborator: it later reads the
  caregiver-approved drafts for the patient to confirm. TREI and CONV meet on the shared
  `NoArvoreFala` state, asynchronously — not through a TREI→CONV call.

## 3. End-to-end user flows

### Golden path — author + approve a phrase (Tela 08)

```
Caregiver shell (neuroassist-ai-web) → "Treinamento"
  └─ Tela 08 — Treinamento (badge: "Treinamento — não é o app do paciente")
     [ PT ▾ ]  [ EN ]                                   ← LocaleTabs (active locale = PT)
     ┌───────────────── ChatConversationPanel ─────────────────┐
     │ 💬 Assistente: "Quer adicionar uma frase para pedir      │
     │                 água com gelo?"                          │  (served by trei-service,
     │ 💬 Cuidadora:  "Sim, e também uma para chamar o Danilo"  │   which owns the Claude call)
     │ [ escrever… ]                                    (loading… while assistant responds)
     └──────────────────────────────────────────────────────────┘
                 │  trei-service proposes drafts → NoArvoreFala (status = rascunho)
                 ▼
     Propostas pendentes (idioma ativo: PT)              ← ProposedNodeCard[]
     ┌──────────────────────────────────────────────────────────┐
     │ 💧 "Quero água com gelo"     [PT ✅ cuidadora][PT ⏳ paciente]│ [Editar]
     │ 📞 "Chamar o Danilo"         [PT ⏳ cuidadora][PT ⏳ paciente]│ [Aprovar]
     └──────────────────────────────────────────────────────────┘
                 │  Aprovar → write conteudo.PT.confirmadoCuidadora = true (trei-service)
                 ▼
     ┌────────────────────────────────────────────────────────────┐
     │ caregiver side approved for PT →                           │
     │   proposal stays a DRAFT, now awaiting the PATIENT side     │  (ADR-007 local)
     │   (patient confirms asynchronously in CONV / Tela 09)      │
     │ when BOTH sides true for PT → trei-service indexes (ES) +   │  (ADR-015/017,
     │   synthesizes audio (ElevenLabs)  ← FE never sees this      │   BE side effect)
     └────────────────────────────────────────────────────────────┘
                 │
                 ▼
     [ Encerrar sessão de treinamento ]  → pending proposals remain drafts (never discarded)
```

### Locale-switch path

```
LocaleTabs: PT → EN
   → active-locale signal flips to EN
   → ProposedNodeCard[] re-filters to conteudo.EN.* status; approval indicators show EN state
   → the SAME node may read "✅ cuidadora" in PT and "⏳ cuidadora" in EN (independent, ADR-018)
   (approving EN writes conteudo.EN.confirmadoCuidadora only — never touches PT)
```

### Edge cases

- **No session started** → empty state, CTA "Iniciar treinamento"; no conversation, no
  proposals shown.
- **Assistant is responding** → `ChatConversationPanel` shows a loading turn; input is
  disabled or queued; the caregiver is never left guessing whether her turn landed.
- **`trei-service` / Anthropic failure** → explicit error state on the conversation
  ("falha ao falar com o assistente — tente novamente"); previously-drafted proposals
  remain visible and approvable; the failure never blocks approving what already exists.
- **Approve conflict / stale draft** (proposal changed server-side since load) → the
  approval write returns a conflict; the FE refetches the card and asks the caregiver to
  re-confirm, rather than silently overwriting.
- **Node pending in the other locale** → shown as `⏳` for that locale; the caregiver may
  switch locales to author it, or leave it pending. Never auto-approved across locales.
- **Session ended with pending proposals** → a notice that they remain as drafts until
  the patient confirms in CONV; nothing is lost.

## 4. Internal boundaries (target lib structure — materialized at scaffold)

> This is the **target** shape (`/fe-architect-scaffold`). TREI has **no BA prototype**,
> so it is *scaffolded* from these specs, not *promoted* from a design-reference clone
> (D-493). This project's entry lives as a **library** (`@neuroassist/trei`, shared
> ADR-006), not in `apps/`.

```
apps/trei/                              # thin buildable/serveable host (ADR-006)
└── src/app/app.routes.ts              # loadChildren: () => import('@neuroassist/trei')

libs/trei/
├── shell-entry/                        # importPath @neuroassist/trei, tags scope:trei type:app
│   ├── trei.module.ts                  # the single lazy NgModule
│   ├── trei.contract.ts                # TreiMenuMetadata ("Treinamento") + permissions + flags
│   ├── trei.routes.ts                  # TREI_ROUTES[] (Tela 08)
│   ├── trei.permission-map.ts          # trei.treinamento.* authorities
│   ├── trei.feature-flags.ts
│   ├── trei.nav.config.ts
│   ├── trei.bootstrap.ts               # init/destroy lifecycle
│   └── TREI_MODULE_DESCRIPTOR (barrel index.ts)
├── access/                             # caregiver-session guard(s) (trei.* authorities)
├── data-access/                        # HTTP adapters + signal facades (conversation, proposals, session)
├── feature-training-session/           # Tela 08 — ChatConversationPanel, LocaleTabs, EndSessionButton
├── feature-proposal-review/            # ProposedNodeCard list, Aprovar/Editar, dual-confirmation indicator
├── integration/                        # TreiShellIntegrationService (registers into caregiver shell)
├── navigation/                         # breadcrumb / "Treinamento" nav state
├── ui/ (optional)                      # trei-local presentational components (badge, chat bubble, status chip)
└── styles/ (optional)                  # module CSS (Tailwind utilities + tokens; no SCSS token files)
```

Boundary rule (`nx-boundaries.md`): `scope:trei` may import `scope:trei` + `scope:shared`
only. TREI has no cross-module compile-time dependency (no GEST engine, no CONV) — its
only collaborators are `trei-service` (over HTTP) and the shared libs; the caregiver-shell
registration happens at `scope:web` via `TREI_MODULE_DESCRIPTOR`.

## 5. Cross-cutting concerns

- **Auth / entitlement** — the caregiver-session JWT (ACS) is attached by the shared
  access layer; a TREI route guard checks the authoring authority (`trei.treinamento.*`).
  TREI is a **caregiver-shell** module: it *is* registered into `neuroassist-ai-web`
  (unlike CONV, which is patient-shell-only). No patient authority is ever declared here.
- **AI-surface isolation (product invariant)** — TREI is the *only* generative surface;
  the "Treinamento — não é o app do paciente" badge is a cross-cutting requirement on
  every TREI route (TECH-007, wireframe §5.5). The Anthropic call is server-side only;
  the browser bundle must carry no model SDK/credential (ADR-005 local; enforced by
  dependency check + e2e network assertion).
- **Observability** — via the shared observability layer, correlated by the pipeline
  trace convention; key signals: session started/ended, assistant turn latency,
  proposal approved (locale + side), LLM/`trei-service` error rate.
- **Design tokens** — Tailwind utilities + CSS-variable tokens from the project design
  system (`design-system-tokens.md`); **no** ad-hoc colors/spacing. TREI has no
  prototype, so it composes existing caregiver-shell chrome + shared UI primitives; the
  design-system token set is the single source (Learning: FE promote token-parity
  finding — TREI must reference only tokens present in the app `theme.css`, never invent
  its own theme).
- **i18n (EN/PT)** — two distinct layers: **UI chrome** (labels, buttons) via the Angular
  i18n mechanism chosen at specs; **authored content** (proposed phrase text per locale)
  selected by the active-locale signal. The requirement is fixed here; the mechanism is a
  specs decision (ADR-018 §5).
- **LGPD / sensitive data** — proposed phrase content is health-adjacent personal data
  (SEC-002); the training conversation may reference the patient by name. TREI-FE holds it
  only transiently for display; nothing is persisted client-side beyond ephemeral runtime
  state, and the conversation/proposals live in `trei-service`, not in the browser.
- **Error model** — `trei-service` `ProblemDetail` → `AppError` via
  `@neuroassist/shared/error-model` → caregiver-facing messages via the shared feedback
  layer; LLM/timeout failures degrade to an explicit, retriable error, never a blank
  screen.
