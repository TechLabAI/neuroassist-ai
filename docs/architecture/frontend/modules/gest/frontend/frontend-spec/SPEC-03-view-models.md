# SPEC-03 — View Models — GEST (Frontend)

> View-models are `record`-style readonly TypeScript shapes exposed as Signals by
> `GestureTestFacade`. Components read `.asReadonly()` signals; they never mutate.
>
> **Contract binding (authoritative):** the wire type consumed from the edge
> bridge is the **frozen** `EdgeRecognitionSignal` from BE `event-contracts.md`
> §3.2 (`contractVersion`, `signalKind`, `commandDraftRef`, `primitives[]`,
> `timingWindow`, `degraded`, `emittedAt`). The draft shape sketched in FE vision
> doc 03 §2 (`recognized`, `matchedPrimitivo`, `step`) is **superseded** by the
> frozen contract and MUST NOT be used in `data-access`. See SPEC-04 for the map.

## 1. Domain input — `ComandoDraft` (from host page)

The command under test, provided by the PAC command editor via `@Input` (never
fetched by GEST; GEST does not own the catalog — doc 05 §3).

```ts
export type Sensibilidade = 'baixa' | 'media' | 'alta';
export type Urgencia = 'emergencia' | 'rotina';

export interface ComandoDraft {
  readonly commandDraftRef: string;       // opaque handle; matches EdgeRecognitionSignal.commandDraftRef
  readonly rotulo: string;                 // family label, e.g. "água"
  readonly primitivoPrincipal: string;     // TipoGesto id
  readonly primitivoSecundario: string | null;
  readonly sensibilidade: Sensibilidade;
  readonly urgencia: Urgencia;
  readonly tempoMinimoGestoMs: number;
  readonly tempoMaximoGestoMs: number;
  readonly composite: boolean;             // derived: primitivoSecundario !== null
}
```

## 2. Screen VM — `GestureTestVM`

```ts
export type GestureTestState =
  | 'idle' | 'testing' | 'recognized' | 'not-recognized' | 'camera-error';

export type GestureTestStep = 'principal' | 'secundario' | null;

export type DegradedReason =
  | 'device-offline' | 'bridge-unreachable' | 'version-mismatch' | null;

export interface GestureTestVM {
  readonly commandDraftRef: string;
  readonly rotulo: string;
  readonly state: GestureTestState;
  readonly step: GestureTestStep;          // composite progress; null when simple
  readonly composite: boolean;
  readonly message: string;                // pt-BR, human-language ONLY (no raw confidence)
  readonly deviceOnline: boolean;          // gates real vs degraded test
  readonly degraded: boolean;              // true ⇒ mocked verdict, honest banner
  readonly degradedReason: DegradedReason;
  readonly contractVersion: string | null; // negotiated with the edge bridge
}
```

## 3. Outcome emitted to host — `GestureTestOutcome`

```ts
export interface GestureTestOutcome {
  readonly commandDraftRef: string;
  readonly recognized: boolean;            // final verdict for THIS run
  readonly degraded: boolean;              // true ⇒ verdict was NOT a real recognition
  readonly composite: boolean;
  readonly completedStep: GestureTestStep; // last step reached
}
```

`degraded: true` in the outcome guarantees the host (PAC) can distinguish an
honest simulated pass from a real edge recognition — a false "recognized" is
never emitted (ADR-005 invariant).

## 4. VM state invariants

| Invariant | Rule |
|---|---|
| Camera off outside `testing` | `state !== 'testing'` ⇒ no active `MediaStream` track (owner: facade, doc 03 §4) |
| No raw numbers | `message` is always mapped human-language pt-BR; `confidence` never surfaced (doc 03 §5, UX Principle 2.4) |
| Honest degraded | `degraded === true` ⇒ `state` may reach `recognized` **only** when it is an explicitly simulated verdict AND `GestureTestOutcome.degraded === true` |
| Composite gating | `composite === true` ⇒ `state = 'recognized'` requires both `principal` and `secundario` recognized within `tempoMinimo/MaximoGestoMs` |
| Empty label allowed for test | live test may run on an unsaved draft; `rotulo` empty does not block the test (doc 03 §5) |

## 5. UI events the VM exposes (component → facade)

| Event | Facade method | State effect |
|---|---|---|
| `startTest` | `startTest(draft)` | `idle → testing` (acquire camera + open bridge) |
| `stopTest` | `stopTest()` | `* → idle` (stop stream + close bridge) |
| `retry` | `retry()` | `recognized \| not-recognized \| camera-error → testing` |
| *(internal)* signal arrived | facade maps `EdgeRecognitionSignal` | advances step or sets terminal state |

## Critérios de aceite (EARS)

- WHILE `state` is not `testing`, THE SYSTEM SHALL ensure no `MediaStream` track is active.
- THE SYSTEM SHALL render only human-language pt-BR messages and SHALL NOT display raw confidence or threshold numbers on screen.
- WHEN the edge bridge emits `signalKind = "recognized"` for a simple command, THE SYSTEM SHALL set `state` to `recognized` and emit a `GestureTestOutcome` with `recognized: true`.
- WHILE `composite` is true, THE SYSTEM SHALL require both the `principal` and the `secundario` primitive to be recognized before setting `state` to `recognized`.
- IF the surface is in degraded mode, THEN THE SYSTEM SHALL set `degraded: true` on any emitted `GestureTestOutcome` and SHALL NOT emit a real `recognized` verdict.
- WHEN a live test runs on a draft whose `rotulo` is empty, THE SYSTEM SHALL allow the test to proceed.
