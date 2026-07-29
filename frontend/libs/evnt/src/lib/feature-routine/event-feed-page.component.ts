import {
  Component,
  ChangeDetectionStrategy,
  inject,
  computed,
  OnInit,
} from '@angular/core';
import { Router } from '@angular/router';
import { EventDeliveryFacade } from '../evnt.facade';
import { EventoVm } from '../evnt.types';

// =============================================================================
// EventFeedPageComponent — SPEC-03 §3, SPEC-02 §3 (routine-feed surface)
// Route: /eventos (registered via EVNT_ROUTES)
// Shows the routine event queue (discreet, dismissible) and delivery-mode banner.
// Emergency alarm is handled exclusively by AlarmOverlayComponent (shell outlet).
// =============================================================================

function horaLabel(iso: string): string {
  const d = new Date(iso);
  const h = String(d.getHours()).padStart(2, '0');
  const m = String(d.getMinutes()).padStart(2, '0');
  return `${h}h${m}`;
}

@Component({
  selector: 'evnt-event-feed-page',
  standalone: true,
  template: `
    <div class="mx-auto max-w-2xl px-4 py-8">

      <!-- Degraded banner (SPEC-04 §4) -->
      @if (deliveryMode() === 'degraded') {
        <div class="mb-4 flex items-center gap-2 rounded-lg bg-warn/10 px-4 py-3 text-sm text-warn-ink">
          <span class="material-symbols-outlined text-[18px]">signal_disconnected</span>
          <span>Modo degradado — recebendo eventos via polling.</span>
        </div>
      }

      <h1 class="font-display text-xl font-bold text-ink mb-6">Notificações de rotina</h1>

      <!-- Loading state -->
      @if (loading()) {
        <div class="flex justify-center py-12">
          <span class="material-symbols-outlined animate-spin text-[32px] text-ink-faint">
            progress_activity
          </span>
        </div>
      }

      <!-- Error state -->
      @if (error()) {
        <div class="rounded-lg bg-error/10 px-4 py-3 text-sm text-error-ink">
          Erro ao carregar notificações. Tente novamente.
        </div>
      }

      <!-- Empty state -->
      @if (!loading() && !error() && routineQueue().length === 0) {
        <div class="flex flex-col items-center py-16 text-center">
          <span class="material-symbols-outlined text-[56px] text-ink-faint">notifications_off</span>
          <p class="mt-4 text-sm text-ink-soft">Nenhuma notificação de rotina no momento.</p>
        </div>
      }

      <!-- Routine queue -->
      @for (evt of routineQueue(); track evt.eventoId) {
        <div
          class="mb-3 flex items-start gap-3 rounded-xl border border-surface-border bg-surface px-4 py-3 shadow-sm">

          <span class="material-symbols-outlined mt-0.5 text-[20px] text-ink-soft">
            notifications
          </span>

          <div class="flex-1 min-w-0">
            <p class="text-sm font-semibold text-ink">"{{ evt.commandLabel }}"</p>
            <p class="mt-0.5 text-xs text-ink-soft">
              {{ evt.patientName }} · {{ horaLabel(evt.recognizedAt) }}
            </p>
          </div>

          <div class="flex shrink-0 gap-2">
            <button
              (click)="verDetalhe(evt)"
              class="rounded-lg px-3 py-1.5 text-xs font-medium text-primary hover:bg-primary/10 transition-colors">
              Ver
            </button>
            <button
              (click)="dispensar(evt.eventoId)"
              class="rounded-lg px-3 py-1.5 text-xs font-medium text-ink-soft hover:bg-surface-hover transition-colors">
              <span class="material-symbols-outlined text-[16px]">close</span>
            </button>
          </div>
        </div>
      }

    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventFeedPageComponent implements OnInit {
  private readonly facade = inject(EventDeliveryFacade);
  private readonly router = inject(Router);

  readonly routineQueue = this.facade.routineQueue;
  readonly deliveryMode = this.facade.deliveryMode;
  readonly loading = this.facade.loading;
  readonly error = this.facade.error;

  /** True when queue is empty and not loading (SPEC-03 §3). */
  readonly empty = computed(() => !this.loading() && this.routineQueue().length === 0);

  protected horaLabel = horaLabel;

  ngOnInit(): void {
    // Hydration already done by evnt.bootstrap; this re-fetch is lightweight
    // and refreshes the routine list if the user navigates to /eventos later.
    // TODO[FE-EVNT-020]: load history (recent events) for SPEC-03 §3 recentEvents
    void this.facade.loadHistory({ page: 0, size: 20 });
  }

  protected dispensar(eventoId: string): void {
    this.facade.dismiss(eventoId);
  }

  protected verDetalhe(evt: EventoVm): void {
    void this.router.navigate(['/eventos', evt.eventoId]);
  }
}
