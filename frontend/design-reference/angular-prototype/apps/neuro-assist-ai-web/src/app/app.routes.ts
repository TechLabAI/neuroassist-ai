import { Routes } from '@angular/router';
import { ShellComponent } from './shell/shell.component';

export const APP_ROUTES: Routes = [
  {
    path: 'entrada',
    loadComponent: () =>
      import('./screens/entrada/entrada.component').then(m => m.EntradaComponent),
    data: { label: 'Entrada', icon: '•' },
  },
  {
    path: '',
    component: ShellComponent,
    children: [
      { path: '', redirectTo: 'home', pathMatch: 'full' },
      {
        path: 'home',
        loadComponent: () =>
          import('./screens/home/home.component').then(m => m.HomeComponent),
        data: { label: 'Home', icon: '🏠' },
      },
      {
        path: 'alarme',
        loadComponent: () =>
          import('./screens/alarme/alarme.component').then(m => m.AlarmeComponent),
        data: { label: 'Alarme', icon: '•' },
      },
      {
        path: 'historico',
        loadComponent: () =>
          import('./screens/historico/historico.component').then(m => m.HistoricoComponent),
        data: { label: 'Historico', icon: '•' },
      },
      {
        path: 'calibracao',
        loadComponent: () =>
          import('./screens/calibracao/calibracao.component').then(m => m.CalibracaoComponent),
        data: { label: 'Calibracao', icon: '•' },
      },
      {
        path: 'onboarding',
        loadComponent: () =>
          import('./screens/onboarding/onboarding.component').then(m => m.OnboardingComponent),
        data: { label: 'Onboarding', icon: 'rocket_launch' },
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
