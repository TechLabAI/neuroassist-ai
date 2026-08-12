import { Routes } from '@angular/router';
import { acsCoordinatorGuard, acsCaregiverWriteGuard, acsLinkWriteGuard } from './access/acs.guard';

// =============================================================================
// ACS_ROUTES — SPEC-01 (route map)
// Route: /acs       → CaregiverRosterComponent   (roster + coverage overview)
// Route: /acs/cuidadores/novo → CaregiverEditorComponent (invite)
// Route: /acs/cuidadores/:id/editar → CaregiverEditorComponent (edit)
// Route: /acs/pacientes/:pacienteId/vinculos → PatientLinkageComponent
//
// NOTE: ACS also contributes TenantSetupComponent and CaregiverLinkStepComponent
// as composed features in the scope:web onboarding wizard (not shell-routed here).
// =============================================================================

export const ACS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [acsCoordinatorGuard],
    loadComponent: () =>
      import('./feature-caregiver-roster/caregiver-roster.component').then(
        (m) => m.CaregiverRosterComponent,
      ),
    title: 'Cuidadores',
  },
  {
    path: 'cuidadores/novo',
    canActivate: [acsCoordinatorGuard, acsCaregiverWriteGuard],
    loadComponent: () =>
      import('./feature-caregiver-editor/caregiver-editor.component').then(
        (m) => m.CaregiverEditorComponent,
      ),
    title: 'Convidar cuidador',
  },
  {
    path: 'cuidadores/:cuidadorId/editar',
    canActivate: [acsCoordinatorGuard, acsCaregiverWriteGuard],
    loadComponent: () =>
      import('./feature-caregiver-editor/caregiver-editor.component').then(
        (m) => m.CaregiverEditorComponent,
      ),
    title: 'Editar cuidador',
  },
  {
    path: 'pacientes/:pacienteId/vinculos',
    canActivate: [acsCoordinatorGuard, acsLinkWriteGuard],
    loadComponent: () =>
      import('./feature-patient-linkage/patient-linkage.component').then(
        (m) => m.PatientLinkageComponent,
      ),
    title: 'Vínculos do paciente',
  },
];
