# SPEC-03 — View Models — CONV (Frontend)

> View-models are TypeScript `readonly` records surfaced by facades as
> `Signal<T>` (`.asReadonly()`); `computed()` for derived fields; `effect()` only for
> side-effects (e.g. starting playback on leaf activation). No NgRx, no manual
> `BehaviorSubject`. Types mirror the BE `conv-service` contract (never redefine it).

## 1. Shared value types

```ts
type Locale = 'pt' | 'en';
type NodeTipo = 'categoria' | 'frase';
type ContentStatus = 'ativo' | 'pending_locale';   // mirrors api-contract §2.1/§5
type InputChannel = 'gesture' | 'touch';
type Via = 'gesto' | 'toque';                        // POST /select `via` (api-contract §2.3)
```

## 2. `SpeechTreeVM` (Tela 06) — owner `SpeechTreeFacade`

```ts
interface SpeechTreeVM {
  readonly level: readonly SpeechNodeVM[];   // nodes at the active level
  readonly path: readonly BreadcrumbItem[];  // root → active category
  readonly selectedNodeId: string | null;    // ONE slot, written by both channels
  readonly activeChannel: InputChannel | null; // presentational metadata only (ADR-005)
  readonly locale: Locale;
  readonly status: 'idle' | 'loading' | 'ready' | 'empty' | 'error';
}

interface SpeechNodeVM {
  readonly id: string;                 // NoArvoreFala.id
  readonly tipo: NodeTipo;
  readonly label: string | null;       // conteudo[locale].texto — null when pending_locale
  readonly contentStatus: ContentStatus;
  readonly selecionavel: boolean;      // api-contract: frase && contentStatus==='ativo'
  readonly hasAudio: boolean;          // audioRef.present
  readonly pendingInLocale: boolean;   // contentStatus === 'pending_locale'
  readonly engineFeatureRef: string | null; // opaque gesture token (never parsed, gap G4)
}

interface BreadcrumbItem { readonly nodeId: string; readonly label: string; }
```

**Derived (`computed`)**: `selectedNode = level.find(n => n.id === selectedNodeId) ?? null`;
`isRootLevel = path.length === 0`.

**Locale-pending mapping (contract §2.1/§5 → VM):** a node returned with
`contentStatus: pending_locale`, `texto: null`, `selecionavel: false` maps to
`label: null`, `pendingInLocale: true`, `selecionavel: false` and is rendered **disabled
/ esmaecido**; the FE **never** substitutes the other locale's text (SPEC-07 §pending).

**UI events exposed:** `channelSelectionMoved(nodeId, channel)`, `nodeActivated(nodeId)`,
`backRequested()`, `localeSwitched(locale)`.

## 3. `PlaybackVM` (feature-tts) — owner `TtsPlaybackFacade`

```ts
interface PlaybackVM {
  readonly state: 'idle' | 'loading' | 'speaking' | 'fallback' | 'unavailable';
  readonly source: 'cached-audio' | 'web-speech' | 'text-only' | null;
  readonly nodeId: string | null;
  readonly locale: Locale;   // playback is always in the active locale (ADR-007/018)
}
```

State machine (ADR-007): `idle → loading → speaking → idle` (cached audio);
`loading → fallback` (audio absent/fails → Web Speech) `→ idle`;
`fallback → unavailable` (Web Speech absent → prominent text-only).

**UI events exposed:** `playbackReplayRequested(nodeId)`, `playbackStopRequested()`.

## 4. `SemanticAssistResultVM` (feature-speech-tree) — owner `SemanticAssistFacade`

```ts
interface SemanticAssistResultVM {
  readonly matches: readonly SemanticMatchVM[];  // conv-service kNN result, rendered as-is
  readonly retrieval: 'semantic' | 'static';     // 'static' == degraded (api-contract §2.2)
  readonly degraded: boolean;                     // ES down → OPS-003 silent fallback
  readonly status: 'idle' | 'loading' | 'ready' | 'empty';
}
interface SemanticMatchVM {
  readonly noId: string; readonly texto: string; readonly score: number;
  readonly tipo: NodeTipo; readonly hasAudio: boolean; readonly engineFeatureRef: string | null;
}
```

**Invariant:** the FE never scores/ranks/queries vectors; it renders `conv-service`'s
`matches` unchanged (ADR-015). `degraded: true` is **not** an error — it silently reverts
to manual tree navigation (no toast, no banner).

**UI events exposed:** `assistTriggered()`, `matchChosen(noId)`.

## 5. `ProposalConfirmVM` (Tela 09) — owner `ProposalConfirmFacade`

```ts
interface ProposalConfirmVM {
  readonly pending: readonly ProposalVM[];  // confirmadoCuidadora=true, confirmadoPaciente=false, active locale
  readonly locale: Locale;
  readonly status: 'idle' | 'loading' | 'empty' | 'saving' | 'saved' | 'error';
}
interface ProposalVM {
  readonly id: string;                 // node id
  readonly text: string;               // proposed phrase in the active locale
  readonly categoryLabel: string;      // parent category label
  readonly variants: readonly string[]; // short variants for "Ajustar" — NO free text
}
```

> **Contract gap (see backend-api-cross-link §Proposals):** the BE api-contract v1 does
> **not** yet expose list-pending / confirm / adjust / reject endpoints. This VM assumes a
> `conv-service` proposals resource to be added; the shape here is the FE requirement that
> the addition must satisfy (flagged for specs-validate and BE backlog).

**UI events exposed:** `proposalConfirmed(nodeId)`, `proposalAdjusted(nodeId, variant)`,
`proposalRejected(nodeId)`.

## 6. `ConvLocaleFacade` (module-scoped)

```ts
interface ConvLocaleVM {
  readonly active: Locale;
  readonly supported: readonly Locale[];  // localesSuportados from GET /tree
}
```

Switching `active` re-fetches the current level and clears playback and the light tree
cache (doc 05 §3). The locale is also sent as `Accept-Language` / body `locale` on
`conv-service` calls, within `supported` only.

## 7. Criterios de aceite (EARS)

- WHILE a speech-tree level is loading, THE SYSTEM SHALL expose `SpeechTreeVM.status = 'loading'` and SHALL render a loading state rather than a stale level.
- IF a level load returns no nodes, THEN THE SYSTEM SHALL expose `SpeechTreeVM.status = 'empty'` and SHALL render the empty-tree state with a caregiver-configuration hint.
- WHILE a speech-tree node's `contentStatus` is `pending_locale`, THE SYSTEM SHALL expose that node with `label = null`, `pendingInLocale = true`, and `selecionavel = false`, and SHALL render it disabled/esmaecido without substituting the other locale's text.
- THE SYSTEM SHALL hold at most one `selectedNodeId` per level, and both the gesture channel and the touch channel SHALL write that same single slot.
- WHEN a gesture or a touch moves the selection, THE SYSTEM SHALL update `activeChannel` for the indicator only and SHALL NOT branch any navigation or playback logic on the channel.
- WHILE cached audio is playing, THE SYSTEM SHALL expose `PlaybackVM.state = 'speaking'` and `source = 'cached-audio'`.
- WHEN a semantic-assist response reports `degraded = true`, THE SYSTEM SHALL expose `SemanticAssistResultVM.degraded = true` and SHALL fall back to manual navigation without surfacing an error.
- WHILE a Tela 09 confirmation write is in flight, THE SYSTEM SHALL expose `ProposalConfirmVM.status = 'saving'`.
- WHEN the patient switches the active locale, THE SYSTEM SHALL reload the current level and audio in the new locale and SHALL clear the tree cache for the previous locale.
