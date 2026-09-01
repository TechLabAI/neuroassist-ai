# SPEC-04 — Data Access — CONV (Frontend)

> Hexagonal ports (prototype `core/data-access.ts` pattern): each resource has a **Port**
> (abstract) with a **HttpAdapter** (production) and a **MockAdapter** (Estágio 0);
> `DATA_PROVIDERS` binds Port→adapter, promote/scaffold swaps Mock→Http. Facades inject
> Ports and expose **signals**; `HttpClient`/RxJS live only in the adapter I/O edge. All
> HTTP goes through GWY via `@neuroassist/shared/access` with the patient-session token.
> Endpoints below are the **BE contract of record** — `conv-service` api-contract.md
> (never redefined here, only consumed). Public base: `/api/v1/conv`.

## 1. Master resource table

| Resource | Endpoint (via GWY) | Service | Facade | Operations | Cache? |
|---|---|---|---|---|---|
| Speech tree | `GET /api/v1/conv/tree` | `SpeechTreePort` → `SpeechTreeHttpAdapter` | `SpeechTreeFacade` | `getTree(rootOnly, locale)`, `listLevel(parentId, locale)` | yes — light in-memory, per active locale, per session |
| Semantic assist | `POST /api/v1/conv/search` | `SemanticSearchPort` → `SemanticSearchHttpAdapter` | `SemanticAssistFacade` | `search(intent, k, locale)` | no — query-dependent, staleness undesirable |
| Node selection | `POST /api/v1/conv/nodes/{id}/select` | `NodeSelectionPort` → `NodeSelectionHttpAdapter` | `SpeechTreeFacade` (→ `TtsPlaybackFacade`) | `select(nodeId, via, locale, clientEventId)` | no — idempotent by `clientEventId` |
| Gesture binding | `POST /api/v1/conv/bindings/resolve` | `BindingPort` → `BindingHttpAdapter` | `SpeechTreeFacade` (gesture bridge) | `resolve(engineFeatureRef, locale)` | no |
| Cached audio | resolved from `audioRef.uri` via GWY | `AudioPort` → `AudioHttpAdapter` | `TtsPlaybackFacade` | `resolve(audioRef)` → blob URL | yes — session-only in-memory blob (never persisted, SEC-002) |
| Proposals (Tela 09) | **⚠ not in api-contract v1** (gap — see §6) | `ProposalPort` → `ProposalHttpAdapter` | `ProposalConfirmFacade` | `listPending(locale)`, `confirm(nodeId, locale, clientEventId)`, `adjust(nodeId, variant, locale)`, `reject(nodeId, locale)` | no |

## 2. DTOs (mirror the BE JSON — see backend-api-cross-link)

```ts
// GET /tree — api-contract §2.1
interface TreeResponseDto {
  arvoreId: string; pacienteId: string;
  localeRequested: Locale; localeServed: Locale; localesSuportados: Locale[];
  degraded: boolean; retrieval: 'static';
  nodes: TreeNodeDto[];
}
interface TreeNodeDto {
  id: string; parentId: string | null; tipo: 'categoria' | 'frase';
  ordem: number; engineFeatureRef: string | null;
  conteudo: {
    locale: Locale; texto: string | null;
    contentStatus: 'ativo' | 'pending_locale';
    audioRef: { present: boolean; formato?: 'mp3' | 'opus' };
    selecionavel: boolean;
  };
}

// POST /search — api-contract §2.2
interface SearchRequestDto { intent: string; k?: number; locale: Locale; }
interface SearchResponseDto {
  localeServed: Locale; degraded: boolean; retrieval: 'semantic' | 'static';
  matches: { noId: string; texto: string; score: number; tipo: 'frase';
             engineFeatureRef?: string | null; audioRef: { present: boolean; formato?: string } }[];
  reason?: string;   // present when degraded
}

// POST /nodes/{id}/select — api-contract §2.3
interface SelectRequestDto { via: 'gesto' | 'toque'; locale: Locale; clientEventId: string; }
interface SelectResponseDto {
  noId: string; localeServed: Locale; texto: string;
  audioRef: { present: boolean; uri?: string; formato?: 'mp3' | 'opus' };
  playbackMode: 'audio-cacheado' | 'web-speech'; degraded: boolean; via: 'gesto' | 'toque';
}

// POST /bindings/resolve — api-contract §2.4
interface BindingRequestDto { engineFeatureRef: string; locale: Locale; }
interface BindingResponseDto { engineFeatureRef: string; noId: string | null; bound: boolean; selecionavel?: boolean; }
```

## 3. Operations behaviour

- **`getTree` / `listLevel`** — `GET /tree` (optionally `rootOnly=true` for the first paint, then `?parentId=` per level via the `:categoriaId` route param). Structure is locale-invariant; only `conteudo` varies. The light session cache keeps already-loaded levels for the active locale and is invalidated on locale switch and session start (doc 05 §3).
- **`search`** — `POST /search`; `k` defaults to 5 (BE clamps > 50). A `degraded: true` / empty `matches` response is rendered as "no assist, keep navigating" — **never** an error (OPS-003).
- **`select`** — `POST /nodes/{id}/select` with a client-minted `clientEventId` UUID (idempotency, SPEC-05). On `200` the response drives `TtsPlaybackFacade` (`playbackMode: audio-cacheado` → resolve+play `audioRef.uri`; `web-speech` → speak text; SPEC-07). Only `frase` nodes are selected — a `categoria` activation descends the tree (no `/select` call).
- **`resolve`** (gesture bridge) — when the GEST engine emits a recognized `engineFeatureRef`, the bridge calls `POST /bindings/resolve`; `bound: true && selecionavel: true` → treat as a selection of `noId` (calls the same `SpeechTreeFacade.selectNode/activateNode`); `bound: false` is a no-op, **not** an error. `engineFeatureRef` is passed through opaque (never parsed — gap G4).
- **`resolve(audioRef)`** — fetch the blob from `audioRef.uri` via GWY on first play, keep the blob URL in a session Map; revoke on session end.

## 4. Selection ↔ playback flow (single writer)

```
touch output ─┐
              ├─► SpeechTreeFacade.selectNode(id)  (ONE selectedNodeId signal, ADR-005)
gesture bridge┘        │ activateNode(id):
                       │   tipo==='categoria' → listLevel(id)   (descend, no /select)
                       │   tipo==='frase'     → NodeSelectionPort.select(id, via, locale, clientEventId)
                       │                        → TtsPlaybackFacade.speak(SelectResponseDto)
```

## 5. Criterios de aceite (EARS)

- WHEN a `categoria` node is activated, THE SYSTEM SHALL call `GET /tree` for that node's level and SHALL NOT call `POST /select`.
- WHEN a `frase` node is activated, THE SYSTEM SHALL call `POST /nodes/{id}/select` carrying `via`, the active `locale`, and a client-minted `clientEventId`.
- WHEN `POST /select` returns `playbackMode: audio-cacheado`, THE SYSTEM SHALL resolve `audioRef.uri` and play the cached audio; WHEN it returns `playbackMode: web-speech`, THE SYSTEM SHALL speak the returned `texto` via Web Speech.
- WHEN the gesture channel emits an `engineFeatureRef`, THE SYSTEM SHALL call `POST /bindings/resolve` and, IF `bound = true` and `selecionavel = true`, THEN THE SYSTEM SHALL treat the resolved `noId` as a selection through the same single selected-node model.
- IF `POST /bindings/resolve` returns `bound = false`, THEN THE SYSTEM SHALL treat it as a no-op and SHALL NOT surface an error.
- WHEN `POST /search` returns `degraded = true`, THE SYSTEM SHALL keep manual tree navigation available and SHALL NOT surface a blocking error.
- WHILE the active locale is unchanged within a session, THE SYSTEM SHALL serve already-loaded tree levels from the in-memory cache.
- WHEN the locale switches, THE SYSTEM SHALL invalidate the in-memory tree-level cache.
- THE SYSTEM SHALL hold resolved cached-audio blobs only in memory for the session and SHALL NOT persist them to any client-side store (SEC-002 / LGPD).
- THE SYSTEM SHALL pass `engineFeatureRef` to the backend as an opaque string and SHALL NOT parse its internal structure.

## 6. Contract gaps surfaced to specs-validate / BE backlog

- **Proposals resource (Tela 09) absent from api-contract v1.** The FE needs `conv-service` endpoints for list-pending / confirm / adjust / reject of proposals (`confirmadoPaciente` write). The data-model (`conv_no_conteudo.confirmado_paciente`) supports it, but no HTTP endpoint is contracted. `ProposalPort` is specified here as the FE requirement; the endpoint must be added to the BE contract before scaffold wires the HttpAdapter (MockAdapter carries it until then). See backend-api-cross-link §Proposals.
- **`audioRef.uri` resolution route.** api-contract §2.3 returns `audioRef.uri` (e.g. `conv/p-1024/en/n-42.opus`) but does not define the audio-fetch endpoint/base explicitly; assumed served via GWY under the CONV surface. Confirm the exact audio path with BE at scaffold.
