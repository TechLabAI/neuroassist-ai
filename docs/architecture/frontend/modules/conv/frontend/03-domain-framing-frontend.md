# 03 — Domain Framing (Frontend) — CONV

> Mirror of the backend domain, from the UI's point of view. Do **not** redefine the
> ubiquitous language — this references the BE data model (`_extensions.data_model`
> in `project-context.json`), the CONV `_brief.md`, and ADR-013/015/017/018.

## 1. Ubiquitous language (mirror of BE)

- **NoArvoreFala** — a node of the speech tree. `parentId` (null = root), `tipo`
  (`categoria` | `frase`), and — per ADR-018 — **per-locale content**:
  `conteudo: { pt: { texto, audioRef, confirmadoCuidadora, confirmadoPaciente },
  en: { … } }`. Optional `engineFeatureRef` binds a recognized gesture to this node.
  Structure (`parentId`/`tipo`/`engineFeatureRef`) is shared across locales; only
  spoken content varies by locale.
- **Categoria** — a non-leaf `NoArvoreFala` (`tipo=categoria`); navigating it descends
  one level.
- **Frase (leaf)** — a leaf `NoArvoreFala` (`tipo=frase`); selecting it speaks the
  phrase and may emit an `Evento`.
- **SessaoPaciente** — the patient session actor (ACS, ADR-012): `status`
  (`ativa`|`revogada`), provisioned by the caregiver, non-expiring, revocable. CONV-FE
  consumes its token; it does not create/revoke it.
- **engineFeatureRef** — the unified gesture vocabulary (PAC/GEST, commit `89f4ee5`)
  mapping a recognized gesture to a tree node without a CONV-owned mapping model.
- **Comando / Evento** — existing PAC/EVNT concepts; a selected node *may* map to a
  `Comando`, which emits a structured `Evento` (mapping still open, ADR-013 §12/6).
- **Retrieval result** — the closest-phrase node(s) returned by `conv-service`'s
  semantic search (ADR-015); a read-model, not a CONV entity.
- **audioRef** — reference to the pre-synthesized cached audio for a node's locale
  content (ADR-017); the FE resolves it to an audio URL, it does not hold the binary.

## 2. View models (draft, per screen)

### `SpeechTreeVM` (Tela 06)

| field | type | notes |
|---|---|---|
| `level` | `SpeechNodeVM[]` | nodes at the active level (categories or leaves) |
| `selectedNodeId` | `string \| null` | the currently highlighted node (any channel) |
| `path` | `BreadcrumbItem[]` | category path from root to active level |
| `activeChannel` | `'gesture' \| 'touch' \| null` | last channel that moved selection |
| `locale` | `'pt' \| 'en'` | active patient locale |
| `status` | `'idle' \| 'loading' \| 'ready' \| 'empty' \| 'error'` | level load state |

### `SpeechNodeVM`

| field | type | notes |
|---|---|---|
| `id` | `string` | `NoArvoreFala.id` |
| `tipo` | `'categoria' \| 'frase'` | |
| `label` | `string` | `conteudo[locale].texto` |
| `hasAudio` | `boolean` | `conteudo[locale].audioRef != null` |
| `pendingInLocale` | `boolean` | active-locale content not yet dual-approved |
| `engineFeatureRef` | `string \| null` | gesture that reaches this node |

### `PlaybackVM` (feature-tts)

| field | type | notes |
|---|---|---|
| `state` | `'idle' \| 'loading' \| 'speaking' \| 'fallback' \| 'unavailable'` | |
| `source` | `'cached-audio' \| 'web-speech' \| 'text-only'` | which path spoke |
| `nodeId` | `string \| null` | node being spoken |

### `ProposalConfirmVM` (Tela 09)

| field | type | notes |
|---|---|---|
| `pending` | `ProposalVM[]` | nodes `confirmadoCuidadora=true, confirmadoPaciente=false` in locale |
| `locale` | `'pt' \| 'en'` | active locale |
| `status` | `'idle' \| 'loading' \| 'empty' \| 'saving' \| 'saved' \| 'error'` | |

### `ProposalVM`

| field | type | notes |
|---|---|---|
| `id` | `string` | node id |
| `text` | `string` | proposed phrase in active locale |
| `categoryLabel` | `string` | parent category label |
| `variants` | `string[]` | short LLM variants offered for "Ajustar" (no free text) |

## 3. UI events (user-triggered, mutate state)

- `channelSelectionMoved(nodeId, channel)` — a gesture or touch moved the selection.
- `nodeActivated(nodeId)` — the selected node is committed (descend if category;
  speak if leaf).
- `backRequested()` — ascend one tree level.
- `assistTriggered()` — request semantic-search assist from `conv-service`.
- `localeSwitched(locale)` — change active patient locale.
- `playbackReplayRequested(nodeId)` — replay the current phrase.
- `proposalConfirmed(nodeId)` / `proposalAdjusted(nodeId, variant)` /
  `proposalRejected(nodeId)` — Tela 09 actions.

## 4. State transitions (UI level)

**Speech tree level:** `idle → loading → ready | empty | error`; from `ready`,
`nodeActivated(category)` → `loading` (next level); `backRequested()` → previous
level (`ready`).

**Playback:** `idle → loading → speaking → idle` (cached audio); on missing audio
`idle → fallback(speaking via Web Speech) → idle`; on total failure
`idle → text-only(unavailable)`.

**Selection (channel-agnostic):** `unselected → selected(nodeId)`; re-entering a
different node id updates selection regardless of which channel fired.

**Proposal confirm (Tela 09):** `idle → loading → (empty | ready) → saving → saved |
error`; `saved` returns to the pending list (minus the resolved node).

## 5. Invariants (client-side)

- **One selected node** — at most one node is "selected" at a level; gesture and touch
  update the same slot; the two channels never produce two selections.
- **Channel independence** — no core navigation/playback logic branches on
  `activeChannel`; the channel is presentational metadata only (ADR-005).
- **Leaf-only speech** — only `tipo=frase` triggers playback; categories only descend.
- **Locale-consistent playback** — a node is spoken only in the active locale, using
  that locale's `texto`/`audioRef`; a node pending in the active locale is never
  spoken in another locale as a silent substitute.
- **Voice-never-silent** — a leaf activation always resolves to one of {cached audio,
  Web Speech, prominent text}; never to nothing (ADR-007).
- **No free-text on patient side** — "Ajustar" picks from `variants` only; the patient
  never types (motor-control invariant).
- **Read-mostly runtime** — CONV-FE reads the tree and search result; the only writes
  are Tela 09 confirmations (`confirmadoPaciente`) and optional `Evento` emission.
- **No client-side vector logic** — similarity/ranking is never computed in the
  browser; the FE renders `conv-service`'s result as-is (ADR-015).
