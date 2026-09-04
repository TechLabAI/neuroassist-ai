import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs/operators';
import { ProposalReviewFacade } from '../trei-proposal.facade';
import { DraftNodeVm, TreiLocale } from '../trei.types';
import { ApprovalSummaryBarComponent } from './approval-summary-bar.component';
import { ProposalListComponent } from './proposal-list.component';
import { ProposalEmptyStateComponent } from './proposal-empty-state.component';
import { ProposalEditDialogComponent } from './proposal-edit-dialog.component';
import { NodeActionEvent } from './proposed-node-card.component';

// =============================================================================
// ProposalReviewPageComponent — SPEC-02 §3, TREI-FE-018 (smart container)
// Route: /treinamento/sessao/propostas
//
// SMART container — reads signals from ProposalReviewFacade and delegates
// all rendering to presentational components.
//
// Signal sources consumed:
//   - facade.screenVm  → nodes | activeLocale | status | error
//
// G2 (deep-link): reads ?proposalId= query param via ActivatedRoute.
//   When proposalId is present but no nodes are loaded, attempts loadNode().
//   When no session is active, the empty-state component guides the caregiver back.
//
// INV-1 (ADR-007): confirmadoPaciente is NEVER referenced in this component.
//   Approval writes go only to facade.approve() (caregiver side).
//   The patient side is CONV's domain (Tela 09).
//
// User actions delegated to facade:
//   - onLocaleChange() → facade.setActiveLocale()
//   - onApprove()      → facade.approve(noId, locale)
//   - onReject()       → facade.discard(noId, locale)
//   - onEditSave()     → facade.materialize() (pre-approval text edit)
// =============================================================================

@Component({
  selector: 'trei-proposal-review-page',
  standalone: true,
  imports: [
    RouterLink,
    ApprovalSummaryBarComponent,
    ProposalListComponent,
    ProposalEmptyStateComponent,
    ProposalEditDialogComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-4xl p-6">

      <!-- Header + back navigation -->
      <div class="mb-6 flex items-center gap-3">
        <a
          routerLink="/treinamento/sessao"
          data-testid="back-to-session"
          class="flex items-center gap-1 text-sm text-ink-soft hover:text-ink">
          <span class="material-symbols-outlined text-[18px]" aria-hidden="true">arrow_back</span>
          Voltar à conversa
        </a>
        <div class="h-4 w-px bg-line" aria-hidden="true"></div>
        <h1 class="font-display text-xl font-bold text-ink">Propostas para aprovação</h1>
      </div>

      <!-- AI authoring affordance (TECH-007 / vision §4) -->
      <!-- P6: moved to TreiRootComponent so it appears on all TREI routes -->
      <div
        class="mb-4 flex items-center gap-2 rounded-xl border border-warning bg-warning-bg
               px-4 py-2.5 text-sm font-semibold text-warning-ink"
        data-testid="authoring-banner"
        aria-label="Esta é a superfície de autoria — não é o app do paciente">
        <span class="material-symbols-outlined text-[18px]" aria-hidden="true">psychology</span>
        <span>Treinamento — não é o app do paciente</span>
      </div>

      <!-- Locale tabs (ADR-018: locale-independent per-locale approval) -->
      <div class="mb-4 flex gap-2" role="tablist" aria-label="Idioma de autoria">
        @for (locale of locales; track locale) {
          <button
            role="tab"
            [attr.aria-selected]="vm().activeLocale === locale"
            [attr.data-testid]="'locale-tab-' + locale"
            (click)="onLocaleChange(locale)"
            class="rounded-lg px-4 py-1.5 text-sm font-semibold transition-colors"
            [class]="vm().activeLocale === locale
              ? 'bg-primary text-white'
              : 'bg-surface-2 text-ink-soft hover:text-ink'">
            {{ locale.toUpperCase() }}
          </button>
        }
      </div>

      <!-- ApprovalSummaryBar -->
      <div class="mb-4">
        <trei-approval-summary-bar
          [rascunhoCount]="rascunhoCount()"
          [aguardandoCount]="aguardandoCount()" />
      </div>

      <!-- G2: deep-link info when proposalId is in query params but nodes are not yet loaded -->
      @if (proposalId() && vm().status === 'idle') {
        <div
          class="mb-4 flex items-center gap-2 rounded-xl border border-info bg-info-bg
                 px-4 py-2.5 text-sm text-info-ink"
          data-testid="deeplink-loading-notice"
          role="status">
          <span
            class="h-4 w-4 animate-spin rounded-full border-2 border-info-ink border-t-transparent"
            aria-hidden="true"></span>
          A carregar proposta solicitada…
        </div>
      }

      <!-- Main content area -->
      @if (vm().status === 'loading') {
        <!-- Loading skeleton -->
        <div class="flex flex-col gap-3" aria-busy="true" aria-label="Carregando propostas">
          @for (_ of [1, 2, 3]; track $index) {
            <div class="h-24 animate-pulse rounded-2xl bg-surface-2" aria-hidden="true"></div>
          }
        </div>

      } @else if (vm().status === 'error') {
        <!-- Error state (G5: error never exposes correlationId in UI) -->
        <div
          role="alert"
          class="rounded-2xl border border-error bg-error-bg p-4 text-sm text-error-ink"
          data-testid="proposal-error">
          <p class="font-semibold">Erro ao carregar propostas</p>
          <p class="mt-1 text-xs">{{ vm().error?.message }}</p>
        </div>

      } @else if (vm().nodes.length === 0) {
        <!-- Empty state — no drafts for this locale -->
        <trei-proposal-empty-state />

      } @else {
        <!-- ProposalList with ProposedNodeCards (INV-1 propagated through component tree) -->
        <trei-proposal-list
          [nodes]="vm().nodes"
          [activeLocale]="vm().activeLocale"
          (approve)="onApprove($event)"
          (reject)="onReject($event)" />
      }

      <!-- ProposalEditDialog (controlled by editingNode signal) -->
      <trei-proposal-edit-dialog
        [draftText]="editingNodeText()"
        [isOpen]="editingNode() !== null"
        (save)="onEditSave($event)"
        (dismiss)="editingNode.set(null)" />

    </div>
  `,
})
export class ProposalReviewPageComponent implements OnInit {
  private readonly facade = inject(ProposalReviewFacade);
  private readonly route = inject(ActivatedRoute);

  protected readonly locales: TreiLocale[] = ['pt', 'en'];

  /** Assembled screen VM — single signal source for all template bindings. */
  protected readonly vm = this.facade.screenVm;

  /**
   * G2 (deep-link): reads ?proposalId= query parameter.
   * When present, the page attempts to load the referenced node automatically.
   */
  protected readonly proposalId = toSignal(
    this.route.queryParamMap.pipe(map((params) => params.get('proposalId'))),
    { initialValue: null },
  );

  /** State for ProposalEditDialog — the node currently being edited (null = closed). */
  protected readonly editingNode = signal<DraftNodeVm | null>(null);

  /** Derived text for the edit dialog from editingNode. */
  protected readonly editingNodeText = computed(() => this.editingNode()?.texto ?? '');

  /** Count of rascunho nodes (awaiting caregiver approval). */
  protected readonly rascunhoCount = computed(
    () => this.facade.nodes().filter((n) => n.subEstado === 'rascunho').length,
  );

  /** Count of nodes already approved by caregiver but awaiting patient confirmation. */
  protected readonly aguardandoCount = computed(
    () => this.facade.nodes().filter((n) => n.subEstado === 'aguardando_aprovacao').length,
  );

  ngOnInit(): void {
    // G2: if a proposalId is specified in the deep-link, auto-load that node.
    const pid = this.proposalId();
    if (pid && this.facade.nodes().length === 0) {
      void this.facade.loadNode(pid);
    }
  }

  protected onLocaleChange(locale: TreiLocale): void {
    this.facade.setActiveLocale(locale);
  }

  /** Approve caregiver side (INV-1: only caregiver side, never patient side). */
  protected onApprove(event: NodeActionEvent): void {
    void this.facade.approve(event.noId, event.locale);
  }

  /** Discard/reject a draft node. */
  protected onReject(event: NodeActionEvent): void {
    void this.facade.discard(event.noId, event.locale);
  }

  /** Open the edit dialog for a node (pre-approval text edit). */
  protected onEditOpen(node: DraftNodeVm): void {
    this.editingNode.set(node);
  }

  /** Save edited text — pre-approval text correction via materialize. */
  protected onEditSave(text: string): void {
    const node = this.editingNode();
    if (node == null) return;

    void this.facade.materialize({
      pacienteId: '',           // resolved by the mock adapter from session context
      propostaId: node.noId,
      parentNoId: 'n-root',
      tipo: node.tipo,
      locale: node.locale,
      texto: text,
      engineFeatureRef: null,
    });

    this.editingNode.set(null);
  }
}
