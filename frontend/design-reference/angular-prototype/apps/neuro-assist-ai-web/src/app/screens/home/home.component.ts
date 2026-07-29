import { Component, ChangeDetectionStrategy, inject, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PacientesFacade, EventosFacade, DispositivosFacade, ComandosFacade } from '../../core/data-access';
import { agora } from '../../shared/clock';
import { DsCardComponent } from '../../ui/ds-card.component';
import { DsButtonComponent } from '../../ui/ds-button.component';
import { DsKpiComponent } from '../../ui/ds-kpi.component';
import { DsBadgeComponent } from '../../ui/ds-badge.component';

// Contrato: .build-plans/home.plan.md  (o implementer lê SÓ o build-plan)
// Wireframe ref: §5.1–5.5 — Home
// Objetivo: responder, em até 2 segundos de leitura, "Márcio está bem?" (Princípio 2.1).
// (Emenda 1) mostra qual comando do catálogo foi disparado, não só um estado binário.
// IA inline: sem painel lateral (wireframe §3.1) — sugestão advisory aparece como card
// dentro da própria tela, com Confirmar/Ignorar, nunca executa sozinha.

function hora(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}h${String(d.getMinutes()).padStart(2, '0')}`;
}
function dataHora(iso: string): string {
  const d = new Date(iso);
  const dd = String(d.getDate()).padStart(2, '0');
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  return `${dd}/${mm} às ${String(d.getHours()).padStart(2, '0')}h${String(d.getMinutes()).padStart(2, '0')}`;
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, DsCardComponent, DsButtonComponent, DsKpiComponent, DsBadgeComponent],
  template: `
    <div class="mx-auto max-w-6xl p-8">
      @if (loading()) {
        <div class="flex min-h-[50vh] items-center justify-center text-sm text-ink-faint">Carregando status…</div>
      } @else if (!paciente()) {
        <app-ds-card>
          <p class="text-sm text-ink-soft">Nenhum paciente configurado ainda.</p>
          <a routerLink="/onboarding" class="mt-3 inline-block text-sm font-semibold text-brand">Iniciar configuração →</a>
        </app-ds-card>
      } @else {
        <header class="mb-7 flex items-center justify-between">
          <div>
            <h1 class="font-display text-2xl font-bold tracking-tight text-ink">Olá, Jamilly</h1>
            <p class="mt-1 text-sm text-ink-soft">Este é o status de {{ paciente()!.nome }} agora.</p>
          </div>
          @if (dispositivo(); as dev) {
            <span class="flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold"
                  [class]="dev.status === 'online' ? 'bg-success-bg text-success-ink' : 'bg-danger-bg text-danger-ink'">
              <span class="material-symbols-outlined text-[16px]">{{ dev.status === 'online' ? 'wifi' : 'wifi_off' }}</span>
              Dispositivo {{ dev.status === 'online' ? 'online' : 'offline' }}
            </span>
          }
        </header>

        @if (sugestao(); as s) {
          <app-ds-card class="mb-6 block !bg-ai-bg">
            <div class="flex items-start gap-3">
              <span class="material-symbols-outlined mt-0.5 text-[20px] text-ai">auto_awesome</span>
              <div class="flex-1">
                <p class="text-sm font-semibold text-ai">Sugestão</p>
                <p class="mt-1 text-sm text-ink-soft">{{ s }}</p>
                <div class="mt-3 flex gap-2">
                  <button (click)="dispensarSugestao()" class="rounded-lg bg-ai-fill px-3 py-1.5 text-xs font-semibold text-ai-on">Confirmar</button>
                  <button (click)="dispensarSugestao()" class="rounded-lg border-[1.5px] border-line-strong px-3 py-1.5 text-xs font-semibold text-ink-soft hover:bg-surface-2">Ignorar</button>
                </div>
              </div>
            </div>
          </app-ds-card>
        }

        <div class="grid grid-cols-1 gap-6 lg:grid-cols-[2fr_1fr]">
          <!-- Coluna principal: StatusCard + KPIs do dia -->
          <div class="flex flex-col gap-6">
            <app-ds-card variant="elevated">
              <div class="flex items-center gap-6 py-4">
                @if (estaBem()) {
                  <div class="grid size-24 flex-shrink-0 place-items-center rounded-full bg-success-bg">
                    <span class="material-symbols-outlined text-[56px] text-success">check_circle</span>
                  </div>
                  <div>
                    <h2 class="font-display text-3xl font-bold text-ink">{{ paciente()!.nome.split(' ')[0] }} está bem</h2>
                    <p class="mt-2 text-sm text-ink-soft">
                      Última verificação: {{ ultimaHora() }}
                      @if (dispositivo(); as dev) {
                        <span class="text-ink-faint"> · sincronizado {{ sincronizadoRelativo(dev.ultimaSincronizacao) }}</span>
                      }
                    </p>
                  </div>
                } @else if (ultimoEmergenciaPendente()) {
                  <div class="grid size-24 flex-shrink-0 place-items-center rounded-full bg-warning-bg">
                    <span class="material-symbols-outlined text-[56px] text-warning">error</span>
                  </div>
                  <div>
                    <h2 class="font-display text-3xl font-bold text-ink">Evento pendente de confirmação</h2>
                    <p class="mt-2 text-sm text-ink-soft">Última verificação: {{ ultimaHora() }}</p>
                  </div>
                } @else {
                  <div class="grid size-24 flex-shrink-0 place-items-center rounded-full bg-info-bg">
                    <span class="material-symbols-outlined text-[56px] text-info">notifications</span>
                  </div>
                  <div>
                    <h2 class="font-display text-3xl font-bold text-ink">
                      {{ paciente()!.nome.split(' ')[0] }} pediu "{{ ultimoComandoRotulo() }}"
                    </h2>
                    <p class="mt-2 text-sm text-ink-soft">Última verificação: {{ ultimaHora() }}</p>
                  </div>
                }
              </div>
            </app-ds-card>

            <div class="grid grid-cols-2 gap-4">
              <app-ds-kpi label="Eventos hoje" [value]="eventosDeHoje().length" kind="tint" />
              <app-ds-kpi label="Confirmados" [value]="confirmadosHoje()" kind="tint" [supportTone]="confirmadosTone()" [delta]="confirmadosDelta()" />
            </div>

            <a routerLink="/historico" class="self-start">
              <app-ds-button variant="secondary">
                <span class="material-symbols-outlined text-[18px]">history</span>
                Ver histórico de hoje
              </app-ds-button>
            </a>
          </div>

          <!-- Coluna lateral: dispositivo + catálogo -->
          <div class="flex flex-col gap-6">
            @if (dispositivo(); as dev) {
              <app-ds-card>
                <h3 class="mb-3 font-display text-sm font-semibold text-ink">Dispositivo edge</h3>
                <div class="flex items-center justify-between text-sm">
                  <span class="text-ink-soft">Status</span>
                  <app-ds-badge [tone]="dev.status === 'online' ? 'ok' : 'er'">{{ dev.status }}</app-ds-badge>
                </div>
                <div class="mt-2.5 flex items-center justify-between text-sm">
                  <span class="text-ink-soft">Versão</span>
                  <span class="text-ink">{{ dev.versao }}</span>
                </div>
                <div class="mt-2.5 flex items-center justify-between text-sm">
                  <span class="text-ink-soft">Sincronizado</span>
                  <span class="text-ink">{{ sincronizadoRelativo(dev.ultimaSincronizacao) }}</span>
                </div>
              </app-ds-card>
            }

            <app-ds-card>
              <h3 class="mb-3 font-display text-sm font-semibold text-ink">Catálogo de comandos</h3>
              <div class="flex flex-col gap-2">
                @for (c of comandos(); track c.id) {
                  <div class="flex items-center justify-between text-sm">
                    <span class="text-ink-soft">"{{ c.rotulo }}"</span>
                    <app-ds-badge [tone]="c.urgencia === 'emergencia' ? 'er' : 'nf'">{{ c.urgencia }}</app-ds-badge>
                  </div>
                }
              </div>
              <a routerLink="/calibracao" class="mt-3 inline-block text-xs font-semibold text-brand hover:text-brand-strong">Gerenciar catálogo →</a>
            </app-ds-card>
          </div>
        </div>

        <!-- Preview de eventos recentes -->
        <app-ds-card class="mt-6 block">
          <div class="mb-3 flex items-center justify-between">
            <h3 class="font-display text-sm font-semibold text-ink">Eventos recentes</h3>
            <a routerLink="/historico" class="text-xs font-semibold text-brand hover:text-brand-strong">Ver todos →</a>
          </div>
          @if (eventosRecentes().length === 0) {
            <p class="text-sm text-ink-soft">Nenhum evento registrado ainda.</p>
          } @else {
            <div class="flex flex-col divide-y divide-line">
              @for (evt of eventosRecentes(); track evt.id) {
                <div class="flex items-center justify-between py-2.5 text-sm">
                  <span class="text-ink-soft">
                    "{{ rotuloDoComando(evt.comando) }}"
                    <span class="text-ink-faint">— {{ dataHora(evt.timestamp) }}</span>
                  </span>
                  @if (evt.confirmadoEm) {
                    <app-ds-badge tone="ok">✓ confirmado</app-ds-badge>
                  } @else {
                    <app-ds-badge tone="wn">pendente</app-ds-badge>
                  }
                </div>
              }
            </div>
          }
        </app-ds-card>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  private readonly pacientesFacade = inject(PacientesFacade);
  private readonly eventosFacade = inject(EventosFacade);
  private readonly dispositivosFacade = inject(DispositivosFacade);
  private readonly comandosFacade = inject(ComandosFacade);

  protected readonly dataHora = dataHora;

  readonly loading = computed(() => this.pacientesFacade.loading() || this.eventosFacade.loading() || this.dispositivosFacade.loading() || this.comandosFacade.loading());
  readonly paciente = computed(() => this.pacientesFacade.items()[0] ?? null);
  readonly dispositivo = computed(() => this.dispositivosFacade.items().find(d => d.id === this.paciente()?.dispositivo) ?? null);
  readonly comandos = computed(() => this.comandosFacade.items());

  protected rotuloDoComando(comandoId: string): string {
    return this.comandos().find(c => c.id === comandoId)?.rotulo ?? comandoId;
  }
  private urgenciaDoComando(comandoId: string): 'emergencia' | 'rotina' | undefined {
    return this.comandos().find(c => c.id === comandoId)?.urgencia;
  }

  readonly eventosDeHoje = computed(() => {
    const p = this.paciente();
    if (!p) return [];
    const hoje = agora().toISOString().slice(0, 10);
    return this.eventosFacade.items()
      .filter(e => e.pacienteId === p.id && e.timestamp.slice(0, 10) === hoje)
      .sort((a, b) => b.timestamp.localeCompare(a.timestamp));
  });

  readonly confirmadosHoje = computed(() => this.eventosDeHoje().filter(e => e.confirmadoEm != null).length);

  readonly confirmadosDelta = computed(() => {
    const total = this.eventosDeHoje().length;
    if (total === 0) return null;
    const pendentes = total - this.confirmadosHoje();
    return pendentes === 0 ? 'todos revisados' : `${pendentes} pendente${pendentes > 1 ? 's' : ''}`;
  });
  readonly confirmadosTone = computed<'up' | 'warn' | 'flat'>(() =>
    this.eventosDeHoje().length > 0 && this.confirmadosHoje() === this.eventosDeHoje().length ? 'up' : 'warn',
  );

  readonly eventosRecentes = computed(() => {
    const p = this.paciente();
    if (!p) return [];
    return this.eventosFacade.items()
      .filter(e => e.pacienteId === p.id)
      .sort((a, b) => b.timestamp.localeCompare(a.timestamp))
      .slice(0, 4);
  });

  readonly estaBem = computed(() => this.eventosDeHoje().every(e => e.confirmadoEm != null));

  /** Emergência sem confirmação — não deveria acontecer (Tela 02 assume a tela antes),
   * mas serve de guarda caso a cuidadora chegue no Home antes do redirecionamento. */
  readonly ultimoEmergenciaPendente = computed(() =>
    this.eventosDeHoje().find(e => !e.confirmadoEm && this.urgenciaDoComando(e.comando) === 'emergencia') ?? null,
  );

  readonly ultimoComandoRotulo = computed(() => {
    const ultimo = this.eventosDeHoje()[0];
    return ultimo ? this.rotuloDoComando(ultimo.comando) : '';
  });

  readonly ultimaHora = computed(() => {
    const ultimo = this.eventosDeHoje()[0];
    return ultimo ? hora(ultimo.confirmadoEm ?? ultimo.timestamp) : '—';
  });

  protected sincronizadoRelativo(iso: string): string {
    const diffMin = Math.round((agora().getTime() - new Date(iso).getTime()) / 60000);
    if (diffMin < 1) return 'agora';
    if (diffMin < 60) return `há ${diffMin} min`;
    return `há ${Math.round(diffMin / 60)}h`;
  }

  // IA inline (advisory) — sem painel lateral (wireframe §3.1): card no próprio Home.
  private readonly sugestaoDispensada = signal(false);
  readonly sugestao = computed(() => {
    if (this.sugestaoDispensada()) return null;
    const seteDiasMs = 7 * 24 * 60 * 60 * 1000;
    const referencia = agora().getTime();
    const eventosAguaSemana = this.eventosFacade.items()
      .filter(e => e.comando === 'cmd-agua' && referencia - new Date(e.timestamp).getTime() <= seteDiasMs);
    if (eventosAguaSemana.length >= 3) {
      return 'O comando "água" foi disparado várias vezes esta semana. Considere revisar a sensibilidade dele na Calibração.';
    }
    return null;
  });
  protected dispensarSugestao(): void {
    this.sugestaoDispensada.set(true);
    // TODO[fe-autopilot]: registrar decisão (confirmar/ignorar) em auditoria — advisory nunca executa sozinha
  }
}
