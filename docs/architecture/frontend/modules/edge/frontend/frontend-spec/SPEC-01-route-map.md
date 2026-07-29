# SPEC-01 — Route Map — EDGE (Frontend)

> Module: **EDGE — Integração de Dispositivo Edge** · npm scope: `@neuroassist` · Entry lib: `@neuroassist/edge`
> Surface type: **device management + composed pairing/connectivity surfaces**. Composed at `scope:web`
> (topbar chip on every authenticated route; onboarding step 3 pairing host page — vision doc 02 §4, doc 04 §5).
> BE contract: `backend/services/edge-service/contracts/api-contract.md` (base path `/api/v1/edge`, routed via GWY).

## 1. Routing posture

EDGE is **not** a primary top-level navigation destination. It exposes two kinds of surface:

1. **Always-composed (production):** the **connectivity chip** rendered by the Shell topbar on every
   authenticated route (Wireframe §3.2.B — connectivity is never hidden). The chip reads EDGE's
   connectivity read-model (SPEC-06); it is **not** a route. Clicking it navigates to the device-detail route.
2. **Composed pairing (onboarding step 3):** the `DevicePairingPageComponent` is hosted by the onboarding
   flow at `scope:web` (EDGE owns the flow, the Shell composes it — the reverse import never happens).
3. **Own routes (thin host `apps/edge`, dev/e2e + chip target):** device-detail and a standalone pairing
   route so `nx serve/build edge` and `apps/edge-e2e` (Playwright) have a web-server base, and so the chip
   has a navigable target. These routes are **not** wired into the shell nav (`showInNav: false`, SPEC-05).

## 2. Master route table

| Path | Component | Feature Lib | Guards | Title | Breadcrumb |
|---|---|---|---|---|---|
| `/edge` | *(redirect → `/edge/device`)* | `libs/edge/shell-entry` | `authGuard` | — | — |
| `/edge/device` | `DeviceDetailPageComponent` | `libs/edge/feature-device-detail` | `authGuard` | "Dispositivo" | `Dispositivo` |
| `/edge/device/:deviceId` | `DeviceDetailPageComponent` | `libs/edge/feature-device-detail` | `authGuard` | "Dispositivo" | `Dispositivo › Detalhes` |
| `/edge/pairing` | `DevicePairingPageComponent` | `libs/edge/feature-pairing` | `authGuard`, `edgeConductorGuard` | "Parear dispositivo" | `Dispositivo › Parear` |
| *(composed)* — hosted inside onboarding step 3 `/onboarding/dispositivo` | `DevicePairingPageComponent` (via host slot) | consumed by `scope:web` page | host guards + `edgeConductorGuard` (composed) | *(none — host owns)* | *(none — host owns)* |
| *(composed)* — topbar connectivity chip (every authenticated route) | `ConnectivityChipComponent` (via read-model) | consumed by `scope:web` shell | *(inherits route guards)* | *(n/a — not a route)* | *(n/a)* |

Notes:
- The composed rows are **not** EDGE-owned routes; they document where EDGE surfaces appear. The onboarding
  path is authoritative in the onboarding/Shell route map.
- `apps/edge` host route: `loadChildren: () => import('@neuroassist/edge').then(m => m.EdgeModule)` behind `EDGE_ROUTES`.
- Sub-routes use `loadComponent` (lazy): `/edge/pairing`, `/edge/device` (doc 04 §5).

## 3. Guards

| Guard | Source | Responsibility | On failure |
|---|---|---|---|
| `authGuard` | `@neuroassist/shared/access-decision` | authenticated + tenant-scoped session (active `empresa`/`Paciente`) | redirect to login |
| `edgeConductorGuard` | `libs/edge/access` | requires the **conductor** entitlement for register/pair/unpair/retire (maps to BE `edge.manage`, api-contract §6.2) | redirect to `/edge/device` (read-only) with a "sem permissão" notice; never a blank page |

- **Monitoring is read-only** for caregivers: `/edge/device` needs only `authGuard` (BE `edge.read`, any tenant member).
- **Pairing/management is conductor-privileged:** `/edge/pairing` and the re-pair/unpair actions on
  `/edge/device` require `edgeConductorGuard` (BE `edge.manage`). The action buttons are additionally hidden
  by RBAC (SPEC-05) so a caregiver never sees an affordance she cannot use.

## 4. Deep-linking / query params

- `/edge/device/:deviceId` accepts an explicit device id (chip target / operator link). When absent
  (`/edge/device`), the page resolves the active patient's device via `GET /api/v1/edge/patients/{pacienteId}/device`
  (SPEC-04). No `tenantId` ever appears in a URL — it is derived from the session token (BE api-contract §1.1).
- `/edge/pairing?pacienteHint=<id>` may pre-fill the pairing target from the onboarding context; the value is
  advisory only (mirrors BE `pacienteHint`, api-contract §2.1) and is never treated as an authenticated identity.

## Critérios de aceite (EARS)

- WHEN an authenticated caregiver opens `/edge/device` without a device id, THE SYSTEM SHALL resolve the active patient's paired device before rendering the detail surface.
- THE SYSTEM SHALL omit EDGE from the shell navigation menu (`showInNav: false`) and SHALL surface connectivity only through the always-visible topbar chip.
- IF the session is not authenticated when any `/edge` route is entered, THEN THE SYSTEM SHALL redirect to the login route and SHALL NOT mount the device or pairing component.
- IF a caregiver without the conductor entitlement navigates to `/edge/pairing`, THEN THE SYSTEM SHALL redirect to the read-only device detail route and SHALL NOT mount the pairing surface.
- WHILE the pairing feature is composed inside the onboarding step-3 host page, THE SYSTEM SHALL contribute no breadcrumb, leaving the trail owned by the host page.
- WHEN the caregiver activates the topbar connectivity chip, THE SYSTEM SHALL navigate to the device detail route for the active device.
