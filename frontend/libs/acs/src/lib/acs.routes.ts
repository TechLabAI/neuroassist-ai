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
    // `acsCoordinatorGuard` sozinho só exigia SESSÃO — e cuidadora tem sessão.
    // A §4 de docs/operacao/fluxo-e-perfis.md é explícita: "Equipe" é item
    // exclusivo da coordenadora, e a cuidadora "não vê: cuidadoras, dispositivos
    // da clínica…". O menu (shell/menu-trabalho.ts) já escondia o item filtrando
    // por `acs.cuidador.criar`, mas esconder é ergonomia — o próprio arquivo diz
    // "quem tranca a porta é o guard da rota", e aqui não havia tranca: bastava
    // digitar /acs para a cuidadora cair no roster inteiro, com Convidar e
    // Desativar à mão.
    //
    // `acsCaregiverWriteGuard` é o guard certo e não um atalho: ele consulta
    // 'acs.cuidador.write', que o MODULE_PERMISSION_MAP traduz para
    // 'acs.cuidador.criar' — exatamente a authority por que o menu filtra. Um
    // segundo guard com a mesma regra seria mais um literal para divergir.
    canActivate: [acsCoordinatorGuard, acsCaregiverWriteGuard],
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
