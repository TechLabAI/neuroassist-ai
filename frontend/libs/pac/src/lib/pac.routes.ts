import { Routes } from '@angular/router';
import { pacAuthGuard } from './access/pac-auth.guard';
import { pacTenantPatientGuard } from './access/pac-tenant-patient.guard';
import { pacCommandWriteGuard } from './access/pac-command-write.guard';

// ---------------------------------------------------------------------------
// PAC_ROUTES — shell route: /pac (generator-config.yml shell_route)
// Guards: authGuard → pacTenantPatientGuard (catalog) → pacCommandWriteGuard (editor)
// ---------------------------------------------------------------------------

export const PAC_ROUTES: Routes = [
  {
    path: '',
    canActivate: [pacAuthGuard, pacTenantPatientGuard],
    loadComponent: () =>
      import('./feature-command-catalog/command-catalog.component').then(
        (m) => m.CommandCatalogComponent,
      ),
  },
  {
    path: 'comandos/novo',
    canActivate: [pacAuthGuard, pacTenantPatientGuard, pacCommandWriteGuard],
    loadComponent: () =>
      import('./feature-command-editor/command-editor.component').then(
        (m) => m.CommandEditorComponent,
      ),
  },
  {
    path: 'comandos/:comandoId/editar',
    canActivate: [pacAuthGuard, pacTenantPatientGuard, pacCommandWriteGuard],
    loadComponent: () =>
      import('./feature-command-editor/command-editor.component').then(
        (m) => m.CommandEditorComponent,
      ),
  },
  { path: '**', redirectTo: '' },
];
