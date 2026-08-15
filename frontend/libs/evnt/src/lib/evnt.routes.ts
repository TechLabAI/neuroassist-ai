import { Routes } from '@angular/router';
import { evntGuard } from './access/evnt.guard';

// =============================================================================
// EVNT_ROUTES — SPEC-01 (route map)
// Route: /eventos             → EventFeedPageComponent (routine feed list)
// Route: /eventos/:eventoId   → EventDetailPageComponent (FE-EVNT-016)
//
// The emergency alarm overlay is NOT here — it is a global shell outlet
// registered by EvntShellIntegrationService (ADR-005, SPEC-06 §2).
// =============================================================================

export const EVNT_ROUTES: Routes = [
  {
    path: '',
    canActivate: [evntGuard],
    loadComponent: () =>
      import('./feature-routine/event-feed-page.component').then(
        (m) => m.EventFeedPageComponent,
      ),
  },
  {
    path: ':eventoId',
    canActivate: [evntGuard],
    loadComponent: () =>
      import('./feature-routine/event-detail-page/event-detail-page.component').then(
        (m) => m.EventDetailPageComponent,
      ),
  },
];
