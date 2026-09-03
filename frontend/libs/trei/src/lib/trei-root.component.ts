import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TreiAuthoringBannerComponent } from './ui/trei-authoring-banner.component';

// =============================================================================
// TreiRootComponent — SPEC-06 §2 (FE-TREI-002)
//
// Root component for the /treinamento route subtree.
// Acts as the layout host for all TREI pages (TrainingSession + ProposalReview).
// Provides the <router-outlet> where child routes are rendered.
//
// The TreiAuthoringBannerComponent is declared here (P6) so it appears on
// every TREI child route without duplication in individual page components
// (INV-6). Previously it was inlined in TrainingSessionPageComponent (P4).
//
// Selector: 'trei-root' — used only by the Angular router (never directly in
// another template since TREI is always loaded via loadChildren / loadComponent).
// =============================================================================

@Component({
  selector: 'trei-root',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, TreiAuthoringBannerComponent],
  template: `
    <trei-authoring-banner />
    <router-outlet />
  `,
})
export class TreiRootComponent {}
