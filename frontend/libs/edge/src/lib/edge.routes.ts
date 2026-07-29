import { Routes } from '@angular/router';
import { edgeConductorGuard } from './access/edge-conductor.guard';

// ---------------------------------------------------------------------------
// EDGE_ROUTES — SPEC-01 §2
// Thin host routes for dev/e2e and the connectivity chip target (/edge/device).
// showInNav:false — EDGE is not a primary nav item (SPEC-05 §2).
//
// /edge           → redirect to /edge/device
// /edge/device    → DeviceDetailPageComponent (authGuard; all tenant members)
// /edge/device/:id → DeviceDetailPageComponent with explicit deviceId
// /edge/pairing   → DevicePairingPageComponent (edgeConductorGuard; conductor only)
// ---------------------------------------------------------------------------

export const EDGE_ROUTES: Routes = [
  {
    path: '',
    redirectTo: 'device',
    pathMatch: 'full',
  },
  {
    path: 'device',
    // authGuard: TODO[FE-EDGE-001] wire @neuroassist/shared/access-decision authGuard
    loadComponent: () =>
      import('./feature-device-detail/device-detail-page.component').then(
        (m) => m.DeviceDetailPageComponent,
      ),
    title: 'Dispositivo',
  },
  {
    path: 'device/:deviceId',
    // authGuard: TODO[FE-EDGE-001] wire @neuroassist/shared/access-decision authGuard
    loadComponent: () =>
      import('./feature-device-detail/device-detail-page.component').then(
        (m) => m.DeviceDetailPageComponent,
      ),
    title: 'Dispositivo · Detalhes',
  },
  {
    path: 'pairing',
    canActivate: [edgeConductorGuard],
    loadComponent: () =>
      import('./feature-pairing/device-pairing-page.component').then(
        (m) => m.DevicePairingPageComponent,
      ),
    title: 'Parear dispositivo',
  },
];
