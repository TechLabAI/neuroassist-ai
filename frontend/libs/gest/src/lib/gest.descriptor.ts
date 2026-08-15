import { GestureLiveTestComponent } from './feature-live-test/gesture-live-test.component';
import { GEST_ROUTES } from './gest.routes';
import { gestBootstrap } from './gest.bootstrap';

// ---------------------------------------------------------------------------
// GEST_MODULE_DESCRIPTOR — SPEC-06 §1
// Barrel export from @neuroassist/gest. The shell reads this to register GEST
// without importing internals. Key field for composition: liveTestComponent.
// ---------------------------------------------------------------------------

export const MODULE_MENU_METADATA = {
  showInNav: false as const, // GEST never appears in the sidebar nav
  label: 'Teste de Gesto',
  icon: 'gesture',
} as const;

export const MODULE_PERMISSION_MAP = {
  view: 'gest:live-test:execute',
} as const;

export const MODULE_FEATURE_FLAGS = {
  edgeLiveBridge: 'on' as 'on' | 'off', // off => forced honest degraded/mock
} as const;

export const GEST_MODULE_DESCRIPTOR = {
  key: 'gest',
  entry: () =>
    import('./feature-live-test/gesture-live-test.component').then((m) => m.GestureLiveTestComponent),
  routes: GEST_ROUTES,
  permissionMap: MODULE_PERMISSION_MAP,
  featureFlags: MODULE_FEATURE_FLAGS,
  menu: MODULE_MENU_METADATA,
  bootstrap: gestBootstrap,
  /** The only sanctioned way for scope:web host pages to obtain this component.
   * scope:pac / scope:acs must use GestShellIntegrationService.resolveLiveTestSlot()
   * — they must NEVER import this field directly (GEST-INT-002 nx-boundary). */
  liveTestComponent: () =>
    import('./feature-live-test/gesture-live-test.component').then((m) => m.GestureLiveTestComponent),
} as const;
