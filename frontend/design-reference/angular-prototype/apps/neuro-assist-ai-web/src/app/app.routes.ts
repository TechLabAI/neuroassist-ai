import { Routes } from '@angular/router';
import { ShellComponent } from './shell/shell.component';
import { PatientShellComponent } from './shell/patient-shell.component';

export const APP_ROUTES: Routes = [
  {
    path: 'entrada',
    loadComponent: () =>
      import('./screens/entrada/entrada.component').then(m => m.EntradaComponent),
    data: { label: 'Entrada', icon: '•' },
  },
  // Emenda 3 (2026-08-30) — shell separado e restrito para a sessão do paciente
  // (ADR-012/014; decisão de wireframe registrada em design/wireframe.md §3.2.B adendo).
  // Não é destino de navegação da cuidadora — é o que o celular do paciente exibe.
  {
    path: 'paciente',
    component: PatientShellComponent,
    children: [
      { path: '', redirectTo: 'conversar', pathMatch: 'full' },
      {
        path: 'conversar',
        loadComponent: () =>
          import('./screens/conv-arvore/conv-arvore.component').then(m => m.ConvArvoreComponent),
        data: { label: 'Conversar', icon: 'chat_bubble' },
      },
      // Emenda 4 (2026-08-31) — Tela 09 do wireframe. Acessível a partir do banner na
      // Tela 06 quando há propostas do TREINAMENTO aguardando confirmação (ADR-016).
      {
        path: 'confirmar-treinamento',
        loadComponent: () =>
          import('./screens/confirmacao-treinamento/confirmacao-treinamento.component').then(m => m.ConfirmacaoTreinamentoComponent),
        data: { label: 'Confirmar frases novas', icon: 'auto_awesome' },
      },
    ],
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
      // Emenda 3 (2026-08-30) — Tela 07 do wireframe. Acessível a partir do Onboarding
      // (link de demonstração), não da sidebar diária da cuidadora — ver ADR-012/014.
      {
        path: 'celular-paciente',
        loadComponent: () =>
          import('./screens/celular-ativacao/celular-ativacao.component').then(m => m.CelularAtivacaoComponent),
        data: { label: 'Ativar celular do paciente', icon: 'smartphone' },
      },
      // Emenda 4 (2026-08-31) — Tela 08 do wireframe. Vive no shell da cuidadora (DASH),
      // não do paciente — sessão ocasional, não uso diário (ADR-016).
      {
        path: 'treinamento',
        loadComponent: () =>
          import('./screens/treinamento/treinamento.component').then(m => m.TreinamentoComponent),
        data: { label: 'Treinamento', icon: 'auto_awesome' },
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
