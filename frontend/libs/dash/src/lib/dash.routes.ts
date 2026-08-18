import { Routes } from '@angular/router';
import { authGuard, dashGuard } from './access/dash.guard';

// =============================================================================
// DASH_ROUTES — SPEC-01 (route map)
// Routes loaded under path: '' of AppShellComponent (shell_route: / in generator-config.yml).
// DASH is the DEFAULT landing: shell children path:'' → loadChildren DASH_ROUTES.
// /entrada (chromeless) is registered as a TOP-LEVEL route in app.routes.ts (outside shell).
//
// Routes:
//   /              → HomeStatusComponent   (inside shell)
//   /notificacoes  → NotificationCenterComponent (inside shell)
//
// Guards: Wave-0 bypass active until auth layer promoted (INT-FE-DASH-002/003).
// =============================================================================

export const DASH_ROUTES: Routes = [
  {
    path: '',
    canActivate: [authGuard, dashGuard],
    loadComponent: () =>
      import('./feature-home/home-status.component').then(
        (m) => m.HomeStatusComponent,
      ),
    title: 'Status',
  },
  {
    path: 'notificacoes',
    canActivate: [authGuard, dashGuard],
    loadComponent: () =>
      import('./feature-notifications/notification-center.component').then(
        (m) => m.NotificationCenterComponent,
      ),
    title: 'Notificações',
  },
];
