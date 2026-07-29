# SPEC-02 — Component Hierarchy — EDGE (Frontend)

> Every component is `standalone: true`, `OnPush` (vision doc 01 §6). Smart components consume the
> `DeviceFacade` / `PairingFacade` (signals); presentational components take `@Input`s and emit `@Output`s
> only and declare no facade dependency.

## 1. `libs/edge/feature-pairing` — `feature-pairing`

Pairing flow for onboarding step 3 and the standalone pairing route. `DevicePairingPageComponent` is the
smart container; everything below it is presentational.

```
DevicePairingPageComponent                       [smart, standalone, OnPush]
│   reads signals:
│     pairing.state()        → PairingVM
│     pairing.phase()        → PairingPhase
│     pairing.countdown()    → number | null   (enrollment-token TTL seconds remaining)
│   @Input()  pacienteHint?: string             // wired by onboarding host page (scope:web)
│   @Output() paired = EventEmitter<PairedDeviceOutcome>
│   dispatches: pairing.register(label, pacienteHint) / pairing.pairToPatient(pacienteId) /
│               pairing.reset() / pairing.retry()
│   lifecycle: ngOnDestroy → pairing.stopPolling() (no leaked poll timer)
│
├── PairingCodeCardComponent                     [presentational]
│     @Input() enrollmentToken: string | null    // the one-time claim code shown to the operator
│     @Input() expiresInSeconds: number | null    // TTL countdown (BE enrollmentExpiresAt, api-contract §2.1)
│     @Input() phase: PairingPhase
│     @Output() copy = EventEmitter<void>         // copy code to clipboard
│     (renders code + "rode o agente e informe este código"; never renders a credential/secret)
│
├── PairingProgressComponent                     [presentational]
│     @Input() phase: PairingPhase                // registering → awaiting-claim → awaiting-first-heartbeat → paired
│     (ordered step indicator; mirrors prototype onboarding checklist, made honest)
│
└── PairingResultComponent                       [presentational]
      @Input() phase: PairingPhase
      @Input() versao: string | null              // agent version at first heartbeat
      @Input() derivedState: DerivedState          // online | stale | offline | unknown
      @Output() retry = EventEmitter<void>
      ("Dispositivo pareado · online · versão X" / "código expirado, gerar novo" / "tentar de novo")
```

## 2. `libs/edge/feature-device-detail` — `feature-device-detail`

Device detail / management view. `DeviceDetailPageComponent` is the smart container.

```
DeviceDetailPageComponent                        [smart, standalone, OnPush]
│   reads signals:
│     device.detail()        → DeviceDetailVM
│     device.derivedState()  → DerivedState
│     device.lastSyncRelative() → string
│   @Input() deviceId?: string                    // route param; absent ⇒ resolve active patient's device
│   dispatches: device.load(deviceId) / device.resolveForActivePatient() /
│               device.unpair(reason) / device.retire()  (conductor only)
│   lifecycle: ngOnInit → device.startPolling(); ngOnDestroy → device.stopPolling()
│
├── ConnectivityStatusCardComponent              [presentational]
│     @Input() derivedState: DerivedState          // online | stale | offline | unknown | not-paired
│     @Input() lastSyncRelative: string
│     @Input() versao: string | null
│     (large honest status; color token + text label + icon — never color-only, SPEC design-tokens)
│
├── DeviceMetaListComponent                       [presentational]
│     @Input() detail: DeviceDetailVM              // pairingState, ultimaSincronizacao, versao, profileDrift, threshold
│     (last-sync time, agent version, running/current profile version, drift, staleness window)
│
└── DeviceActionsComponent                        [presentational]
      @Input()  canManage: boolean                // conductor entitlement (SPEC-05)
      @Input()  rePairEnabled: boolean            // feature flag
      @Output() rePair = EventEmitter<void>
      @Output() unpair = EventEmitter<void>
      @Output() retire = EventEmitter<void>
      ("Reparear" / "Desparear" / "Aposentar" — hidden entirely when !canManage)
```

## 3. `libs/edge/ui` — `ui`

Reusable presentational primitives, no facade dependency:

```
ConnectivityChipComponent                        [presentational]
   @Input() derivedState: DerivedState             // the always-visible topbar chip
   @Input() tone: 'ok' | 'warn' | 'er' | 'muted'
   @Input() label: string                          // pt-BR ("Dispositivo online" / "…offline" / "…sem sinal")
   @Output() open = EventEmitter<void>             // click → navigate to /edge/device
   (wifi / wifi_off / wifi-question icon; reuses prototype topbar chip semantics)

RelativeTimeComponent                            [presentational]
   @Input() iso: string | null                     // renders "há 2 min" from an ISO timestamp
```

`ConnectivityChipComponent` is the presentational unit the **Shell topbar** renders, driven by EDGE's
connectivity read-model (SPEC-06) — the Shell binds `@Input`s from the read-model signal and never imports
`scope:edge` internals.

## 4. `libs/edge/shell-entry` — entry (`type:app`)

No visual components. Exports the barrel (`edge.module`, `edge.routes`, `edge.contract`, `edge.bootstrap`,
`edge.descriptor`, `edge.nav.config`, `edge.permission-map`, `edge.feature-flags`) and re-exports
`DevicePairingPageComponent` + the connectivity read-model provider so the `scope:web` shell can compose
them (SPEC-06).

## 5. Composition wiring (scope:web)

The onboarding step-3 host page and the Shell topbar — living in `scope:web`, the only scope permitted to
import `scope:edge` — render:

```
<onboarding-step-dispositivo-page>     [scope:web host page]
   <edge-device-pairing                ← DevicePairingPageComponent (from @neuroassist/edge)
       [pacienteHint]="onboarding.pacienteId()"
       (paired)="onboarding.onDevicePaired($event)" />

<app-topbar>                            [scope:web shell]
   <edge-connectivity-chip             ← ConnectivityChipComponent (via read-model port)
       [derivedState]="shell.edgeConnectivity().derivedState"
       [tone]="shell.edgeConnectivity().tone"
       (open)="router.navigate(['/edge/device'])" />
```

PAC/ACS/onboarding never import `scope:edge`; the shell page/topbar is the composition point (nx-boundaries,
doc 02 §4). This is the production decomposition of the prototype's `topbar.component.ts` chip (which today
trusts the raw `status` flag) and `onboarding.component.ts` step 3 (which today fakes pairing with a
`setTimeout`) — both made honest per ADR-005.

## 6. Signals read by each smart component (explicit)

| Component | Signals read | Writes (via facade dispatch) |
|---|---|---|
| `DevicePairingPageComponent` | `state`, `phase`, `countdown` | `register`, `pairToPatient`, `reset`, `retry`, `stopPolling` |
| `DeviceDetailPageComponent` | `detail`, `derivedState`, `lastSyncRelative` | `load`, `resolveForActivePatient`, `unpair`, `retire`, `startPolling`, `stopPolling` |

No presentational component reads signals directly — all state flows down as `@Input`.
