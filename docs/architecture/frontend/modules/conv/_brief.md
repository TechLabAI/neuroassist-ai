# CONV — Structured Brief (source for specs / backlog / scaffold)

> Module: **CONV** — Conversação por Árvore de Falas · Service: `conv-service`
> Stack: Java 21 / Spring Boot · Nature: **new module, retrofit into a production system**
> Closed decisions carried in: ADR-012, ADR-013, ADR-014, ADR-015, ADR-017, ADR-018 (all `accepted`).

This is a **new module retrofit** onto a system already in production. Real backend services already
exist for GEST / EVNT / PAC / ACS / HIST / DASH / EDGE under `backend/services/*`. CONV is a new
deployable that consumes those existing surfaces by reference; it does not extend `pac-service`
(ADR-013) and does not own the patient identity it operates under (ADR-012).

## 1. Canonical name and prefix

- Prefix: `CONV` (uppercase)
- Canonical name: `conv` — service `conv-service`, frontend lib `conv`
- Package base: `com.neuroassist.conv`

## 2. Problem the tool solves (1–2 sentences)

A patient with an advanced neurodegenerative disease needs to say everyday things ("I want water with
ice", "reposition me", "call Danilo") beyond the emergency alarms that GEST/EVNT already cover. CONV
gives them a navigable **speech tree** (category → phrase) they drive by residual gesture (via GEST) or
by direct touch/mouse, with **semantic search** to jump straight to the closest phrase, and playback in
the patient's **own cached synthesized voice**.

## 3. Primary and secondary persona

- **Primary — "Paciente com sessão" (patient-with-session):** the person who operates the tree to
  communicate. A first-class actor (ADR-012, Opção C), authenticated by a persistent, non-expiring,
  revocable session **provisioned by the caregiver** and **modeled/owned in `acs-service`, not in CONV**.
  Its RBAC is narrow: `conv.arvore.navegar` / `conv.no.selecionar`, with **no caregiver authority**.
- **Secondary — Cuidadora Familiar (Jamilly Amaral):** does not operate CONV runtime; she provisions and
  revokes the patient session (in ACS) and authors/curates the tree content in the **TREI** module (out
  of CONV scope). She is a consumer of CONV read surfaces only indirectly.

## 4. MVP scope (minimum viable)

1. Serve and navigate the speech tree: root → categories → phrases (`NoArvoreFala`), scoped to one patient
   and tenant (empresa).
2. **Assisted semantic search** over the tree via Elasticsearch kNN (retrieval-only) — given a captured
   intent, return the closest existing node in the active session locale. Reuse the existing
   `SearchPort`/`IndexPort` from `elastic-stack-client-lib` (do not reinvent).
3. **Node selection** returns the phrase text + a reference to the pre-cached TTS audio for the active
   locale; the frontend plays the cached audio, or falls back to the browser Web Speech API (OPS-004).
4. Bilingual EN/PT content served per the active session locale; a node may be active in one locale and
   pending in the other (ADR-018).
5. Static navigation fallback when Elasticsearch is unavailable (OPS-003) — the tree is still walkable
   from PostgreSQL without semantic search.
6. Consume the patient-with-session identity from ACS via JWT (verify scope), and resolve the
   `engineFeatureRef` vocabulary shared with GEST/PAC for the gesture→node binding.

## 5. Out of MVP scope

- **Authoring / editing / approval of tree content, embedding generation, and ElevenLabs synthesis** —
  all belong to the **TREI** module (ADR-016). CONV runtime never calls an LLM and never calls the
  ElevenLabs API (TECH-007, TECH-008); it only reads indexed content and plays cached audio.
- **Ownership of the patient-with-session actor** — schema, provisioning and revocation live in ACS
  (ADR-012). CONV only consumes the token.
- Gesture detection / computer vision (GEST + edge browser engine), device pairing/heartbeat (EDGE),
  event/alarm generation (EVNT), immutable audit storage (HIST), admin panel rendering (DASH).
- Angular i18n mechanism choice for administrative UI (ADR-018 §5) — a frontend concern, not CONV backend.
- Any async broker / event bus (`messaging.enabled = false`) and any object-storage for audio (MVP uses a
  simple local filesystem, ADR-017).

## 6. Mandatory external integrations

- **Elasticsearch 9.4.1** (index `arvore-fala`, already declared in `project-context.json`) — the semantic
  retrieval engine. Reuse `elastic-stack-client-lib` ports. **Gap to resolve in specs:** kNN/`dense_vector`
  search is not yet expressible through the current `SearchPort.search(indexPattern, queryDslMap)`
  signature (top-level `knn` clause) — see doc 06.
- **ACS (`acs-service`)** — JWT issuer/validator for the patient-with-session actor + the JWT blacklist
  used for immediate session revocation (ADR-012). Consumed by reference (shared realm), not owned.
- **PAC (`pac-service`) / GEST (`gest-service`)** — resolve the shared `engineFeatureRef` vocabulary so a
  gesture recognized by GEST binds to a tree node (ADR-013 complementary decision). Read-only, by reference.
- **Audio storage** — local filesystem holding the pre-synthesized MP3/Opus files (ADR-017); CONV runtime
  reads the file referenced by the ES document; it never writes audio.
- **No third-party API on the CONV runtime path** (no LLM, no ElevenLabs at runtime).

## 7. TechLab AI hub dependencies / cross-module events

- **TechLab AI:** none (`ai.hasAi = false`). CONV runtime is retrieval-only; no orchestration, knowledge,
  document_composition or analytics capability is consumed on the runtime path.
- **Messaging:** disabled (`stack.messaging.enabled = false`). All cross-service calls are synchronous
  REST over Eureka (REG) + Spring Cloud Gateway (GWY). Audit-worthy facts (node selected, session-scoped
  navigation) are sent to HIST synchronously.

## 8. Anchor domain event

**"Nó da árvore selecionado"** (SpeechNodeSelected) — the patient selects a phrase node and the system
returns its text + cached audio reference; this is the central act CONV exists to serve. Secondary anchor:
**"Frase encontrada por busca"** (PhraseMatched) — a semantic search resolves a captured intent to the
closest existing node.
