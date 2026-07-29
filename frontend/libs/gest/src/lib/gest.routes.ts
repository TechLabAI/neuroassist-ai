import { Routes } from '@angular/router';
import { gestDeviceOnlineGuard } from './access/gest-device-online.guard';

// ---------------------------------------------------------------------------
// GEST_ROUTES — thin host only (dev/e2e), NOT a shell nav item (showInNav:false)
// The real surface is GestureLiveTestComponent embedded in PAC and onboarding
// via GestShellIntegrationService.resolveLiveTestSlot().
//
// Route: /gest → GestureTestHostComponent (dev harness for e2e and standalone dev)
// ---------------------------------------------------------------------------

export const GEST_ROUTES: Routes = [
  {
    path: '',
    canActivate: [gestDeviceOnlineGuard],
    loadComponent: () =>
      import('./feature-live-test/gest-dev-host.component').then(
        (m) => m.GestDevHostComponent,
      ),
  },
];
