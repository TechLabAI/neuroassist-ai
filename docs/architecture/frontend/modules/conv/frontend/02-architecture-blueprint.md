# 02 — Architecture Blueprint — CONV (Frontend)

## 1. System boundary

CONV-FE is a **client of the API gateway** (`neuroassist-ai-api-gateway`, GWY) via
the shared access layer (`@neuroassist/shared/access` HTTP base). It never calls a
backend service directly, never talks to Elasticsearch, and never calls ElevenLabs.
Everything CONV needs — speech tree, semantic-search result, cached-audio references
— arrives through `conv-service` behind the gateway. The one non-gateway browser API
CONV uses is the **Web Speech API** (native TTS fallback) and the **MediaPipe
in-browser gesture engine** (reused from GEST), both of which run entirely on-device.

## 2. External actors and systems

- **Patient (Márcio)** — authenticated by a **patient session** (JWT issued by ACS,
  RBAC `conv.arvore.navegar` / `conv.no.selecionar`; ADR-012). CONV-FE *consumes*
  this token; it does not perform login.
- **Patient device (phone)** — Android/Chromium only (ADR-014), carrying **two
  independent identities**: a device token (`edge.device`, for the GEST channel) and
  the patient session (for CONV). CONV-FE uses the patient session for its API calls
  and consumes the GEST engine's recognized-gesture signal locally.
- **`conv-service` (BE)** — speech tree (`NoArvoreFala`), semantic-search endpoint
  (retrieval result), cached-audio references.
- **In-browser GEST engine** — MediaPipe FaceLandmarker (WASM local, no CDN), emits a
  recognized-gesture id; reused as the gesture input channel.
- **Web Speech API** — browser-native voice, fallback output only.
- **ACS (Tela 07)** and **TREI (Tela 08)** — sibling surfaces that *feed* CONV
  (session token; approved proposals) but are not part of CONV-FE.

## 3. End-to-end user flows

### Golden path — say a phrase (Tela 06)

```
Patient shell (restricted)
  └─ Tela 06 — Speech Tree
     [ 🥤 Preciso de algo ] [ 🛏 Reposicionar ] [ 💬 Falar ]   ← categories (root level)
                 │  input: gesture (GEST engine) OR touch — same target
                 ▼
     [ 💧 Água com gelo ]  [ 🧊 Só gelo ]  [ ‹ Voltar ]         ← leaf phrases
                 │  select leaf node "Água com gelo"
                 ▼
     ┌───────────────────────────────────────────┐
     │ speak(node):                              │
     │   audioRef present?  → play cached MP3    │  (ADR-017, patient's cloned voice)
     │   else               → Web Speech(text)   │  (ADR-007 fallback / OPS-004)
     │   optional: node→Comando? → emit Evento   │  (EVNT, mapping still open)
     └───────────────────────────────────────────┘
```

### Assisted path — semantic-search shortcut

```
Patient input (partial gesture sequence / assist trigger)
   → conv-service /search  (Elasticsearch kNN, BE-side, ADR-015)
   → FE receives the closest NoArvoreFala node(s)
   → SpeechTreeNavigator highlights/offers that node — patient confirms via any channel
   (FE renders the result only; it never scores/queries vectors itself)
```

### Confirmation path — new phrases (Tela 09)

```
Patient shell (restricted)
  └─ Tela 09 — Pending confirmations (active locale only)
     💧 "Quero água com gelo"   [ ✅ Confirmar ] [ ✏️ Ajustar ] [ ✕ Recusar ]
        confirmadoCuidadora=true, confirmadoPaciente=false
        │ Confirmar → write confirmadoPaciente=true (conv-service)
        │            → if caregiver side already true, BE triggers ES index + TTS synth
        │ Ajustar   → pick a short LLM-suggested variant (no free text)
        │ Recusar   → drop this locale's proposal
```

### Edge cases

- **Tree not configured for patient** → Tela 06 empty state with a discreet CTA for
  the caregiver to configure (authoring is TREI, not here).
- **Semantic search unavailable** → silent fallback to manual tree navigation
  (OPS-003); no error blocks communication.
- **Cached audio missing / audio source down** → Web Speech fallback; if that is also
  unavailable, the phrase text is shown full-bleed and prominent (ADR-007).
- **Gesture channel unavailable** (camera/permission/Wake Lock) → touch remains fully
  functional; the channel indicator shows gesture as off. Communication never depends
  on a single channel.
- **Node pending in active locale** → not spoken in the wrong language; shown per the
  locale-fallback rule decided at specs (ADR-018 open TODO).

## 4. Internal boundaries (target lib structure — materialized at M6 promote)

> This is the **target** shape (`/fe-architect-promote`, M6). This project's entry
> lives as a **library** (`@neuroassist/conv`, shared ADR-006), not in `apps/`.

```
apps/conv/                              # thin buildable/serveable host (ADR-006)
└── src/app/app.routes.ts              # loadChildren: () => import('@neuroassist/conv')

libs/conv/
├── shell-entry/                        # importPath @neuroassist/conv, tags scope:conv type:app
│   ├── conv.module.ts                  # the single lazy NgModule
│   ├── conv.contract.ts                # ConvMenuMetadata + permissions + flags
│   ├── conv.routes.ts                  # CONV_ROUTES[] (Tela 06, Tela 09)
│   ├── conv.permission-map.ts          # conv.arvore.navegar / conv.no.selecionar
│   ├── conv.feature-flags.ts
│   ├── conv.nav.config.ts
│   ├── conv.bootstrap.ts               # init/destroy lifecycle
│   └── CONV_MODULE_DESCRIPTOR (barrel index.ts)
├── patient-shell/                      # restricted shell (topbar only, no sidebar) — hosts Tela 06/09
├── access/                             # patient-session guard(s) (conv.* authorities)
├── data-access/                        # HTTP adapters + signal facades (tree, search, audio, proposals)
├── feature-speech-tree/                # Tela 06 — SpeechTreeNavigator, InputChannelIndicator, BackButton
├── feature-tts/                        # TTSPlayer (cached audio + Web Speech fallback)
├── feature-proposal-confirm/           # Tela 09 — PendingConfirmationList, Confirm/Adjust/Reject
├── integration/                        # ConvShellIntegrationService; GEST-engine input bridge (scope:web composition)
├── navigation/                         # breadcrumbs / tree-level state
├── ui/ (optional)                      # conv-local presentational components
└── styles/ (optional)                  # module CSS (Tailwind utilities + tokens; no SCSS token files)
```

Boundary rule (`nx-boundaries.md`): `scope:conv` may import `scope:conv` +
`scope:shared` only. Composition with the GEST engine (which is `scope:gest`) happens
at `scope:web` (the shell/app), never by a `scope:conv → scope:gest` import.

## 5. Cross-cutting concerns

- **Auth / entitlement** — the patient-session JWT (ACS, ADR-012) is attached by the
  shared access layer; a CONV route guard checks `conv.arvore.navegar` /
  `conv.no.selecionar`. The patient shell renders **no** caregiver route, so isolation
  is structural, not guard-only (defense in depth).
- **Observability** — via the shared observability layer (`@neuroassist/shared/...`),
  correlated by the pipeline trace convention; key UX signals: time-to-speak, channel
  used, fallback-voice used, locale, search-assist hit/miss.
- **Design tokens** — Tailwind utilities + CSS-variable tokens from the project design
  system (`design-system-tokens.md`); **no** ad-hoc colors/spacing. The prototype's
  full-bleed calm aesthetic (large targets, high contrast, Material Symbols) is the
  baseline for the patient shell.
- **i18n (EN/PT)** — cross-cutting and mandatory (ADR-018). Two distinct layers: **UI
  chrome** (labels, buttons) via the Angular i18n mechanism chosen at specs; **content**
  (phrase text + audio) selected per active locale from `NoArvoreFala.conteudo.<locale>`.
  The mechanism choice is a specs decision; the *requirement* is fixed here.
- **Accessibility / motor tolerance** — large hit targets, generous dwell/timeout,
  no free-text input on the patient side, no time-pressure interactions; the input
  channel must tolerate reduced fine motor control.
- **LGPD / sensitive data** — phrase content and the patient's cloned-voice audio are
  sensitive health/biometric data (SEC-002). CONV-FE holds them only transiently for
  playback; nothing is persisted client-side beyond ephemeral runtime state.
