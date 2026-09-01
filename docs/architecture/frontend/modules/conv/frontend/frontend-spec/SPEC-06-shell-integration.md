# SPEC-06 — Shell Integration — CONV (Frontend)

> How CONV mounts into the **restricted patient shell**, how the GEST engine is bridged in
> at the `scope:web` composition root without a `scope:conv → scope:gest` import, and what
> `init`/`destroy` do. CONV is **not** wired into the caregiver shell descriptor.

## 1. `tool-descriptors.ts` (fallback registration)

CONV is registered into the **patient-shell descriptor list**, not the caregiver shell:

```ts
// apps/neuroassist-ai-web/src/app/patient/patient-tool-descriptors.ts
export const PATIENT_TOOL_DESCRIPTORS: ToolDescriptor[] = [
  {
    key: 'conv',
    load: () => import('@neuroassist/conv').then(m => m.CONV_MODULE_DESCRIPTOR),
    surface: 'patient-only',
  },
];
```

The caregiver `tool-descriptors.ts` (admin shell) **does not** list CONV — structural
isolation (SPEC-01/05). The patient shell is the only surface that resolves it.

## 2. `ConvShellIntegrationService` (`libs/conv/integration`)

Owns cross-feature coordination that does not belong to a single facade:

- **Navigation ↔ playback orchestration:** on a `frase` activation, sequence
  `NodeSelectionPort.select(...)` → `TtsPlaybackFacade.speak(...)`; on a `categoria`
  activation, `SpeechTreeFacade.listLevel(...)` (no playback).
- **Gesture input bridge:** consumes an **injected** `InputChannelPort` abstraction
  (provided at the composition root, §3) and forwards recognized gestures through
  `BindingPort.resolve(...)` → `SpeechTreeFacade.selectNode/activateNode(...)`. This keeps
  `libs/conv/*` free of any `scope:gest` import (ADR-005 / nx-boundaries).
- **Locale coordination:** on `localeSwitched`, invalidate the tree cache and stop playback
  (doc 05 §3), then reload the current level.

```ts
export const INPUT_CHANNEL_PORT = new InjectionToken<InputChannelPort>('conv.input-channel');
export interface InputChannelPort {
  readonly recognizedGesture: Signal<{ engineFeatureRef: string } | null>;
  start(): Promise<void>;   // acquires camera + Wake Lock (delegated to GEST engine impl)
  stop(): void;             // releases camera track(s) — zero-camera-leak invariant
  readonly available: Signal<boolean>;
}
```

At the `scope:web` composition root (`apps/neuroassist-ai-web`), `INPUT_CHANNEL_PORT` is
bound to a GEST-engine-backed adapter; in Estágio 0 / e2e it is bound to a mock that emits
scripted gestures. `libs/conv` depends only on the token, never on GEST.

## 3. `conv.bootstrap.ts` — init / destroy lifecycle

```ts
export const CONV_BOOTSTRAP: ToolBootstrap = {
  init: async (ctx) => {
    // 1. verify active SessaoPaciente (shared/access) — else abort mount
    // 2. seed ConvLocaleFacade from JWT `locale` ∩ localesSuportados (GET /tree rootOnly)
    // 3. if gestureChannelEnabled: INPUT_CHANNEL_PORT.start() (camera + Wake Lock)
    // 4. prime the root tree level for first paint
  },
  destroy: () => {
    // 1. INPUT_CHANNEL_PORT.stop()  → release ALL camera tracks (zero-camera-leak)
    // 2. TtsPlaybackFacade.stop()   → stop audio / Web Speech
    // 3. revoke session-cached audio blob URLs; clear the tree cache
  },
};
```

`destroy` runs on patient-shell teardown and on route-leave from `/paciente/arvore`
(SPEC-01 §4). A `401 session-revoked` from any call triggers `destroy` and exits the shell.

## 4. Patient-shell placement (open scaffold decision, carried from SPEC-01 §1)

- **Chosen for specs:** a guarded top-level `/paciente` root inside `apps/neuroassist-ai-web`, hosting `libs/conv/patient-shell` (topbar only). Single deployable app, sidebar-less patient root, no caregiver route reachable.
- **Deferred to scaffold/M6:** whether to split into a separate `apps/patient-web` for stronger deployment isolation. The route/descriptor contract is identical; only the host changes. Flagged for `/fe-architect-scaffold` and specs-validate.

## 5. Criterios de aceite (EARS)

- THE SYSTEM SHALL register CONV only into the patient-shell descriptor list and SHALL NOT register it into the caregiver admin shell descriptors.
- WHEN the patient shell mounts CONV, THE SYSTEM SHALL verify an active `SessaoPaciente` before rendering any tree content.
- WHERE the gesture channel is enabled, THE SYSTEM SHALL start the injected input-channel port during init and SHALL consume recognized gestures only through that injected port.
- THE SYSTEM SHALL consume the GEST engine only through the injected `INPUT_CHANNEL_PORT` and SHALL NOT import any `scope:gest` library from a `scope:conv` library.
- WHEN CONV is destroyed or its route is left, THE SYSTEM SHALL stop the input-channel port, stop playback, and release every camera track and audio blob URL.
- IF a `conv-service` call reports the session revoked, THEN THE SYSTEM SHALL run the CONV destroy lifecycle and exit the patient shell.
