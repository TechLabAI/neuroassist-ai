# 05 — State and Data Flow — GEST (Frontend)

## 1. State topology

| State | Where | Kind |
|---|---|---|
| Live-test VM (`state`, `step`, `message`, `deviceOnline`) | `libs/gest/data-access` → `GestureTestFacade` | signal, module-local |
| `MediaStream` handle | `GestureTestFacade` (single owner) | imperative resource, not signal |
| Command draft under test | provided by host page (PAC command editor) | `@Input`/signal, external |
| Active tenant / session | `@neuroassist/shared/empresa-context`, `auth-session` | shared, global |
| Device online status | `@neuroassist/shared/*` (EDGE-sourced) read by gest guard | shared |

There is **no global GEST store** — all recognition/test state is local to the
feature's facade and dies with the component.

## 2. Data access pattern

```
EdgeDetectionClient (data-access)
   ├── openStream(commandDraft): Observable<EdgeRecognitionSignal>   // localhost WS/SSE (ADR-005)
   └── (fallback) mockStream(commandDraft)                           // degraded / offline

GestureTestFacade (data-access)
   - startTest(draft): acquires MediaStream (getUserMedia) + subscribes stream
   - maps EdgeRecognitionSignal -> GestureTestVM (signal.update)
   - stopTest()/onDestroy: unsubscribe + stop all MediaStream tracks

GestureLiveTestComponent (feature)
   - reads facade.state() (signal), binds <video>.srcObject, renders verdict
   - dispatches startTest/stopTest/retry
```

- Recognition arrives via **stream** (push), not polling — the edge agent emits
  as it classifies.
- Any REST side-effect (persist calibration, request sync to device) goes through
  `HttpClient` inside a service → facade emits a signal; no `BehaviorSubject`.

## 3. Cache strategy

- **None for recognition** — a live test is always a fresh camera + fresh stream;
  nothing about a test run is cached or replayed.
- Command/TipoGesto catalog is owned by PAC; GEST reads the draft passed in, it
  does not cache the catalog.

## 4. Optimistic updates

- Not applicable. The whole point is to show the *actual* engine verdict; there
  is nothing to optimistically pre-confirm. State only advances on a real (or
  explicitly-degraded) signal.

## 5. Error handling

| Failure | Detection | UI |
|---|---|---|
| Camera permission denied / no device | `getUserMedia` rejects | `camera-error` + recovery message + `Tentar de novo` |
| Edge agent unreachable / device offline | stream open fails / guard | degraded banner; mock verdict; never a false "recognized" |
| Recognition timeout | no signal within `tempoMaximoGestoMs` window | `not-recognized` + guidance |
| Backend sync error (calibration persist) | `ProblemDetail` | `@neuroassist/shared/error-model` → `AppError` → toast via `ui-feedback` |

Errors never leave the camera running (see invariant in doc 03 §4).

## 6. Breadcrumbs and navigation state

GEST is an embedded feature, not a primary nav destination. When reached through
its thin host `apps/gest` (dev/e2e), `libs/gest/navigation` contributes a single
`BreadcrumbItem[]` (`Calibração › Teste ao vivo`). When composed inside PAC's
calibração / onboarding at the `scope:web` page level, the **host page** owns the
breadcrumb; GEST contributes none, to avoid duplicate trails.
