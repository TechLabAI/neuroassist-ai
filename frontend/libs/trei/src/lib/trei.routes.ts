import { Routes } from '@angular/router';
import { caregiverSessionGuard, treiGuard, treiProporGuard } from './access/trei.guard';

// =============================================================================
// TREI_ROUTES — SPEC-01 (route map, FE-TREI-001)
// Routes:
//   /treinamento/sessao          → TrainingSessionPageComponent (Tela 08)
//   /treinamento/sessao/propostas → ProposalReviewPageComponent (Tela 08 proposals)
//
// Guards: Wave-0 bypass active (tokens null → allow through).
// Shell route root: /treinamento (registered in app.routes.ts → TREI_ROUTES).
// =============================================================================

export const TREI_ROUTES: Routes = [
  {
    path: '',
    redirectTo: 'sessao',
    pathMatch: 'full',
  },
  {
    path: 'sessao',
    canActivate: [caregiverSessionGuard, treiGuard],
    loadComponent: () =>
      import('./feature-training-session/training-session-page.component').then(
        (m) => m.TrainingSessionPageComponent,
      ),
    title: 'Treinamento',
  },
  {
    // Sibling route (not child): ProposalReview is a full-page view, not nested inside
    // TrainingSessionPageComponent (which would require a <router-outlet>). Using a
    // sibling makes it a standalone page under /treinamento/sessao/propostas.
    path: 'sessao/propostas',
    canActivate: [caregiverSessionGuard, treiProporGuard],
    loadComponent: () =>
      import('./feature-proposal-review/proposal-review-page.component').then(
        (m) => m.ProposalReviewPageComponent,
      ),
    title: 'Propostas — Treinamento',
  },
];
