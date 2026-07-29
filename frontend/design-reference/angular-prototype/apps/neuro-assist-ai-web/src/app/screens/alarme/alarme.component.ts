import { Component, ChangeDetectionStrategy, inject, computed, signal } from '@angular/core';
import { EventosFacade, ComandosFacade, CuidadoresFacade } from '../../core/data-access';
import { agoraIso } from '../../shared/clock';

// Contrato: .build-plans/alarme.plan.md  (o implementer lê SÓ o build-plan)
// Wireframe ref: §5.1–5.5 — Alarme — fluxo-mãe do produto (4.2)
// Objetivo: ser absolutamente inconfundível (Princípio 2.2) e exigir confirmação humana
// explícita antes de voltar ao normal (Princípio 2.3). (Emenda 1) Exclusiva para comandos
// com urgencia=emergencia — comandos de rotina nunca chegam aqui (ver toast §3.2.E).
// Lê a mesma coleção Evento[] que Home/Histórico (EventosFacade) — não há mais uma
// coleção "evento_ativo" separada. "Evento ativo" = o mais recente com urgência
// emergencia e SEM confirmadoEm (não apenas "o mais recente", que poderia ser um
// alarme já resolvido há dias — ver wireframe §5.5: esta tela só existe quando há
// evento ativo de verdade).

function hora(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}h${String(d.getMinutes()).padStart(2, '0')}`;
}

@Component({
  selector: 'app-alarme',
  standalone: true,
  template: `
    @if (eventoPendente(); as evt) {
      <div class="fixed inset-0 z-50 flex flex-col items-center justify-center bg-alarm px-8 text-center">
        <span class="material-symbols-outlined text-[96px] text-alarm-ink">emergency_home</span>
        <h1 class="mt-6 font-display text-4xl font-extrabold tracking-tight text-alarm-ink">
          {{ pacienteNome }} precisa de ajuda
        </h1>
        <p class="mt-3 text-lg font-semibold text-alarm-ink/90">"{{ rotulo(evt.comando) }}"</p>
        <p class="mt-2 text-base font-medium text-alarm-ink/80">
          Gesto detectado às {{ hora(evt.timestamp) }}
        </p>
        <button
          (click)="confirmar(evt.id)"
          class="mt-12 inline-flex items-center gap-2 rounded-xl bg-white px-8 py-4 text-base font-bold text-alarm shadow-lg transition-transform hover:scale-[1.02] active:scale-[0.98]">
          <span class="material-symbols-outlined text-[22px]">check</span>
          Confirmar que atendi {{ pacienteNome.split(' ')[0] }}
        </button>
      </div>
    } @else if (recemConfirmado(); as evt) {
      <div class="mx-auto flex min-h-[70vh] max-w-md flex-col items-center justify-center p-8 text-center">
        <span class="material-symbols-outlined text-[64px] text-success">check_circle</span>
        <h1 class="mt-5 font-display text-2xl font-bold text-ink">Atendimento confirmado</h1>
        <p class="mt-2 text-sm text-ink-soft">
          {{ pacienteNome }} foi atendido às {{ hora(evt.confirmadoEm!) }} por {{ nomeDoCuidador(evt.confirmadoPor) }}.
        </p>
      </div>
    } @else {
      <div class="mx-auto flex min-h-[70vh] max-w-md flex-col items-center justify-center p-8 text-center">
        <span class="material-symbols-outlined text-[64px] text-ink-faint">notifications_off</span>
        <p class="mt-4 text-sm text-ink-soft">Nenhum alarme de emergência ativo no momento.</p>
      </div>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlarmeComponent {
  private readonly facade = inject(EventosFacade);
  private readonly comandosFacade = inject(ComandosFacade);
  private readonly cuidadoresFacade = inject(CuidadoresFacade);
  protected readonly hora = hora;
  protected readonly pacienteNome = 'Márcio Amaral';

  /** Id do evento confirmado nesta sessão — só existe para dar feedback imediato após
   * o clique em "Confirmar"; não persiste como estado da tela numa visita nova. */
  private readonly confirmadoNestaSessaoId = signal<string | null>(null);

  private readonly emergencias = computed(() =>
    this.facade.items()
      .filter(e => this.comandosFacade.items().find(c => c.id === e.comando)?.urgencia === 'emergencia')
      .sort((a, b) => b.timestamp.localeCompare(a.timestamp)),
  );

  /** Evento ativo de verdade: o mais recente ainda SEM confirmadoEm. Um alarme já
   * resolvido (mesmo que seja o mais recente por timestamp) nunca aparece aqui. */
  readonly eventoPendente = computed(() => this.emergencias().find(e => !e.confirmadoEm) ?? null);

  readonly recemConfirmado = computed(() => {
    const id = this.confirmadoNestaSessaoId();
    if (!id) return null;
    return this.facade.items().find(e => e.id === id && e.confirmadoEm != null) ?? null;
  });

  protected rotulo(comandoId: string): string {
    return this.comandosFacade.items().find(c => c.id === comandoId)?.rotulo ?? '';
  }

  /** Evento.confirmadoPor guarda o id do Cuidador (mesma convenção do mock histórico,
   * ex.: 'cuid-jamilly') — resolve para o nome de exibição. */
  protected nomeDoCuidador(id: string): string {
    return this.cuidadoresFacade.items().find(c => c.id === id)?.nome ?? 'você';
  }

  protected confirmar(id: string): void {
    const now = agoraIso();
    const cuidador = this.cuidadoresFacade.items()[0]?.id ?? '';
    this.facade.items.update(list =>
      list.map(e => (e.id === id ? { ...e, confirmadoEm: now, confirmadoPor: cuidador, feedback: 'real' as const } : e)),
    );
    this.confirmadoNestaSessaoId.set(id);
    // TODO[fe-autopilot]: registrar confirmação em auditoria (fluxo-mãe do produto)
  }
}
