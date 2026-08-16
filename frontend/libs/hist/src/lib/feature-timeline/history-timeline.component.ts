import {
  Component,
  ChangeDetectionStrategy,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { HistoryFacade } from '../hist.facade';
import { Period, Verdict } from '../hist.types';

// =============================================================================
// HistoryTimelineComponent — SPEC-02 (component tree root / smart component)
// Route: /historico (SPEC-01)
// Promoted from: frontend/design-reference/angular-prototype/apps/
//   neuro-assist-ai-web/src/app/screens/historico/historico.component.ts
//
// Architectural changes vs prototype:
//  - EventosFacade / ComandosFacade / PacientesFacade → HistoryFacade (single port)
//  - rotulo resolved by the facade DTO→VM mapping (no facade join here)
//  - urgency resolved by the facade DTO→VM mapping
//  - Non-optimistic feedback: marcar() delegates to facade.markFeedback() (no local signal.update)
//  - Loading/empty/error states from facade.screenVm (SPEC-03)
//  - Tailwind preserved verbatim from prototype
// =============================================================================

type PeriodLabel = { [K in Period]: string };
const PERIODO_LABEL: PeriodLabel = { hoje: 'Hoje', semana: 'Semana', mes: 'Mês' };

@Component({
  selector: 'hist-history-timeline',
  standalone: true,
  imports: [],
  template: `
    <div class="mx-auto max-w-6xl p-8">
      <header class="mb-7 flex items-center justify-between">
        <div>
          <h1 class="font-display text-2xl font-bold tracking-tight text-ink">Histórico</h1>
          <p class="mt-1 text-sm text-ink-soft">
            Eventos registrados — trilha imutável de auditoria.
          </p>
        </div>

        <!-- Period filter tabs (FilterTabsComponent inline) -->
        <div class="inline-flex rounded-xl bg-surface-2 p-1" role="tablist" aria-label="Período">
          @for (p of periodos; track p) {
            <button
              role="tab"
              [attr.aria-selected]="vm().period === p"
              (click)="setPeriod(p)"
              class="rounded-lg px-4 py-1.5 text-sm font-semibold transition-colors"
              [class]="
                vm().period === p
                  ? 'bg-surface text-ink shadow-card'
                  : 'text-ink-soft hover:text-ink'
              ">
              {{ periodoLabel[p] }}
            </button>
          }
        </div>
      </header>

      <div class="grid grid-cols-1 gap-6 lg:grid-cols-[2fr_1fr]">
        <!-- Column main: event list (ListSkeleton / EventListItem / EmptyState / InlineError) -->
        <div>
          @if (vm().status === 'loading' && vm().items.length === 0) {
            <!-- ListSkeleton -->
            <div aria-busy="true" aria-label="Carregando histórico" class="flex flex-col gap-3">
              @for (_ of skeletonRows; track $index) {
                <div class="h-16 animate-pulse rounded-2xl bg-surface-2"></div>
              }
            </div>
          } @else if (vm().status === 'error') {
            <!-- InlineError -->
            <div
              role="alert"
              class="rounded-2xl border border-error-line bg-error-surface p-4 text-sm text-error-ink">
              <p class="font-semibold">Erro ao carregar histórico</p>
              @if (vm().error?.correlationId) {
                <p class="mt-1 text-xs text-error-ink/70">
                  Código: {{ vm().error!.correlationId }}
                </p>
              }
              <button
                (click)="retry()"
                class="mt-3 rounded-lg border border-error-line px-3 py-1.5 text-xs font-semibold text-error-ink transition-colors hover:bg-error-surface-2">
                Tentar novamente
              </button>
            </div>
          } @else if (vm().status === 'empty') {
            <!-- EmptyState -->
            <div class="rounded-2xl border border-line bg-surface p-6 text-center">
              <p class="text-sm text-ink-soft">Nenhum evento neste período.</p>
            </div>
          } @else {
            <!-- EventListItem rows -->
            <div class="flex flex-col gap-3">
              @for (item of vm().items; track item.id) {
                <div
                  class="rounded-2xl border border-line bg-surface p-4 shadow-card"
                  [attr.aria-label]="item.label">
                  <div class="flex items-start justify-between gap-4">
                    <div>
                      <p class="text-sm font-semibold text-ink">
                        <!-- Human-readable label (Comando.rotulo) — never factType code (SPEC-03) -->
                        "{{ item.label }}"

                        <!-- UrgencyBadge inline -->
                        <span
                          class="ml-1.5 inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold"
                          [class]="urgencyClass(item.urgency)">
                          {{ item.urgency }}
                        </span>

                        <span class="font-normal text-ink-faint">
                          — {{ item.occurredAtLabel }}
                        </span>
                      </p>
                      @if (item.confirmationLine) {
                        <p class="mt-1 text-xs text-ink-soft">{{ item.confirmationLine }}</p>
                      } @else {
                        <p class="mt-1 text-xs text-warning-ink">Ainda não confirmado</p>
                      }
                      @if (item.correctsRecordId) {
                        <p class="mt-1 text-xs text-ink-faint">
                          Corrige registro anterior (ADR-004)
                        </p>
                      }
                    </div>

                    <!-- FeedbackAction (non-optimistic — SPEC-03 §4) -->
                    @if (markingState(item.id) === 'marking') {
                      <!-- per-item progress indicator while awaiting backend -->
                      <span
                        aria-live="polite"
                        class="flex-shrink-0 text-xs text-ink-soft">
                        Aguardando…
                      </span>
                    } @else if (item.verdict === null && markingState(item.id) === 'idle') {
                      <button
                        (click)="marcar(item.id, 'real')"
                        class="flex-shrink-0 rounded-lg border-[1.5px] border-line-strong px-3 py-1.5 text-xs font-semibold text-ink-soft transition-colors hover:bg-surface-2">
                        Foi real?
                      </button>
                    } @else if (item.verdict === 'real' || markingState(item.id) === 'marked') {
                      <span
                        class="inline-flex items-center rounded-full bg-ok-surface px-2.5 py-1 text-xs font-semibold text-ok-ink">
                        ✓ Marcado real
                      </span>
                    } @else if (item.verdict === 'falso_positivo') {
                      <span
                        class="inline-flex items-center rounded-full bg-mu-surface px-2.5 py-1 text-xs font-semibold text-mu-ink">
                        Falso positivo
                      </span>
                    } @else if (markingState(item.id) === 'mark-error') {
                      <button
                        (click)="marcar(item.id, 'real')"
                        class="flex-shrink-0 rounded-lg border-[1.5px] border-error-line px-3 py-1.5 text-xs font-semibold text-error-ink transition-colors hover:bg-error-surface">
                        Erro. Tentar de novo?
                      </button>
                    }
                  </div>
                </div>
              }
            </div>

            <!-- LoadMore (SPEC-03 §4 / SPEC-04 §1) -->
            @if (vm().hasMore) {
              <div class="mt-4 flex justify-center">
                <button
                  (click)="loadMore()"
                  [disabled]="vm().status === 'loading'"
                  class="rounded-xl border border-line px-5 py-2 text-sm font-semibold text-ink-soft transition-colors hover:bg-surface-2 disabled:opacity-50">
                  @if (vm().status === 'loading') {
                    Carregando…
                  } @else {
                    Carregar mais
                  }
                </button>
              </div>
            }
          }
        </div>

        <!-- Column lateral: summary -->
        <div class="flex flex-col gap-6">
          <div class="grid grid-cols-2 gap-4 lg:grid-cols-1">
            <div class="rounded-2xl border border-line bg-surface p-4 shadow-card">
              <p class="text-xs text-ink-soft">Eventos no período</p>
              <p class="mt-1 text-2xl font-bold text-ink">{{ vm().items.length }}</p>
            </div>
            <div class="rounded-2xl border border-line bg-surface p-4 shadow-card">
              <p class="text-xs text-ink-soft">Confirmados</p>
              <p class="mt-1 text-2xl font-bold text-ink">{{ confirmadosCount() }}</p>
            </div>
          </div>

          <div class="rounded-2xl border border-line bg-surface p-4 shadow-card">
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
          </div>
        </div>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HistoryTimelineComponent implements OnInit {
  private readonly facade = inject(HistoryFacade);

  protected readonly periodos: Period[] = ['hoje', 'semana', 'mes'];
  protected readonly periodoLabel = PERIODO_LABEL;
  protected readonly skeletonRows = Array(5).fill(null);

  /** Assembled screen VM from facade. */
  readonly vm = this.facade.screenVm;

  ngOnInit(): void {
    this.facade.load('semana');
  }

  protected setPeriod(period: Period): void {
    this.facade.setPeriod(period);
  }

  protected loadMore(): void {
    this.facade.loadMore();
  }

  protected retry(): void {
    this.facade.retry();
  }

  /**
   * Non-optimistic feedback: delegates entirely to facade.
   * Facade sets verdict only after backend confirms (SPEC-03 §4).
   */
  protected marcar(id: string, verdict: Verdict): void {
    this.facade.markFeedback(id, verdict);
  }

  /** Get per-item marking state for the template. */
  protected markingState(id: string): string {
    return this.facade.markings().get(id)?.state ?? 'idle';
  }

  protected urgencyClass(urgency: string): string {
    switch (urgency) {
      case 'critico':   return 'bg-er-surface text-er-ink';
      case 'atencao':   return 'bg-warning-surface text-warning-ink';
      default:          return 'bg-nf-surface text-nf-ink';
    }
  }

  protected confirmadosCount(): number {
    return this.vm().items.filter((i) => i.confirmationLine).length;
  }

  protected marcadosReais(): number {
    return this.vm().items.filter((i) => i.verdict === 'real').length;
  }

  protected falsosPositivos(): number {
    return this.vm().items.filter((i) => i.verdict === 'falso_positivo').length;
  }

  protected naoAvaliados(): number {
    return this.vm().items.filter((i) => i.verdict === null).length;
  }
}
