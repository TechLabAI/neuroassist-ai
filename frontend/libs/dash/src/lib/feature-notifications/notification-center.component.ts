import {
  Component,
  ChangeDetectionStrategy,
  inject,
  OnInit,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { NotificationsFacade } from '../notifications.facade';

// =============================================================================
// NotificationCenterComponent — /notificacoes route (inside shell)
// Spec: SPEC-06 — Notification Center (FE-DASH-006..010)
// Shows list of pending (unacknowledged) notifications; allows acknowledge.
// Data source: NotificationsFacade → GET /panel/notifications/unacknowledged
// Acknowledge: write-through to EVNT via DASH (POST /panel/notifications/{id}/acknowledge)
// TODO[INT-FE-DASH-001]: wire real-time updates from SSE stream at scope:web.
// =============================================================================

@Component({
  selector: 'lib-notification-center',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="mx-auto max-w-3xl p-8">
      <header class="mb-7 flex items-center justify-between">
        <div>
          <h1 class="font-display text-2xl font-bold tracking-tight text-ink">Notificações</h1>
          <p class="mt-1 text-sm text-ink-soft">
            Notificações pendentes de revisão.
          </p>
        </div>
        <a routerLink="/" class="text-sm font-semibold text-brand hover:text-brand-strong">
          ← Voltar ao status
        </a>
      </header>

      @if (facade.loading()) {
        <div class="flex min-h-[30vh] items-center justify-center text-sm text-ink-faint">
          Carregando notificações…
        </div>
      } @else if (facade.status() === 'error') {
        <div class="flex flex-col items-center gap-4 rounded-2xl border border-line bg-surface p-8 text-center">
          <p class="text-sm text-danger">{{ facade.error()?.message ?? 'Erro ao carregar notificações.' }}</p>
          <button
            (click)="facade.retry()"
            class="rounded-lg bg-surface-2 px-4 py-2 text-sm font-semibold text-ink hover:bg-surface-3">
            Tentar novamente
          </button>
        </div>
      } @else if (facade.status() === 'empty') {
        <div class="rounded-2xl border border-line bg-surface p-8 text-center">
          <span class="material-symbols-outlined text-[48px] text-success">check_circle</span>
          <p class="mt-3 text-sm font-semibold text-ink">Tudo revisado</p>
          <p class="mt-1 text-sm text-ink-soft">Nenhuma notificação pendente no momento.</p>
        </div>
      } @else {
        <div class="flex flex-col gap-4">
          @for (notification of facade.notifications(); track notification.dispatchId) {
            <div class="flex items-start justify-between gap-4 rounded-2xl border border-line bg-surface p-5">
              <div class="flex items-start gap-3">
                <div
                  class="mt-0.5 grid size-9 flex-shrink-0 place-items-center rounded-full"
                  [class]="notification.urgencia === 'emergencia' ? 'bg-danger-bg' : 'bg-info-bg'">
                  <span
                    class="material-symbols-outlined text-[20px]"
                    [class]="notification.urgencia === 'emergencia' ? 'text-danger' : 'text-info'">
                    {{ notification.urgencia === 'emergencia' ? 'emergency' : 'notifications' }}
                  </span>
                </div>
                <div>
                  <p class="text-sm font-semibold text-ink">"{{ notification.commandLabel }}"</p>
                  <p class="mt-0.5 text-xs text-ink-faint">
                    {{ formatTime(notification.raisedAt) }}
                    <span
                      class="ml-2 rounded-full px-1.5 py-0.5 text-[11px] font-semibold"
                      [class]="notification.urgencia === 'emergencia' ? 'bg-danger-bg text-danger-ink' : 'bg-info-bg text-info-ink'">
                      {{ notification.urgencia }}
                    </span>
                  </p>
                </div>
              </div>

              <button
                (click)="acknowledge(notification.dispatchId)"
                class="flex-shrink-0 rounded-xl bg-brand px-4 py-2 text-xs font-bold text-brand-ink hover:bg-brand-strong">
                Revisei
              </button>
            </div>
          }
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationCenterComponent implements OnInit {
  protected readonly facade = inject(NotificationsFacade);

  protected formatTime(iso: string): string {
    const d = new Date(iso);
    const dd = String(d.getDate()).padStart(2, '0');
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    return `${dd}/${mm} às ${String(d.getHours()).padStart(2, '0')}h${String(d.getMinutes()).padStart(2, '0')}`;
  }

  protected acknowledge(dispatchId: string): void {
    this.facade.acknowledge(dispatchId);
  }

  ngOnInit(): void {
    this.facade.load();
  }
}
