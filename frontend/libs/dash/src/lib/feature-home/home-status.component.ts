import {
  Component,
  ChangeDetectionStrategy,
  inject,
  computed,
  signal,
  OnInit,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashHomeFacade } from '../dash.facade';
import { LatestEventVm } from '../dash.types';

// =============================================================================
// HomeStatusComponent — Tela 01 (home.component promoted from design-reference)
// Route: / (DASH is the default landing — generator-config.yml shell_route: /)
// Wireframe ref: §5.1–5.5 — Home
// Objective: answer "Márcio está bem?" in < 2 seconds of reading (Princípio 2.1).
// Source: design-reference/angular-prototype/screens/home/home.component.ts
// Promoted: P3 of /fe-architect-promote neuroassist-ai DASH
// Data source: DashHomeFacade.getSummary() → GET /panel/summary (BFF composition)
// DS components: inline Tailwind — shared/ui-components not yet stabilized (INT-FE-DASH-003).
// TODO[INT-FE-DASH-001]: activeEvent signal injected from EVNT at scope:web.
// TODO[INT-FE-DASH-002]: deviceBadge signal injected from EDGE at scope:web.
// TODO[INT-FE-DASH-003]: extract StatusCard + DeviceBadge to @neuroassist/shared/ui-components.
// =============================================================================

function hora(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}h${String(d.getMinutes()).padStart(2, '0')}`;
}

function sincronizadoRelativo(iso: string): string {
  const diffMin = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if (diffMin < 1) return 'agora';
  if (diffMin < 60) return `há ${diffMin} min`;
  return `há ${Math.round(diffMin / 60)}h`;
}

@Component({
  selector: 'dash-home-status',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="mx-auto max-w-6xl p-8">
      @if (facade.loading()) {
        <div class="flex min-h-[50vh] items-center justify-center text-sm text-ink-faint">
          Carregando status…
        </div>
      } @else if (facade.status() === 'error') {
        <div class="flex min-h-[50vh] flex-col items-center justify-center gap-4 text-center">
          <p class="text-sm text-danger">{{ facade.error()?.message ?? 'Erro ao carregar painel.' }}</p>
          <button
            (click)="facade.retry()"
            class="rounded-lg bg-surface-2 px-4 py-2 text-sm font-semibold text-ink hover:bg-surface-3">
            Tentar novamente
          </button>
        </div>
      } @else if (!facade.patient()) {
        <div class="rounded-2xl border border-line bg-surface p-6">
          <p class="text-sm text-ink-soft">Nenhum paciente configurado ainda.</p>
          <a routerLink="/onboarding" class="mt-3 inline-block text-sm font-semibold text-brand">
            Iniciar configuração →
          </a>
        </div>
      } @else {
        <!-- Header -->
        <header class="mb-7 flex items-center justify-between">
          <div>
            <h1 class="font-display text-2xl font-bold tracking-tight text-ink">
              Olá, {{ firstCaregiverName() }}
            </h1>
            <p class="mt-1 text-sm text-ink-soft">
              Este é o status de {{ facade.patient()!.name }} agora.
            </p>
          </div>
          @if (facade.device(); as dev) {
            <span
              class="flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold"
              [class]="dev.status === 'online' ? 'bg-success-bg text-success-ink' : 'bg-danger-bg text-danger-ink'">
              <span class="material-symbols-outlined text-[16px]">
                {{ dev.status === 'online' ? 'wifi' : 'wifi_off' }}
              </span>
              Dispositivo {{ dev.status === 'online' ? 'online' : dev.status === 'offline' ? 'offline' : 'desconhecido' }}
            </span>
          }
        </header>

        <!-- IA advisory (inline card — sem painel lateral, wireframe §3.1) -->
        @if (sugestao() && !sugestaoDispensada()) {
          <div class="mb-6 rounded-2xl border border-line bg-ai-bg p-5">
            <div class="flex items-start gap-3">
              <span class="material-symbols-outlined mt-0.5 text-[20px] text-ai">auto_awesome</span>
              <div class="flex-1">
                <p class="text-sm font-semibold text-ai">Sugestão</p>
                <p class="mt-1 text-sm text-ink-soft">{{ sugestao() }}</p>
                <div class="mt-3 flex gap-2">
                  <button
                    (click)="dispensarSugestao()"
                    class="rounded-lg bg-ai-fill px-3 py-1.5 text-xs font-semibold text-ai-on">
                    Confirmar
                  </button>
                  <button
                    (click)="dispensarSugestao()"
                    class="rounded-lg border-[1.5px] border-line-strong px-3 py-1.5 text-xs font-semibold text-ink-soft hover:bg-surface-2">
                    Ignorar
                  </button>
                </div>
              </div>
            </div>
          </div>
        }

        <div class="grid grid-cols-1 gap-6 lg:grid-cols-[2fr_1fr]">
          <!-- Main column: StatusCard -->
          <div class="flex flex-col gap-6">
            <div class="rounded-2xl border border-line bg-surface p-6 shadow-card">
              <div class="flex items-center gap-6 py-4">
                @if (estaBem()) {
                  <div class="grid size-24 flex-shrink-0 place-items-center rounded-full bg-success-bg">
                    <span class="material-symbols-outlined text-[56px] text-success">check_circle</span>
                  </div>
                  <div>
                    <h2 class="font-display text-3xl font-bold text-ink">
                      {{ patientFirstName() }} está bem
                    </h2>
                    <p class="mt-2 text-sm text-ink-soft">
                      Última verificação: {{ ultimaHoraLabel() }}
                      @if (facade.device(); as dev) {
                        @if (dev.lastSeenAt) {
                          <span class="text-ink-faint">
                            · sincronizado {{ sincRelativo(dev.lastSeenAt) }}
                          </span>
                        }
                      }
                    </p>
                  </div>
                } @else if (isEmergencyPending()) {
                  <div class="grid size-24 flex-shrink-0 place-items-center rounded-full bg-warning-bg">
                    <span class="material-symbols-outlined text-[56px] text-warning">error</span>
                  </div>
                  <div>
                    <h2 class="font-display text-3xl font-bold text-ink">
                      Evento pendente de confirmação
                    </h2>
                    <p class="mt-2 text-sm text-ink-soft">
                      Última verificação: {{ ultimaHoraLabel() }}
                    </p>
                  </div>
                } @else {
                  <div class="grid size-24 flex-shrink-0 place-items-center rounded-full bg-info-bg">
                    <span class="material-symbols-outlined text-[56px] text-info">notifications</span>
                  </div>
                  <div>
                    <h2 class="font-display text-3xl font-bold text-ink">
                      {{ patientFirstName() }} pediu
                      @if (facade.latestEvent(); as ev) {
                        "{{ ev.commandLabel }}"
                      }
                    </h2>
                    <p class="mt-2 text-sm text-ink-soft">
                      Última verificação: {{ ultimaHoraLabel() }}
                    </p>
                  </div>
                }
              </div>
            </div>

            <!-- KPIs -->
            <div class="grid grid-cols-2 gap-4">
              <div class="rounded-2xl border border-line bg-surface p-5">
                <p class="text-xs font-medium text-ink-faint">Notificações pendentes</p>
                <p class="mt-1 font-display text-3xl font-bold text-ink">
                  {{ facade.unacknowledgedCount() }}
                </p>
              </div>
              <div class="rounded-2xl border border-line bg-surface p-5">
                <p class="text-xs font-medium text-ink-faint">Dispositivo</p>
                <p class="mt-1 font-display text-xl font-bold"
                   [class]="facade.device()?.status === 'online' ? 'text-success' : 'text-danger'">
                  {{ facade.device()?.status ?? '—' }}
                </p>
              </div>
            </div>

            <!-- Notification center link -->
            @if (facade.unacknowledgedCount() > 0) {
              <a routerLink="/notificacoes" class="self-start">
                <button class="flex items-center gap-2 rounded-xl border border-line-strong bg-surface px-4 py-2.5 text-sm font-semibold text-ink hover:bg-surface-2">
                  <span class="material-symbols-outlined text-[18px]">notifications</span>
                  Ver notificações ({{ facade.unacknowledgedCount() }})
                </button>
              </a>
            }

            <a routerLink="/historico" class="self-start">
              <button class="flex items-center gap-2 rounded-xl border border-line bg-surface px-4 py-2.5 text-sm font-semibold text-ink-soft hover:bg-surface-2">
                <span class="material-symbols-outlined text-[18px]">history</span>
                Ver histórico de hoje
              </button>
            </a>
          </div>

          <!-- Sidebar column: device + latest event -->
          <div class="flex flex-col gap-6">
            @if (facade.device(); as dev) {
              <div class="rounded-2xl border border-line bg-surface p-5">
                <h3 class="mb-3 font-display text-sm font-semibold text-ink">Dispositivo edge</h3>
                <div class="flex items-center justify-between text-sm">
                  <span class="text-ink-soft">Status</span>
                  <span
                    class="rounded-full px-2 py-0.5 text-xs font-semibold"
                    [class]="dev.status === 'online' ? 'bg-success-bg text-success-ink' : 'bg-danger-bg text-danger-ink'">
                    {{ dev.status }}
                  </span>
                </div>
                @if (dev.lastSeenAt) {
                  <div class="mt-2.5 flex items-center justify-between text-sm">
                    <span class="text-ink-soft">Sincronizado</span>
                    <span class="text-ink">{{ sincRelativo(dev.lastSeenAt) }}</span>
                  </div>
                }
                @if (dev.degraded) {
                  <p class="mt-2 text-xs text-warning">Dados de liveness degradados — EDGE indisponível.</p>
                }
              </div>
            }

            @if (facade.latestEvent(); as ev) {
              <div class="rounded-2xl border border-line bg-surface p-5">
                <h3 class="mb-3 font-display text-sm font-semibold text-ink">Último evento</h3>
                <div class="flex items-center justify-between text-sm">
                  <span class="text-ink-soft">"{{ ev.commandLabel }}"</span>
                  <span
                    class="rounded-full px-2 py-0.5 text-xs font-semibold"
                    [class]="ev.urgencia === 'emergencia' ? 'bg-danger-bg text-danger-ink' : 'bg-info-bg text-info-ink'">
                    {{ ev.urgencia }}
                  </span>
                </div>
                <div class="mt-2.5 flex items-center justify-between text-sm">
                  <span class="text-ink-soft">Horário</span>
                  <span class="text-ink">{{ horaLabel(ev.at) }}</span>
                </div>
                <div class="mt-2.5 flex items-center justify-between text-sm">
                  <span class="text-ink-soft">Revisão</span>
                  <span
                    class="rounded-full px-2 py-0.5 text-xs font-semibold"
                    [class]="ev.feedback === 'nao_avaliado' ? 'bg-warning-bg text-warning-ink' : 'bg-success-bg text-success-ink'">
                    {{ feedbackLabel(ev) }}
                  </span>
                </div>
              </div>
            }

            @if (facade.hasDegradedSections()) {
              <div class="rounded-2xl border border-warning-bg bg-warning-bg/50 p-4">
                <p class="text-xs font-medium text-warning">
                  Algumas informações podem estar desatualizadas (degradação parcial).
                </p>
              </div>
            }
          </div>
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeStatusComponent implements OnInit {
  protected readonly facade = inject(DashHomeFacade);

  // IA inline advisory — dismiss is client-side only (no audit, advisory-only).
  private readonly _sugestaoDispensada = signal(false);
  protected readonly sugestaoDispensada = this._sugestaoDispensada.asReadonly();

  protected readonly sugestao = computed<string | null>(() => {
    if (this._sugestaoDispensada()) return null;
    const count = this.facade.unacknowledgedCount();
    if (count >= 3) {
      return 'Há várias notificações pendentes. Considere revisar o catálogo de comandos na Calibração.';
    }
    return null;
  });

  /** True when there are no pending unacknowledged notifications (all clear). */
  protected readonly estaBem = computed<boolean>(
    () => this.facade.unacknowledgedCount() === 0 && this.facade.latestEvent()?.feedback !== 'nao_avaliado',
  );

  /** True when latest event is an emergency pending acknowledgement. */
  protected readonly isEmergencyPending = computed<boolean>(() => {
    const ev = this.facade.latestEvent();
    return ev !== null && ev.urgencia === 'emergencia' && ev.feedback === 'nao_avaliado';
  });

  protected readonly firstCaregiverName = computed<string>(() => {
    const name = this.facade.caregiverName();
    return name.split(' ')[0] ?? name;
  });

  protected readonly patientFirstName = computed<string>(() => {
    const name = this.facade.patient()?.name ?? '';
    return name.split(' ')[0] ?? name;
  });

  protected readonly ultimaHoraLabel = computed<string>(() => {
    const ev = this.facade.latestEvent();
    return ev ? hora(ev.at) : '—';
  });

  protected horaLabel(iso: string): string {
    return hora(iso);
  }

  protected sincRelativo(iso: string): string {
    return sincronizadoRelativo(iso);
  }

  protected feedbackLabel(ev: LatestEventVm): string {
    if (ev.feedback === 'nao_avaliado') return 'pendente';
    if (ev.feedback === 'real') return 'confirmado';
    return 'falso positivo';
  }

  protected dispensarSugestao(): void {
    this._sugestaoDispensada.set(true);
    // TODO[fe-autopilot]: registrar decisão (confirmar/ignorar) em auditoria — advisory nunca executa sozinha
  }

  ngOnInit(): void {
    this.facade.load();
  }
}
