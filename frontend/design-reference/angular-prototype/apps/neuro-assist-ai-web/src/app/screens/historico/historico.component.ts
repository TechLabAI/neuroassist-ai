import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { EventosFacade, ComandosFacade, PacientesFacade } from '../../core/data-access';
import { agora } from '../../shared/clock';
import { DsCardComponent } from '../../ui/ds-card.component';
import { DsBadgeComponent } from '../../ui/ds-badge.component';
import { DsKpiComponent } from '../../ui/ds-kpi.component';

// Contrato: .build-plans/historico.plan.md  (o implementer lê SÓ o build-plan)
// Wireframe ref: §5.1–5.5 — Historico
// Objetivo: revisar eventos do dia/semana/mês em linguagem humana (Princípio 2.5) e
// registrar feedback de falso positivo/negativo (fluxo 4.7). (Emenda 1) mostra o rótulo
// livre do comando (ex.: "água", "Sapo") + badge de urgência, não um texto fixo de gesto.

type Periodo = 'hoje' | 'semana' | 'mes';
const PERIODO_LABEL: Record<Periodo, string> = { hoje: 'Hoje', semana: 'Semana', mes: 'Mês' };
const PERIODO_DIAS: Record<Periodo, number> = { hoje: 1, semana: 7, mes: 30 };

function dataHora(iso: string): string {
  const d = new Date(iso);
  const dd = String(d.getDate()).padStart(2, '0');
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  return `${dd}/${mm} às ${String(d.getHours()).padStart(2, '0')}h${String(d.getMinutes()).padStart(2, '0')}`;
}
function hora(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}h${String(d.getMinutes()).padStart(2, '0')}`;
}

@Component({
  selector: 'app-historico',
  standalone: true,
  imports: [DsCardComponent, DsBadgeComponent, DsKpiComponent],
  template: `
    <div class="mx-auto max-w-6xl p-8">
      <header class="mb-7 flex items-center justify-between">
        <div>
          <h1 class="font-display text-2xl font-bold tracking-tight text-ink">Histórico</h1>
          <p class="mt-1 text-sm text-ink-soft">Eventos registrados pelo dispositivo de Márcio Amaral.</p>
        </div>
        <div class="inline-flex rounded-xl bg-surface-2 p-1">
          @for (p of periodos; track p) {
            <button
              (click)="periodo.set(p)"
              class="rounded-lg px-4 py-1.5 text-sm font-semibold transition-colors"
              [class]="periodo() === p ? 'bg-surface text-ink shadow-card' : 'text-ink-soft hover:text-ink'">
              {{ periodoLabel[p] }}
            </button>
          }
        </div>
      </header>

      <div class="grid grid-cols-1 gap-6 lg:grid-cols-[2fr_1fr]">
        <!-- Coluna principal: lista de eventos -->
        <div>
          @if (loading()) {
            <p class="text-sm text-ink-faint">Carregando eventos…</p>
          } @else if (eventosFiltrados().length === 0) {
            <app-ds-card>
              <p class="text-center text-sm text-ink-soft">Nenhum evento neste período.</p>
            </app-ds-card>
          } @else {
            <div class="flex flex-col gap-3">
              @for (evt of eventosFiltrados(); track evt.id) {
                <app-ds-card>
                  <div class="flex items-start justify-between gap-4">
                    <div>
                      <p class="text-sm font-semibold text-ink">
                        "{{ rotuloDoComando(evt.comando) }}"
                        <app-ds-badge [tone]="urgenciaDoComando(evt.comando) === 'emergencia' ? 'er' : 'nf'">{{ urgenciaDoComando(evt.comando) }}</app-ds-badge>
                        <span class="font-normal text-ink-faint">— {{ dataHora(evt.timestamp) }}</span>
                      </p>
                      @if (evt.confirmadoEm) {
                        <p class="mt-1 text-xs text-ink-soft">Você confirmou às {{ hora(evt.confirmadoEm) }}</p>
                      } @else {
                        <p class="mt-1 text-xs text-warning-ink">Ainda não confirmado</p>
                      }
                    </div>

                    @if (evt.feedback === 'nao_avaliado') {
                      <button
                        (click)="marcar(evt.id, 'real')"
                        class="flex-shrink-0 rounded-lg border-[1.5px] border-line-strong px-3 py-1.5 text-xs font-semibold text-ink-soft transition-colors hover:bg-surface-2">
                        Foi real?
                      </button>
                    } @else if (evt.feedback === 'real') {
                      <app-ds-badge tone="ok">✓ Marcado real</app-ds-badge>
                    } @else {
                      <app-ds-badge tone="mu">Falso positivo</app-ds-badge>
                    }
                  </div>
                </app-ds-card>
              }
            </div>
          }
        </div>

        <!-- Coluna lateral: resumo do período -->
        <div class="flex flex-col gap-6">
          <div class="grid grid-cols-2 gap-4 lg:grid-cols-1">
            <app-ds-kpi label="Eventos no período" [value]="eventosFiltrados().length" kind="tint" />
            <app-ds-kpi label="Confirmados" [value]="confirmados()" kind="tint" [supportTone]="confirmadosTone()" />
          </div>
          <app-ds-card>
            <h3 class="mb-3 font-display text-sm font-semibold text-ink">Feedback</h3>
            <div class="flex items-center justify-between text-sm">
              <span class="text-ink-soft">Marcados reais</span>
              <span class="font-semibold text-ink">{{ marcadosReais() }}</span>
            </div>
            <div class="mt-2.5 flex items-center justify-between text-sm">
              <span class="text-ink-soft">Falsos positivos</span>
              <span class="font-semibold text-ink">{{ falsosPositivos() }}</span>
            </div>
            <div class="mt-2.5 flex items-center justify-between text-sm">
              <span class="text-ink-soft">Ainda não avaliados</span>
              <span class="font-semibold text-ink">{{ naoAvaliados() }}</span>
            </div>
          </app-ds-card>
        </div>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HistoricoComponent {
  private readonly facade = inject(EventosFacade);
  private readonly comandosFacade = inject(ComandosFacade);
  private readonly pacientesFacade = inject(PacientesFacade);
  protected readonly dataHora = dataHora;
  protected readonly hora = hora;
  protected readonly periodos: Periodo[] = ['hoje', 'semana', 'mes'];
  protected readonly periodoLabel = PERIODO_LABEL;

  readonly loading = computed(() => this.facade.loading() || this.pacientesFacade.loading() || this.comandosFacade.loading());
  readonly periodo = signal<Periodo>('semana');

  protected rotuloDoComando(comandoId: string): string {
    return this.comandosFacade.items().find(c => c.id === comandoId)?.rotulo ?? comandoId;
  }
  protected urgenciaDoComando(comandoId: string): 'emergencia' | 'rotina' | undefined {
    return this.comandosFacade.items().find(c => c.id === comandoId)?.urgencia;
  }

  readonly eventosFiltrados = computed(() => {
    const p = this.pacientesFacade.items()[0];
    if (!p) return [];
    const limiteMs = PERIODO_DIAS[this.periodo()] * 24 * 60 * 60 * 1000;
    const referencia = agora().getTime();
    return this.facade.items()
      .filter(e => e.pacienteId === p.id && referencia - new Date(e.timestamp).getTime() <= limiteMs)
      .sort((a, b) => b.timestamp.localeCompare(a.timestamp));
  });

  readonly confirmados = computed(() => this.eventosFiltrados().filter(e => e.confirmadoEm != null).length);
  readonly confirmadosTone = computed<'up' | 'warn' | 'flat'>(() =>
    this.eventosFiltrados().length > 0 && this.confirmados() === this.eventosFiltrados().length ? 'up' : 'warn',
  );
  readonly marcadosReais = computed(() => this.eventosFiltrados().filter(e => e.feedback === 'real').length);
  readonly falsosPositivos = computed(() => this.eventosFiltrados().filter(e => e.feedback === 'falso_positivo').length);
  readonly naoAvaliados = computed(() => this.eventosFiltrados().filter(e => e.feedback === 'nao_avaliado').length);

  protected marcar(id: string, feedback: 'real' | 'falso_positivo'): void {
    this.facade.items.update(list => list.map(e => (e.id === id ? { ...e, feedback } : e)));
  }
}
