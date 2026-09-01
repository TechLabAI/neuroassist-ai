import { Routes } from '@angular/router';
import { PatientShellComponent } from './patient-shell/patient-shell.component';

// =============================================================================
// CONV_ROUTES — patient shell routes (registered under /paciente in app.routes.ts).
//
// Surface: patient-only (registerInAdminShell: false — CONV-FE-001).
// Isolation: /paciente is a top-level route (like /estacao) outside AppShellComponent.
// No caregiver route is listed here — isolation by absence of route (ADR-012/014).
//
// Route structure (public paths via GWY):
//   /paciente                     → redirect to /conversar
//   /paciente/conversar           → ConvArvoreComponent (Tela 06)
//   /paciente/confirmar-treinamento → ConfirmacaoTreinamentoComponent (Tela 09)
//
// Guards: patientSessionGuard deferred to CONV-FE-018 (Layer 2).
// =============================================================================

export const CONV_ROUTES: Routes = [
  {
    path: '',
    component: PatientShellComponent,
    // TODO[CONV-FE-018]: canActivate: [patientSessionGuard]
    children: [
      {
        path: 'conversar',
        loadComponent: () =>
          import('./feature-conv-arvore/conv-arvore.component').then(
            m => m.ConvArvoreComponent,
          ),
        title: 'Conversar',
      },
      {
        path: 'confirmar-treinamento',
        loadComponent: () =>
          import('./feature-confirmacao-treinamento/confirmacao-treinamento.component').then(
            m => m.ConfirmacaoTreinamentoComponent,
          ),
        title: 'Confirmar frases',
      },
      { path: '', redirectTo: 'conversar', pathMatch: 'full' },
    ],
  },
];
