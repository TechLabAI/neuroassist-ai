# 03 — Domain Framing (Frontend) — GEST

## 1. Ubiquitous language (mirror of BE / `_brief.md`)

GEST-FE does **not** redefine domain terms; it mirrors the BE data model
(`docs/architecture/shared/modelo-dados`) and `_brief.md`. Terms used on this
surface:

| Term | Meaning (as used by GEST-FE) |
|---|---|
| **TipoGesto (primitivo)** | A catalogued facial-movement primitive the engine can classify. Extensible catalog (LBC Amendment 2). Naming a type does not imply the engine recognizes it yet (see BE Framing §6.1). |
| **Comando** | A gesture (simple = 1 primitivo, or composite = 2 primitivos) mapped to a family-defined need; carries `sensibilidade`, `urgencia`, `tempoMinimoGestoMs`, `tempoMaximoGestoMs`. This is *what the live test validates.* |
| **Gesto reconhecido** | Edge classifier matched the active `Comando`. In a live test this drives UI state, not a production `Evento`. |
| **Sensibilidade** | `baixa | media | alta` — how readily the engine fires (never shown as raw threshold). |
| **DispositivoEdge** | The paired local agent running detection; must be online for a real live test. |
| **Evento** | Produced in production when a command is recognized; **not** created by the live test. |

## 2. View models (draft)

```ts
// what the live-test surface renders (recognition side only)
interface GestureTestVM {
  comandoId: string;              // command under test
  rotulo: string;                 // family label, e.g. "água"
  primitivoPrincipal: string;     // TipoGesto id
  primitivoSecundario: string | null;
  sensibilidade: 'baixa' | 'media' | 'alta';
  state: GestureTestState;        // see §4
  step: 'principal' | 'secundario' | null; // composite progress
  message: string;                // human-language feedback (pt-BR)
  deviceOnline: boolean;          // gates real vs degraded test
}

type GestureTestState =
  | 'idle' | 'testing' | 'recognized' | 'not-recognized' | 'camera-error';

// transient signal payload coming from the edge bridge (never a raw number on screen)
interface EdgeRecognitionSignal {
  recognized: boolean;
  matchedPrimitivo: string | null;
  step: 'principal' | 'secundario' | 'complete';
  // confidence kept internal — mapped to message, not rendered raw
}
```

## 3. UI events (user → state)

| Event | Trigger | Effect |
|---|---|---|
| `startTest` | "Testar agora" | request camera + open edge bridge → `testing` |
| `stopTest` | "Parar" / cancel / navigate away | stop stream + close bridge → `idle` |
| `recognitionArrived` | edge signal (or mock) | → `recognized` \| advance composite step |
| `timeoutElapsed` | no signal in window | → `not-recognized` |
| `cameraDenied` | `getUserMedia` rejects | → `camera-error` |
| `retry` | "Tentar de novo" | back to `startTest` |

## 4. State transitions (UI level)

```
idle --startTest--> testing
testing --recognitionArrived(complete)--> recognized
testing --recognitionArrived(step=principal, composite)--> testing (step=secundario)
testing --timeoutElapsed--> not-recognized
testing --cameraDenied--> camera-error
testing --stopTest--> idle
recognized | not-recognized | camera-error --retry/startTest--> testing
recognized | not-recognized | camera-error --edit/close--> idle
```

Invariant: leaving `testing` (any transition) **always** stops the `MediaStream`
and closes the edge bridge subscription.

## 5. Invariants (client-side)

- A live test requires a **paired, online** `DispositivoEdge`; otherwise the
  surface enters degraded mode (banner + simulated verdict) — it never claims a
  real recognition it could not have received.
- `rotulo` must be non-empty before a command is savable, but the **live test can
  run on an unsaved draft** (test-before-save is the point).
- Composite test requires **both** primitives to be recognized in sequence
  within their `tempoMinimo/MaximoGestoMs` window to report `recognized`.
- No raw confidence/threshold values are rendered — only mapped human-language
  messages (UX Principle 2.4).
- Camera permission is requested only on explicit user action, never on load.
