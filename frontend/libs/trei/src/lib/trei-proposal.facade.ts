import {
  Injectable,
  inject,
  signal,
  computed,
} from '@angular/core';
import { lastValueFrom } from 'rxjs';
import { DRAFT_NODE_PORT } from './api/draft-node.port';
import { CAREGIVER_APPROVAL_PORT } from './api/caregiver-approval.port';
import {
  DraftNodeVm,
  TreiLocale,
  ProposalReviewScreenVm,
  AppError,
  MaterializeRequest,
  NodeDetail,
} from './trei.types';

// =============================================================================
// ProposalReviewFacade — SPEC-04 §3/§4 (draft node review + caregiver approval)
// Manages:
//   - draft node list (projected from session turns + NodeDetail inspections)
//   - caregiver approval (non-optimistic: state set only after backend confirms)
//   - discard (soft delete; ativo locale → 409, FE shows error)
//   - per-locale filtering (active locale from TrainingSessionFacade or local signal)
//
// INVARIANT (ADR-007 FE / INV-1): this facade ONLY writes confirmadoCuidadora.
// No method, property, or code path in this class touches confirmadoPaciente.
// Patient confirmation lives in CONV (Tela 09), a separate module.
// =============================================================================

function mapError(err: unknown, fallback: string): AppError {
  if (err instanceof Object && 'status' in err) {
    const e = err as { status: number; message?: string };
    return { code: `HTTP_${e.status}`, message: e.message ?? fallback, status: e.status };
  }
  if (err instanceof Error) {
    return { code: 'UNKNOWN', message: err.message };
  }
  return { code: 'UNKNOWN', message: fallback };
}

function toVm(detail: NodeDetail, locale: TreiLocale): DraftNodeVm | null {
  const localeData = detail.locales.find((l) => l.locale === locale);
  if (!localeData) return null;
  return {
    noId: detail.noId,
    locale,
    texto: localeData.texto ?? '(sem texto)',
    tipo: detail.tipo,
    subEstado: localeData.subEstado,
    confirmadoCuidadora: localeData.confirmadoCuidadora,
    confirmadoPaciente: localeData.confirmadoPaciente,
    approvalInProgress: false,
    discardInProgress: false,
  };
}

@Injectable({ providedIn: 'root' })
export class ProposalReviewFacade {
  private readonly draftNodePort = inject(DRAFT_NODE_PORT);
  private readonly approvalPort = inject(CAREGIVER_APPROVAL_PORT);

  // ---------------------------------------------------------------------------
  // Private signals
  // ---------------------------------------------------------------------------
  readonly #nodes = signal<DraftNodeVm[]>([]);
  readonly #activeLocale = signal<TreiLocale>('pt');
  readonly #status = signal<'idle' | 'loading' | 'loaded' | 'empty' | 'error'>('idle');
  readonly #error = signal<AppError | null>(null);

  // ---------------------------------------------------------------------------
  // Public readonly signals
  // ---------------------------------------------------------------------------
  readonly nodes = this.#nodes.asReadonly();
  readonly activeLocale = this.#activeLocale.asReadonly();
  readonly status = this.#status.asReadonly();
  readonly error = this.#error.asReadonly();

  readonly screenVm = computed<ProposalReviewScreenVm>(() => ({
    nodes: this.#nodes(),
    activeLocale: this.#activeLocale(),
    status: this.#status(),
    error: this.#error(),
  }));

  // ---------------------------------------------------------------------------
  // Public methods (SPEC-04 §4)
  // ---------------------------------------------------------------------------

  /**
   * loadNode(noId) — inspect a single node and add/update it in the list.
   * Used after materialize() succeeds, to display the full NodeDetail in the review panel.
   */
  async loadNode(noId: string): Promise<void> {
    try {
      const detail = await lastValueFrom(this.draftNodePort.inspect(noId));
      const locale = this.#activeLocale();
      const vm = toVm(detail, locale);
      if (!vm) return;

      this.#nodes.update((list) => {
        const idx = list.findIndex((n) => n.noId === noId);
        return idx >= 0
          ? list.map((n, i) => (i === idx ? vm : n))
          : [...list, vm];
      });

      this.#status.set(this.#nodes().length === 0 ? 'empty' : 'loaded');
    } catch (err) {
      this.#error.set(mapError(err, 'Erro ao carregar nó'));
    }
  }

  /**
   * materialize(request) — turn a proposal into a draft node, then display it.
   * Non-optimistic: node only appears after the backend confirms (POST /nos → 201).
   */
  async materialize(request: MaterializeRequest): Promise<string | null> {
    this.#status.set('loading');
    this.#error.set(null);

    try {
      const created = await lastValueFrom(this.draftNodePort.materialize(request));
      const vm: DraftNodeVm = {
        noId: created.noId,
        locale: created.locale,
        texto: request.texto,
        tipo: request.tipo,
        subEstado: created.subEstado,
        confirmadoCuidadora: created.confirmadoCuidadora,
        confirmadoPaciente: created.confirmadoPaciente,
        approvalInProgress: false,
        discardInProgress: false,
      };
      this.#nodes.update((list) => [...list, vm]);
      this.#status.set('loaded');
      return created.noId;
    } catch (err) {
      this.#error.set(mapError(err, 'Erro ao materializar proposta'));
      this.#status.set('error');
      return null;
    }
  }

  /**
   * approve(noId, locale) — write the CAREGIVER side of approval (INV-1).
   * Non-optimistic: confirmadoCuidadora set only after backend confirms.
   * Idempotent: re-approving an already-approved node is a no-op (SPEC-05).
   */
  async approve(noId: string, locale: TreiLocale): Promise<void> {
    this.#setApprovalProgress(noId, true);

    try {
      const response = await lastValueFrom(
        this.approvalPort.approve(noId, { locale }),
      );

      // Non-optimistic update: set from backend response
      this.#nodes.update((list) =>
        list.map((n) =>
          n.noId === noId && n.locale === locale
            ? {
                ...n,
                confirmadoCuidadora: response.confirmadoCuidadora,
                subEstado: response.subEstado,
                approvalInProgress: false,
              }
            : n,
        ),
      );
    } catch (err) {
      this.#setApprovalProgress(noId, false);
      this.#error.set(mapError(err, 'Erro ao aprovar nó'));
    }
  }

  /**
   * discard(noId, locale) — soft-delete a draft locale.
   * Cannot discard an ativo locale (409 → show error, keep the node).
   */
  async discard(noId: string, locale: TreiLocale): Promise<void> {
    this.#setDiscardProgress(noId, true);

    try {
      await lastValueFrom(this.draftNodePort.discard(noId, locale));

      // Remove from the visible list (descartado nodes are not shown)
      this.#nodes.update((list) =>
        list.filter((n) => !(n.noId === noId && n.locale === locale)),
      );

      if (this.#nodes().length === 0) {
        this.#status.set('empty');
      }
    } catch (err) {
      this.#setDiscardProgress(noId, false);
      this.#error.set(mapError(err, 'Não foi possível descartar — o nó pode já estar publicado'));
    }
  }

  /** setActiveLocale — switch locale filter (locale-independent per ADR-018). */
  setActiveLocale(locale: TreiLocale): void {
    this.#activeLocale.set(locale);
  }

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  #setApprovalProgress(noId: string, inProgress: boolean): void {
    this.#nodes.update((list) =>
      list.map((n) => (n.noId === noId ? { ...n, approvalInProgress: inProgress } : n)),
    );
  }

  #setDiscardProgress(noId: string, inProgress: boolean): void {
    this.#nodes.update((list) =>
      list.map((n) => (n.noId === noId ? { ...n, discardInProgress: inProgress } : n)),
    );
  }
}
