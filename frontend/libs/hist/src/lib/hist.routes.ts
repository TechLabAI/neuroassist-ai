import { Routes } from '@angular/router';
import { authGuard, histGuard } from './access/hist.guard';

// =============================================================================
// HIST_ROUTES — SPEC-01 (route map)
// Route: /historico → HistoryTimelineComponent
// Guards: [authGuard, histGuard] — Wave-0 bypass active until auth layer promoted.
// =============================================================================

export const HIST_ROUTES: Routes = [
  {
    path: '',
    canActivate: [authGuard, histGuard],
    loadComponent: () =>
      import('./feature-timeline/history-timeline.component').then(
        (m) => m.HistoryTimelineComponent,
      ),
    title: 'Histórico',
  },
];
