import { Injectable, inject, signal, computed, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { from, lastValueFrom } from 'rxjs';
import { TIPOS_GESTO_READ_PORT, TipoGesto } from './api/tipos-gesto-read.port';

// ---------------------------------------------------------------------------
// TipoGestoVM — flat view model (same shape as TipoGesto for simplicity)
// ---------------------------------------------------------------------------

export interface TipoGestoVM {
  readonly id: string;
  readonly nome: string;
}

// ---------------------------------------------------------------------------
// TiposGestoReadFacade — SPEC-04 §3 (cross-module read from GEST via GWY)
// Degradation: if GEST unavailable, list stays empty with error; UI shows
// "tipos de gesto indisponíveis" hint and allows manual name entry.
// ---------------------------------------------------------------------------

@Injectable()
export class TiposGestoReadFacade {
  private readonly port = inject(TIPOS_GESTO_READ_PORT);
  private readonly destroyRef = inject(DestroyRef);

  private readonly _tipos = signal<TipoGestoVM[]>([]);
  private readonly _loading = signal(false);
  private readonly _error = signal<string | null>(null);

  readonly tipos = this._tipos.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  /** Convenience: resolve a tipo name by id (returns id as fallback when not found) */
  readonly getById = computed(
    () =>
      (id: string): string =>
        this._tipos().find((t) => t.id === id)?.nome ?? id,
  );

  /** First available tipo id (useful as default for new command form) */
  readonly firstTipoId = computed(() => this._tipos()[0]?.id ?? '');

  load(): void {
    this._loading.set(true);
    this._error.set(null);
    from(lastValueFrom(this.port.list()))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (tipos) => {
          this._tipos.set(tipos);
          this._loading.set(false);
        },
        error: () => {
          // Graceful degradation: log but don't block the UI (SPEC-07)
          this._error.set('Tipos de gesto indisponíveis — a lista de primitivos não pôde ser carregada. A entrada manual é permitida.');
          this._loading.set(false);
        },
      });
  }

  /** Delegate a new-type request to GEST (naming only — detectability is GEST's decision).
   * On success, appends the new tipo to the local list and returns its id. */
  addNew(nome: string): void {
    from(lastValueFrom(this.port.requestNew(nome)))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (novo) => {
          this._tipos.update((list) => {
            const already = list.find((t) => t.id === novo.id);
            return already ? list : [...list, novo];
          });
        },
        error: () => {
          this._error.set('Não foi possível cadastrar o novo tipo de gesto. Tente novamente.');
        },
      });
  }

  /** Find the id of a requestNew call result for immediate selection after addNew() */
  findByNome(nome: string): string | null {
    return this._tipos().find((t) => t.nome === nome)?.id ?? null;
  }

  getNameById(id: string): string {
    return this._tipos().find((t) => t.id === id)?.nome ?? id;
  }
}
