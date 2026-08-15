import { EDGE_ROUTES } from './edge.routes';
import { edgeBootstrap } from './edge.bootstrap';

// ---------------------------------------------------------------------------
// EDGE_MODULE_DESCRIPTOR — SPEC-06 §1
// Barrel export from @neuroassist/edge. The shell reads this to register EDGE
// without importing internals (nx-boundaries, ADR-006).
// Key composition slots:
//   connectivityReadModel — topbar chip (always-visible, every authenticated route)
//   pairingComponent      — onboarding step-3 host page
// ---------------------------------------------------------------------------

export const MODULE_KEY = 'edge' as const;

export const MODULE_MENU_METADATA = {
  key: MODULE_KEY,
  label: 'Dispositivo',
  icon: 'wifi',
  showInNav: false as const, // composed surface: topbar chip + onboarding; no nav entry
  order: null,
} as const;

export const MODULE_PERMISSION_MAP = {
  'edge.device.read': ['CUIDADORA_FAMILIAR', 'CONDUCTOR'],
  'edge.device.manage': ['CONDUCTOR'],
} as const;

export const MODULE_FEATURE_FLAGS = {
  pollingEnabled: { default: true },
  pollIntervalMs: { default: 15_000 },
  stalenessThresholdMs: { default: 45_000 },
  offlineWindowMs: { default: 90_000 },
  rePairEnabled: { default: true },
} as const;

export const EDGE_MODULE_DESCRIPTOR = {
  key: MODULE_KEY,
  entry: () => import('./edge.routes').then((m) => m.EDGE_ROUTES),
  routes: EDGE_ROUTES,
  permissionMap: MODULE_PERMISSION_MAP,
  featureFlags: MODULE_FEATURE_FLAGS,
  menu: MODULE_MENU_METADATA,
  bootstrap: edgeBootstrap,
  /** Composition slot A: the always-visible connectivity chip for the topbar/Home.
   * scope:web mounts this via NgComponentOutlet — NEVER import ConnectivityChipComponent
   * directly from scope:edge (nx-boundary: scope:web may only compose via this slot).
   * The chip reads its VM from EdgeShellIntegrationService.connectivityReadModel(). */
  connectivityReadModel: () =>
    import('./feature-connectivity/connectivity-chip.component').then(
      (m) => m.ConnectivityChipComponent,
    ),
  /** Composition slot B: onboarding step-3 host page (conductor pairing flow).
   * scope:web mounts this via NgComponentOutlet with [pacienteHint] input binding.
   * NEVER import DevicePairingPageComponent from scope:edge directly (nx-boundary). */
  pairingComponent: () =>
    import('./feature-pairing/device-pairing-page.component').then(
      (m) => m.DevicePairingPageComponent,
    ),
} as const;
