# 02 — Architecture Blueprint — EDGE (Frontend)

## 1. System boundary

EDGE-FE is a **client of the API gateway**. It talks only to `edge-service`
(routed via `neuroassist-ai-api-gateway`, discovered via `service-registry`)
through the shared HTTP access layer. It has **no direct contact with the edge
agent** — the agent is an out-of-browser program that reports to `edge-service`;
EDGE-FE observes its pairing and heartbeat effects **through the backend only**.
No legacy systems, no broker (messaging disabled).

## 2. External actors and systems

- **Browser (authenticated caregiver/conductor)** — session established by ACS;
  EDGE inherits the active `Tenant`/`Paciente` context.
- **`edge-service` (BE)** — owns `DispositivoEdge`: issues pairing codes, records
  heartbeats, exposes device status/version/last-sync. Synchronous REST.
- **The edge agent** (indirect) — claims the pairing code and posts heartbeats to
  `edge-service`; EDGE-FE never addresses it directly.
- **Consumers** — Shell topbar, Home, PAC, GEST, EVNT read EDGE's device
  read-model / online-state.

## 3. End-to-end user flows

### Golden path A — Pair a device (onboarding step 3)

```
[Conductor] Onboarding · Step 3 "Dispositivo edge"
     |
     v
 (no device paired) --> [Parear dispositivo]
     |
     v
 request pairing code  --POST /devices/pairing-codes-->  edge-service
     |                                                        |
     v                                                        v
 show CODE + instructions ("run the agent, enter this code")
     |
     v
 poll device status  --GET /devices/{id}? (or by code)-->  (pending…)
     |   [agent claims code + first heartbeat arrives]
     v
 status = online, versao = X, ultimaSincronizacao = now
     |
     v
 "Dispositivo pareado · online · versão X"  --> step complete
```

### Golden path B — Monitor connectivity (daily, ambient)

```
[Any screen] Shell topbar chip  <-- EDGE device read-model (poll every N s)
     |
     +-- heartbeat fresh (< threshold)        --> [ online ]  wifi
     +-- heartbeat stale (>= threshold, < off) --> [ stale/unknown ]  wifi + "?"
     +-- no heartbeat past offline window       --> [ offline ]  wifi_off (loud)
```

### Alternatives & edge cases

- **Code expires before claim** — code is short-lived; on expiry show "code
  expired, request a new one" (no silent hang).
- **Agent claims but never heartbeats** — remains `pairing`/`stale`; do **not**
  advance to "paired · online" without a first heartbeat (ADR-005).
- **Device goes offline mid-use** — chip degrades `online → stale → offline`;
  never silently stays green.
- **Re-pair / replace** — pairing a new device supersedes the previous bind
  (one active device per patient); the old device moves to `unpaired`.
- **Poll fails (gateway error)** — connectivity state becomes `unknown`
  (degraded), surfaced honestly, with retry; never assumed `online`.

## 4. Internal boundaries (lib structure — module-pattern / ADR-006)

```
apps/edge/                              # thin host app (buildable/serveable, e2e target)
+-- src/app/app.routes.ts               # loadChildren -> @neuroassist/edge

libs/edge/
+-- shell-entry/                        # entry as LIBRARY (importPath @neuroassist/edge, ADR-006)
|   +-- edge.module.ts                  # the ONLY NgModule (lazy entry)
|   +-- edge.contract.ts                # ModuleMenuMetadata + permissions + flags
|   +-- edge.bootstrap.ts               # init/destroy lifecycle
|   +-- edge.routes.ts                  # EDGE_ROUTES[]
|   +-- edge.permission-map.ts
|   +-- edge.feature-flags.ts
|   +-- edge.nav.config.ts
|   +-- edge.descriptor.ts              # EDGE_MODULE_DESCRIPTOR (consumed by Shell)
+-- access/                             # edge-specific guards (device-scope)
+-- data-access/                        # HTTP client + signal facades (DeviceFacade, PairingFacade)
+-- feature-pairing/                    # pairing flow (request code, poll, confirm)
+-- feature-device-detail/             # device detail / management (re-pair, unpair)
+-- integration/                        # EdgeShellIntegrationService + device read-model port
+-- navigation/                         # breadcrumbs state
+-- ui/ (optional)                      # edge-local presentational components (status chip, code card)
+-- styles/ (optional)                  # module CSS (Tailwind utilities + tokens)
```

**Shell-consumed read-model:** EDGE exposes a small **connectivity read-model**
(the active device's derived state as a readonly signal) via `libs/edge/integration`.
The Shell topbar and Home consume it through the module descriptor / a shared port
— they never import `scope:edge` internals directly (nx-boundaries.md).

## 5. Cross-cutting concerns

- **Auth / entitlement** — `authGuard` + an EDGE `deviceGuard` via
  `AccessDecisionService`; pairing/unpair is conductor-privileged, monitoring is
  read-only for caregivers.
- **Observability** — pairing outcomes and connectivity-state transitions emitted
  via `@hub/observability` (or the project equivalent); silence latency is a
  measurable signal (§7 of doc 01).
- **Design tokens** — status colors reuse the design-system tokens already used by
  the prototype topbar (`success` / `danger` / `warning` tones for online / offline
  / stale); no bespoke palette (design-system-tokens.md).
- **i18n** — human-facing strings pt-BR (project locale); technical docs en-US.
- **Honest-state cross-cut** — the derived connectivity state is the **single
  source** every consumer reads; no consumer re-derives "online" from a raw flag
  (ADR-005 local).
