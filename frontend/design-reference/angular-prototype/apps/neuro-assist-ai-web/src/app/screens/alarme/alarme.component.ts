import { Component, ChangeDetectionStrategy, inject, computed } from '@angular/core';
import { EventosFacade, ComandosFacade } from '../../core/data-access';

// O overlay de alarme de emergência agora vive no ShellComponent (aparece em qualquer tela).
// Esta rota mantém apenas um painel de status — útil para navegação direta.

@Component({
  selector: 'app-alarme',
  standalone: true,
  template: `
    <div class="mx-auto max-w-lg p-8">
      <h1 class="mb-6 font-display text-2xl font-bold text-ink">Status do alarme</h1>

      @if (eventoPendente()) {
        <div class="flex items-center gap-3 rounded-xl border border-alarm bg-alarm-bg px-5 py-4">
          <span class="material-symbols-outlined text-[28px] text-alarm">emergency_home</span>
          <div>
            <p class="font-semibold text-alarm">Alarme ativo — aguardando confirmação</p>
            <p class="mt-0.5 text-sm text-alarm/80">O modal de confirmação está visível em tela.</p>
          </div>
        </div>
      } @else {
        <div class="flex items-center gap-3 rounded-xl border border-line bg-surface-2 px-5 py-4">
          <span class="material-symbols-outlined text-[28px] text-ink-faint">notifications_off</span>
          <p class="text-sm text-ink-soft">Nenhum alarme de emergência ativo no momento.</p>
        </div>
      }

      <p class="mt-4 text-xs text-ink-faint">
        O alarme aparece automaticamente em qualquer tela do app quando um gesto é detectado.
        Notificações do sistema são enviadas mesmo com o app em segundo plano (requer permissão).
      </p>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlarmeComponent {
  private readonly facade         = inject(EventosFacade);
  private readonly comandosFacade = inject(ComandosFacade);

  readonly eventoPendente = computed(() =>
    this.facade.items()
      .filter(e => this.comandosFacade.items().find(c => c.id === e.comando)?.urgencia === 'emergencia')
      .find(e => !e.confirmadoEm) ?? null,
  );
}
