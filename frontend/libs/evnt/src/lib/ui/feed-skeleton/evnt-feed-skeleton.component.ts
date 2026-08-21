import { Component, ChangeDetectionStrategy } from '@angular/core';

// =============================================================================
// EvntFeedSkeletonComponent — FE-EVNT-022 / P7
//
// Skeleton loader for EventFeedPageComponent.
// Renders 3 animated placeholder rows shown while loading=true.
// Zero DI — purely presentational (plan P7, SPEC-03 §3).
//
// a11y: aria-busy="true" + aria-label signals screen readers that content is
// being loaded. Individual skeleton rows are aria-hidden — they carry no
// semantic information.
// =============================================================================

@Component({
  selector: 'evnt-feed-skeleton',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="evnt-feed-skeleton"
      aria-busy="true"
      aria-label="Carregando notificações">
      <div class="skeleton-row" aria-hidden="true"></div>
      <div class="skeleton-row" aria-hidden="true"></div>
      <div class="skeleton-row" aria-hidden="true"></div>
    </div>
  `,
  styles: [
    `
      @keyframes evnt-pulse {
        0%,
        100% {
          opacity: 0.12;
        }
        50% {
          opacity: 0.05;
        }
      }

      .evnt-feed-skeleton {
        padding: 0.5rem 0;
      }

      .skeleton-row {
        height: 56px;
        border-radius: 8px;
        background: currentColor;
        opacity: 0.12;
        margin-bottom: 12px;
        animation: evnt-pulse 1.5s ease-in-out infinite;
      }

      .skeleton-row:last-child {
        margin-bottom: 0;
      }
    `,
  ],
})
export class EvntFeedSkeletonComponent {}
