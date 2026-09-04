import { Routes } from '@angular/router';
import { TreiRootComponent } from './trei-root.component';
import { caregiverSessionGuard } from './access/caregiver-session.guard';
import { treiGuard } from './access/trei.guard';
import { treiProporGuard } from './access/trei-propor.guard';

// =============================================================================
// TREI_ROUTES — SPEC-01 (route map, FE-TREI-001)
//
// Route tree (all under /treinamento in app.routes.ts):
//   /treinamento               → TreiRootComponent (layout host + router-outlet)
//   /treinamento/sessao        → TrainingSessionPageComponent (Tela 08)
//   /treinamento/sessao/propostas → ProposalReviewPageComponent (proposal review)
//   /treinamento               → redirects to /treinamento/sessao
//
// Guards (P3 — real authorization, no Wave-0 bypass):
//   caregiverSessionGuard — active caregiver JWT (AuthSessionStore.autenticado)
//   treiGuard             — treinamento.sessao.conduzir authority (AuthSessionStore.pode)
//   treiProporGuard       — treinamento.no.propor authority (G1: redirect to sessao, never 403)
//
// TreiRootComponent is the parent layout component that hosts the <router-outlet>
// for all TREI child routes. P6 will add TreiAuthoringBannerComponent here so
// the banner appears on all TREI routes without per-page duplication.
// =============================================================================

export const TREI_ROUTES: Routes = [
  {
    path: '',
    component: TreiRootComponent,
    canActivate: [caregiverSessionGuard, treiGuard],
    children: [
      {
        path: '',
        redirectTo: 'sessao',
        pathMatch: 'full',
      },
      {
        path: 'sessao',
        loadComponent: () =>
          import('./feature-training-session/training-session-page.component').then(
            (m) => m.TrainingSessionPageComponent,
          ),
        title: 'Treinamento',
      },
      {
        // ProposalReview: /treinamento/sessao/propostas
        // G1: treiProporGuard redirects to /treinamento/sessao when scope absent
        // (never 403, never blocks — review surface remains readable without propor scope)
        path: 'sessao/propostas',
        canActivate: [treiProporGuard],
        loadComponent: () =>
          import('./feature-proposal-review/proposal-review-page.component').then(
            (m) => m.ProposalReviewPageComponent,
          ),
        title: 'Propostas — Treinamento',
      },
    ],
  },
];
