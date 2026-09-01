<!-- OUTPUT-SCHEMA
- Ubiquitous Language
- Aggregates and Entities
- Domain Events
- State Transitions
- Invariants
-->

# 03 — Initial Domain Framing

## Ubiquitous Language
- **Árvore de Falas (Speech Tree)** — the curated hierarchy of categories and phrases a patient navigates
  to communicate. One logical tree per patient.
- **NoArvoreFala (Speech Node)** — a node in the tree; either a **categoria** (branch) or a **frase** (leaf,
  something the patient can "say"). Identified by `id`, linked to its parent by `parentId` (null = root).
- **Conteúdo Localizado (Localized Content)** — the per-locale payload of a node: `texto` (displayed label
  / spoken text) + `audioRef` (reference to the pre-synthesized audio) + authoring confirmations. A node
  carries **one map of content per locale** (`pt`, `en`), authored independently (ADR-018).
- **AudioRef** — a reference (not the binary) to the cached audio file for a given locale, produced by TREI
  via ElevenLabs and stored on the filesystem (ADR-017). CONV reads it; it never creates it.
- **EngineFeatureRef** — the shared vocabulary token (unified in the real repo, GEST `frontend` ADR-008 D4)
  optionally bound to a node, so a gesture recognized by GEST maps directly to a tree node (ADR-013). Owned
  by PAC/GEST, referenced by CONV.
- **Paciente com sessão (Patient-with-session)** — the actor operating the tree; owned by ACS (ADR-012),
  consumed by CONV as a JWT with scopes `conv.arvore.navegar` / `conv.no.selecionar`.
- **Busca assistida (Assisted search)** — a retrieval-only kNN query over the indexed tree that returns the
  closest existing node to a captured intent (ADR-015). Never generative.
- **Locale ativo (Active locale)** — the language (`pt` | `en`) of the current patient session; drives which
  localized content and audio reference are served.
- **Modo degradado (Degraded mode)** — serving the tree without Elasticsearch (static navigation, OPS-003)
  and/or without cached audio (Web Speech fallback, OPS-004).

## Aggregates and Entities
- **`ArvoreFala` (aggregate root, draft).** The tree for one patient within a tenant. Scopes and guards
  the set of `NoArvoreFala`. Attributes: `id`, `pacienteId` (reference to PAC's `Paciente`, by id only),
  `tenantId` (empresa), locales supported. Navigation and locale-availability invariants live here.
- **`NoArvoreFala` (entity, part of the aggregate).** `id`, `parentId` (null = root), `tipo`
  (`categoria` | `frase`), `engineFeatureRef` (optional, single per node), `ordem` (sibling ordering), and
  a per-locale **`ConteudoLocalizado`** map. `parentId`/`tipo`/`engineFeatureRef`/`ordem` are single per
  node — the tree structure is identical across locales; only content varies (ADR-018).
- **`ConteudoLocalizado` (value object).** Per-locale: `texto`, `audioRef` (nullable), `confirmadoCuidadora`,
  `confirmadoPaciente`, `status` (`ativo` | `pendente`). Populated by TREI; read by CONV.
- **`EngineFeatureRef` (value object).** The shared vocabulary token; resolved read-only against PAC/GEST.
- **`AudioRef` (value object).** `path`/`uri` + `formato` (mp3/opus) + `locale`; a pointer, never the bytes.

> CONV is read-mostly at runtime: it **serves** nodes and **records** selections. Creation/edition of nodes,
> content and audio is TREI's write path, not CONV's — the same schema/index/store, different owner.

## Domain Events
Anchor and supporting events (emitted synchronously to HIST; `messaging.enabled = false`, so these are
audit facts over REST, not bus messages):
- **`SpeechNodeSelected` (anchor)** — "Nó da árvore selecionado": patient selected a `frase` node; carries
  `noId`, `pacienteId`, `locale`, `viaGesto|viaToque`, and whether cached audio or Web Speech was used.
- **`PhraseMatched`** — "Frase encontrada por busca": an assisted search resolved a captured intent to the
  closest node; carries the query context, the winning `noId`, score, and `degraded` flag.
- **`TreeNavigated`** — a category was opened / navigation step taken (lighter-weight; may be sampled).
- **`ConvServedDegraded`** — CONV served a request in degraded mode (ES down or audio absent); useful for
  operational signal.

> Content lifecycle events (node created/approved, audio synthesized, embedding indexed) belong to **TREI**,
> not CONV — they are named here only to mark the boundary.

## State Transitions
CONV owns little mutable state at runtime; the meaningful transitions are per-locale **content availability**
(authored in TREI, observed by CONV) and **session validity** (owned by ACS, enforced by CONV):
- **Node content per locale:** `pendente` → `ativo` (TREI approves + indexes) → `ativo` (re-edited/re-indexed)
  → `pendente`/withdrawn. CONV serves only `ativo` content for the active locale; a node `ativo` in PT and
  `pendente` in EN is served in PT and hidden/flagged in EN (exact UX is an open ADR-018 TODO — see doc 06).
- **Retrieval mode:** `semantico` (ES reachable) ⇄ `estatico` (ES unavailable, OPS-003). Transition is
  detected per request, not persisted.
- **Playback mode:** `audio-cacheado` (audioRef present + store reachable) ⇄ `web-speech` (absent/unreachable,
  OPS-004). Resolved per selection.
- **Patient session:** `ativa` → `revogada` (caregiver revokes in ACS → blacklisted). CONV observes via the
  blacklist and rejects immediately; it never drives this transition.

## Invariants
1. **Retrieval-only:** CONV never produces node text or audio at runtime — it can only return content that
   already exists as `ativo` in the store/index (ADR-015/017). No LLM, no ElevenLabs call on the CONV path.
2. **Tree structure is locale-invariant:** `parentId`, `tipo`, `engineFeatureRef`, `ordem` are identical
   across locales; only `ConteudoLocalizado` varies (ADR-018).
3. **Root is unique and parent-less:** exactly one root per `ArvoreFala` (`parentId = null`); every other
   node resolves to the root by following `parentId` (no cycles, no orphans).
4. **Tenant + patient scoping:** every node, query and selection is scoped to one `tenantId` (empresa) and
   one `pacienteId`; cross-tenant/cross-patient reads are impossible by construction (schema-per-tenant).
5. **Only `frase` nodes are selectable/spoken;** `categoria` nodes are navigable only.
6. **Identity is external:** CONV never authenticates the patient itself — it only validates an ACS-issued
   token and honors the ACS blacklist (ADR-012). A blacklisted session has zero authority.
7. **Served locale = active session locale**, with an explicit fallback rule for a node missing that locale
   (rule TBD, ADR-018 TODO) — CONV must never silently serve the wrong-locale text as if it were the active
   one.
