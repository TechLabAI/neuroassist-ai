import { Routes } from '@angular/router';
import { AppShellComponent } from './shell/app-shell.component';

export const appRoutes: Routes = [
  {
    path: '',
    component: AppShellComponent,
    children: [
      { path: '', redirectTo: 'home', pathMatch: 'full' },

      // GEST — thin host route (dev/e2e only; showInNav:false)
      // In production the live-test surface is embedded in PAC calibracao and
      // onboarding via GestShellIntegrationService.resolveLiveTestSlot().
      {
        path: 'gest',
        loadChildren: () =>
          import('@neuroassist/gest').then((m) => m.GEST_ROUTES),
      },

      // EVNT — eventos e alarmes (routine feed; emergency alarm is a global shell outlet)
      {
        path: 'eventos',
        loadChildren: () =>
          import('@neuroassist/evnt').then((m) => m.EVNT_ROUTES),
      },

      // PAC — Pacientes e Catálogo de Comandos (Calibração)
      {
        path: 'pac',
        loadChildren: () =>
          import('@neuroassist/pac').then((m) => m.PAC_ROUTES),
      },

      // EDGE — Integração de Dispositivo Edge (showInNav:false)
      // Thin host for dev/e2e and the connectivity chip target (/edge/device).
      // Primary surfaces: topbar connectivity chip (composed) + onboarding step-3 (composed).
      {
        path: 'edge',
        loadChildren: () =>
          import('@neuroassist/edge').then((m) => m.EDGE_ROUTES),
      },

      // Additional modules added progressively via /fe-architect-promote:
      // { path: 'hist', loadChildren: () => import('@neuroassist/hist').then(m => m.HIST_ROUTES) },
      // { path: 'dash', loadChildren: () => import('@neuroassist/dash').then(m => m.DASH_ROUTES) },
      // { path: 'acs', loadChildren: () => import('@neuroassist/acs').then(m => m.ACS_ROUTES) },

      { path: '**', redirectTo: '' },
    ],
  },
];
