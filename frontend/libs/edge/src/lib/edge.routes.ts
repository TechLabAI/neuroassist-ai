import { Routes } from '@angular/router';
import { edgeConductorGuard } from './access/edge-conductor.guard';
import { edgeAuthGuard } from './access/edge-auth.guard';

// ---------------------------------------------------------------------------
// EDGE_ROUTES — SPEC-01 §2 (FE-EDGE-015)
// Thin host routes for dev/e2e and the connectivity chip target (/edge/device).
// showInNav:false — EDGE is not a primary nav item (SPEC-05 §2).
//
// /edge            → redirect to /edge/device
// /edge/device     → DeviceDetailPageComponent (edgeAuthGuard; all tenant members)
// /edge/device/:id → DeviceDetailPageComponent with explicit deviceId (edgeAuthGuard)
// /edge/pairing    → DevicePairingPageComponent (edgeConductorGuard; conductor only)
// ---------------------------------------------------------------------------

export const EDGE_ROUTES: Routes = [
  {
    path: '',
    redirectTo: 'device',
    pathMatch: 'full',
  },
  {
    path: 'device',
    canActivate: [edgeAuthGuard],
    loadComponent: () =>
      import('./feature-device-detail/device-detail-page.component').then(
        (m) => m.DeviceDetailPageComponent,
      ),
    title: 'Dispositivo',
    // FE-EDGE-016: breadcrumb label read by EdgeBreadcrumbService on NavigationEnd.
    data: { breadcrumb: 'Dispositivo' },
  },
  {
    path: 'device/:deviceId',
    canActivate: [edgeAuthGuard],
    loadComponent: () =>
      import('./feature-device-detail/device-detail-page.component').then(
        (m) => m.DeviceDetailPageComponent,
      ),
    title: 'Dispositivo · Detalhes',
    // FE-EDGE-016: same segment as /edge/device — service produces single-segment trail.
    data: { breadcrumb: 'Dispositivo' },
  },
  {
    path: 'pairing',
    canActivate: [edgeConductorGuard],
    loadComponent: () =>
      import('./feature-pairing/device-pairing-page.component').then(
        (m) => m.DevicePairingPageComponent,
      ),
    title: 'Parear dispositivo',
    // FE-EDGE-016: leaf label; service prepends Dispositivo to form [Dispositivo › Parear].
    data: { breadcrumb: 'Parear' },
  },
];
