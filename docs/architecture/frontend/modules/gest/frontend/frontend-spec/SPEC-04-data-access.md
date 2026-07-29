# SPEC-04 — Data Access — GEST (Frontend)

> `libs/gest/data-access` owns two collaborators: the **edge bridge client**
> (`EdgeDetectionClient`, localhost WS/SSE, FE↔edge, not served by gest-service)
> and the **facade** (`GestureTestFacade`, signals). Any REST side-effect goes
> through a thin service over `@neuroassist/shared/access-http-base`.
>
> `data-access` may depend **only** on `shared/contracts`, `access-http-base`,
> `empresa-context`, `error-model` (nx-boundaries, doc 04 §4).

## 1. Wire contract binding (frozen)

The stream payload is bound **exactly** to the frozen BE `EdgeRecognitionSignal`
(`event-contracts.md` §3.2). This type lives in `@neuroassist/shared/contracts`:

```ts
// @neuroassist/shared/contracts — mirrors event-contracts.md §3.2 (contract v1.0.0)
export interface EdgeRecognitionSignal {
  readonly contractVersion: string;                    // e.g. "1.0.0"
  readonly signalKind: 'recognized' | 'not_recognized' | 'preview_frame_ack' | 'degraded';
  readonly commandDraftRef: string;                    // the in-progress Comando under test
  readonly primitives: ReadonlyArray<{
    readonly engineFeatureRef: string;
    readonly confidence: number;                        // kept internal; never rendered raw
  }>;
  readonly timingWindow: { readonly startMs: number; readonly endMs: number; readonly durationMs: number };
  readonly degraded: boolean;                           // true ⇒ mocked; never a fake "recognized"
  readonly emittedAt: string;                           // ISO-8601 UTC
}
```

`data-access` binds this shape verbatim. It does **not** invent the vision-draft
shape (`recognized`/`matchedPrimitivo`/`step`) — that draft is superseded.

## 2. `EdgeDetectionClient` (localhost bridge)

```ts
class EdgeDetectionClient {
  // FE ADR-005: localhost WS/SSE to the same-device edge agent
  openStream(draft: ComandoDraft): Observable<EdgeRecognitionSignal>;
  // degraded fallback (agent down / offline / cross-device / version mismatch)
  mockStream(draft: ComandoDraft): Observable<EdgeRecognitionSignal>; // always degraded:true
  close(): void;
}
```

- Negotiates `contractVersion` on channel open. IF the agent advertises a
  `contractVersion` incompatible with the FE's, the client transparently switches
  to `mockStream` (degraded, reason `version-mismatch`).
- Transport auth is a **JWT-shaped device token** provisionally (bridge
  handshake/auth still OPEN — BE `event-contracts.md` §3.3; frozen jointly with
  EDGE in backlog). The client isolates this behind a `bridgeToken` provider so
  the final mTLS-vs-OAuth2 decision does not ripple into the facade.

## 3. `GestureTestFacade` (signals, single stream owner)

```ts
class GestureTestFacade {
  private readonly _state = signal<GestureTestVM>(initialVm());
  readonly state      = this._state.asReadonly();
  readonly step       = computed(() => this._state().step);
  readonly deviceOnline = computed(() => this._state().deviceOnline);
  readonly degraded   = computed(() => this._state().degraded);

  startTest(draft: ComandoDraft): void;   // acquire getUserMedia + subscribe (real or mock) stream
  stopTest(): void;                        // unsubscribe + stop ALL MediaStream tracks + client.close()
  retry(): void;                           // stopTest() then startTest(lastDraft)
}
```

- **Single owner of `MediaStream`** — the facade acquires via
  `navigator.mediaDevices.getUserMedia({ video: true })` and is the only holder,
  so teardown is centralized and testable (doc 04 §6, doc 05 §1).
- Bridges the RxJS `Observable<EdgeRecognitionSignal>` into `_state` via
  `signal.update`; components read the signal only — no `BehaviorSubject` in
  components, no NgRx.
- **Signal → VM mapping:**

| `EdgeRecognitionSignal` | VM effect |
|---|---|
| `signalKind='recognized'`, simple | `state='recognized'`, message="Gesto reconhecido com sucesso!" |
| `signalKind='recognized'`, composite, step principal | keep `state='testing'`, `step='secundario'` |
| `signalKind='recognized'`, composite, both done | `state='recognized'` |
| `signalKind='not_recognized'` | `state='not-recognized'`, message="Não reconhecido — repita o gesto ou aumente a sensibilidade" |
| `signalKind='preview_frame_ack'` | keep `state='testing'` (liveness heartbeat) |
| `signalKind='degraded'` / `degraded=true` | `degraded=true`, honest banner; never `recognized` from a real claim |
| no signal within `tempoMaximoGestoMs` | `state='not-recognized'` (timeout) |

## 4. REST side-effect service — `GestEligibilityService` (optional consumed resource)

The live test itself is stream-driven (no REST). GEST-FE consumes exactly one BE
REST resource — the **eligibility guard** — when a host page needs to confirm that
an emergency command's primitives are all `supported` before enabling save.
(Catalog/profile endpoints are owned/consumed by PAC and EDGE, not by GEST-FE.)

```ts
class GestEligibilityService {
  check(req: { urgencia: Urgencia; primitiveIds: string[] }): Observable<EligibilityResult>;
}
```

## 5. Master resource table

| Resource | Endpoint | Service | Facade | Operations | Cache? |
|---|---|---|---|---|---|
| `EdgeRecognitionSignal` (stream) | localhost WS/SSE (FE↔edge, ADR-005) | `EdgeDetectionClient` | `GestureTestFacade` | `openStream`, `mockStream`, `close` | **No** — every test is a fresh camera + fresh stream (doc 05 §3) |
| `Eligibility` | `POST /api/v1/gest/eligibility:check` | `GestEligibilityService` | *(host page)* | `check` | No (read-only, idempotent) |
| `DetectionContract` (descriptor, version negotiation) | `GET /api/v1/gest/detection-contract` | `EdgeDetectionClient` (bootstrap) | — | `getContract` | Yes — memoized per session (immutable descriptor) |

DTOs consumed from `@neuroassist/shared/contracts`: `EdgeRecognitionSignal`,
`EligibilityResult`, `ComandoDraft`. Error mapping via
`@neuroassist/shared/error-model` (SPEC-07).

## Critérios de aceite (EARS)

- WHEN `startTest` is dispatched, THE SYSTEM SHALL acquire a `MediaStream` via `getUserMedia` and subscribe to the edge recognition stream for the given `commandDraftRef`.
- WHEN `stopTest` is dispatched, THE SYSTEM SHALL unsubscribe from the stream, stop every `MediaStream` track, and close the edge bridge client.
- IF the edge bridge advertises a `contractVersion` incompatible with the frontend, THEN THE SYSTEM SHALL switch to the mock stream in degraded mode with reason `version-mismatch`.
- THE SYSTEM SHALL keep the per-primitive `confidence` internal and SHALL NOT expose it through the view-model.
- WHEN no recognition signal arrives within `tempoMaximoGestoMs`, THE SYSTEM SHALL set `state` to `not-recognized`.
- THE SYSTEM SHALL NOT cache or replay any recognition stream between test runs.
