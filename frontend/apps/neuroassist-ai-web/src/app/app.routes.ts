import { Routes } from '@angular/router';
import { AppShellComponent } from './shell/app-shell.component';
import { pacAuthGuard, pacTenantPatientGuard, pacCommandWriteGuard } from '@neuroassist/pac';
// DASH — promoted by /fe-architect-promote neuroassist-ai DASH (D-494)

export const appRoutes: Routes = [
  // Onboarding wizard — scope:web page (ACS-owned), outside shell layout
  {
    path: 'onboarding',
    loadComponent: () =>
      import('./pages/onboarding-wizard-page.component').then(
        (m) => m.OnboardingWizardPageComponent,
      ),
    title: 'Configuração inicial',
  },

  // DASH Entrada — chromeless brand entry (outside shell chrome — FE-DASH generator-config)
  // No shell chrome: rendered at top-level, not inside AppShellComponent.
  {
    path: 'entrada',
    loadComponent: () =>
      import('@neuroassist/dash').then((m) => m.EntradaComponent),
    title: 'Bem-vindo',
  },

  {
    path: '',
    component: AppShellComponent,
    children: [
      // DASH — Dashboard e Notificações (default landing: /)
      // Replaces the previous redirectTo:'home' placeholder.
      // DASH_ROUTES covers: / (HomeStatus) and /notificacoes (NotificationCenter).
      {
        path: '',
        loadChildren: () =>
          import('@neuroassist/dash').then((m) => m.DASH_ROUTES),
      },

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
      // Catalog delegates to PAC_ROUTES as-is; the two comando-editor paths are
      // overridden here (not via PAC_ROUTES) because the live-test composition
      // — GestureLiveTestComponent projected into [slot=live-test] — must live
      // at scope:web (PAC never imports scope:gest, GEST-INT-005/ADR-002).
      // Guards are reused verbatim from PAC's own route definitions.
      {
        path: 'pac',
        children: [
          // These two MUST come before the catalog's loadChildren below —
          // Angular tries children in array order, and PAC_ROUTES (nested
          // under path:'') would otherwise greedily match these same URLs
          // first via its own internal (unwrapped) editor routes.
          {
            path: 'comandos/novo',
            canActivate: [pacAuthGuard, pacTenantPatientGuard, pacCommandWriteGuard],
            loadComponent: () =>
              import('./pages/pac-comando-editor-page.component').then(
                (m) => m.PacComandoEditorPageComponent,
              ),
          },
          {
            path: 'comandos/:comandoId/editar',
            canActivate: [pacAuthGuard, pacTenantPatientGuard, pacCommandWriteGuard],
            loadComponent: () =>
              import('./pages/pac-comando-editor-page.component').then(
                (m) => m.PacComandoEditorPageComponent,
              ),
          },
          {
            path: '',
            loadChildren: () =>
              import('@neuroassist/pac').then((m) => m.PAC_ROUTES),
          },
        ],
      },

      // EDGE — Integração de Dispositivo Edge (showInNav:false)
      // Thin host for dev/e2e and the connectivity chip target (/edge/device).
      // Primary surfaces: topbar connectivity chip (composed) + onboarding step-3 (composed).
      {
        path: 'edge',
        loadChildren: () =>
          import('@neuroassist/edge').then((m) => m.EDGE_ROUTES),
      },

      // ACS — Cuidadores e Acesso (promoted by /fe-architect-promote ACS)
      {
        path: 'acs',
        loadChildren: () =>
          import('@neuroassist/acs').then((m) => m.ACS_ROUTES),
      },

      // HIST — Histórico e Auditoria (promoted by /fe-architect-promote HIST)
      {
        path: 'historico',
        loadChildren: () =>
          import('@neuroassist/hist').then((m) => m.HIST_ROUTES),
      },

      // Additional modules added progressively via /fe-architect-promote.

      { path: '**', redirectTo: '' },
    ],
  },
];
