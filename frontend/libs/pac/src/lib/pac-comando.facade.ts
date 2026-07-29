import { Injectable, inject, signal, computed, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { lastValueFrom, from } from 'rxjs';
import {
  PAC_COMANDO_PORT,
  Comando,
  ComandoCreate,
  ComandoIdentityEdit,
  ComandoTuning,
  ComandoEstado,
  ComandoUrgencia,
  ComandoSensibilidade,
  SyncState,
} from './api/comando.port';
import { TipoGesto } from './api/tipos-gesto-read.port';
import { EdgeDeviceAckStubAdapter } from './api/edge-device-ack-stub.adapter';

/** Crypto-safe idempotency key (browser-native). */
function generateIdempotencyKey(): string {
  return typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2, 11)}`;
}

// ---------------------------------------------------------------------------
// ComandoRowVM — catalog list row (SPEC-03 / SPEC-04 §3)
// ---------------------------------------------------------------------------

export interface ComandoRowVM {
  readonly id: string;
  readonly rotulo: string;
  readonly urgencia: ComandoUrgencia;
  readonly primitivoPrincipalNome: string;
  readonly primitivoSecundarioNome: string | null;
  readonly sensibilidade: ComandoSensibilidade;
  readonly estado: ComandoEstado;
  /** ADR-005: never 'active-on-device' until EDGE ack (stub always returns 'pending-sync') */
  readonly syncState: SyncState;
  readonly testedState: 'not-tested' | 'tested';
  readonly etag: string;
}

// ---------------------------------------------------------------------------
// EditorVM — command editor form state
// ---------------------------------------------------------------------------

export interface EditorVM {
  readonly pid: string;
  readonly cid: string | null;           // null → new command
  readonly rotulo: string;
  readonly urgencia: ComandoUrgencia;
  readonly primitivoPrincipal: string;
  readonly primitivoSecundario: string | null;
  readonly sensibilidade: ComandoSensibilidade;
  readonly tempoMinimoGestoMs: number;
  readonly tempoMaximoGestoMs: number;
  readonly estado: ComandoEstado | null;
  readonly etag: string;
  readonly dirty: boolean;
  readonly saveState: 'idle' | 'saving' | 'saved-pending-sync' | 'error';
  /** Emergency gate error surfaced from BE 422 (PAC-FE-021) */
  readonly emergencyGateError: string | null;
}

export type CatalogLoadState = 'idle' | 'loading' | 'loaded' | 'error';

const INITIAL_EDITOR: EditorVM = {
  pid: '',
  cid: null,
  rotulo: '',
  urgencia: 'rotina',
  primitivoPrincipal: '',
  primitivoSecundario: null,
  sensibilidade: 'media',
  tempoMinimoGestoMs: 400,
  tempoMaximoGestoMs: 1500,
  estado: null,
  etag: '',
  dirty: false,
  saveState: 'idle',
  emergencyGateError: null,
};

// ---------------------------------------------------------------------------
// PacComandoFacade — SPEC-04 §3 (signals-first, ADR-007)
// Owns the command catalog and editor state. Facade never exposes ports or
// adapters — all consumers inject this class only.
// ---------------------------------------------------------------------------

@Injectable()
export class PacComandoFacade {
  private readonly port = inject(PAC_COMANDO_PORT);
  private readonly ackStub = inject(EdgeDeviceAckStubAdapter);
  private readonly destroyRef = inject(DestroyRef);

  private readonly _catalog = signal<ComandoRowVM[]>([]);
  private readonly _loadState = signal<CatalogLoadState>('idle');
  private readonly _editor = signal<EditorVM>(INITIAL_EDITOR);
  private readonly _loading = signal(false);
  private readonly _error = signal<string | null>(null);

  readonly catalog = this._catalog.asReadonly();
  readonly loadState = this._loadState.asReadonly();
  readonly editor = this._editor.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  readonly isEmpty = computed(() => this._catalog().length === 0 && this._loadState() === 'loaded');
  readonly isEditing = computed(() => this._editor().pid !== '');

  /** Load the command catalog for a patient. Default excludes retired. */
  load(pid: string, estado?: ComandoEstado[]): void {
    this._loadState.set('loading');
    this._loading.set(true);
    this._error.set(null);
    from(lastValueFrom(this.port.list(pid, estado)))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (comandos) => {
          this._catalog.set(comandos.map((c) => this._toRowVM(c, [])));
          this._loadState.set('loaded');
          this._loading.set(false);
        },
        error: (err: unknown) => {
          this._error.set(err instanceof Error ? err.message : 'Erro ao carregar catálogo');
          this._loadState.set('error');
          this._loading.set(false);
        },
      });
  }

  /** Prepare editor for a new command */
  openNew(pid: string, firstTipoId: string): void {
    this._editor.set({
      ...INITIAL_EDITOR,
      pid,
      primitivoPrincipal: firstTipoId,
    });
  }

  /** Prepare editor to edit an existing command (lifecycle-aware: published→recalibrate) */
  openEdit(pid: string, row: ComandoRowVM): void {
    this._editor.set({
      pid,
      cid: row.id,
      rotulo: row.rotulo,
      urgencia: row.urgencia,
      primitivoPrincipal: row.primitivoPrincipalNome,
      primitivoSecundario: row.primitivoSecundarioNome,
      sensibilidade: row.sensibilidade,
      tempoMinimoGestoMs: 400,
      tempoMaximoGestoMs: 1500,
      estado: row.estado,
      etag: row.etag,
      dirty: false,
      saveState: 'idle',
      emergencyGateError: null,
    });
  }

  cancelEdit(): void {
    this._editor.set(INITIAL_EDITOR);
  }

  /** Save: create (new) or updateDraft (existing drafted) → reload catalog.
   * ADR-005: after save, syncState stays 'pending-sync' (never 'active-on-device'). */
  save(): void {
    const ed = this._editor();
    if (!ed.pid) return;
    this._editor.update((e) => ({ ...e, saveState: 'saving', emergencyGateError: null }));

    if (!ed.cid) {
      // Create new draft
      const draft: ComandoCreate = {
        rotulo: ed.rotulo,
        urgencia: ed.urgencia,
        primitivoPrincipal: ed.primitivoPrincipal,
        primitivoSecundario: ed.primitivoSecundario,
        sensibilidade: ed.sensibilidade,
        tempoMinimoGestoMs: ed.tempoMinimoGestoMs,
        tempoMaximoGestoMs: ed.tempoMaximoGestoMs,
      };
      const key = generateIdempotencyKey();
      from(lastValueFrom(this.port.create(ed.pid, draft, key)))
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (resp) => {
            if (resp.body) {
              this._editor.update((e) => ({
                ...e,
                cid: resp.body!.id,
                estado: resp.body!.estado,
                etag: resp.body!.etag,
                saveState: 'saved-pending-sync',
                dirty: false,
              }));
            }
            this.load(ed.pid);
          },
          error: (err: unknown) => this._handleSaveError(err),
        });
    } else {
      // Update existing draft (only allowed while estado === 'drafted')
      const edit: ComandoIdentityEdit = {
        rotulo: ed.rotulo,
        urgencia: ed.urgencia,
        primitivoPrincipal: ed.primitivoPrincipal,
        primitivoSecundario: ed.primitivoSecundario,
        sensibilidade: ed.sensibilidade,
        tempoMinimoGestoMs: ed.tempoMinimoGestoMs,
        tempoMaximoGestoMs: ed.tempoMaximoGestoMs,
      };
      from(lastValueFrom(this.port.updateDraft(ed.pid, ed.cid, edit, ed.etag)))
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (updated) => {
            this._editor.update((e) => ({
              ...e,
              etag: updated.etag,
              estado: updated.estado,
              saveState: 'saved-pending-sync',
              dirty: false,
            }));
            this.load(ed.pid);
          },
          error: (err: unknown) => this._handleSaveError(err),
        });
    }
  }

  /** Recalibrate: for published commands, adjusts tuning without going back to drafted */
  recalibrate(tuning: ComandoTuning): void {
    const ed = this._editor();
    if (!ed.cid) return;
    from(lastValueFrom(this.port.recalibrate(ed.pid, ed.cid, tuning, ed.etag)))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this._editor.update((e) => ({
            ...e,
            sensibilidade: updated.sensibilidade,
            etag: updated.etag,
            saveState: 'saved-pending-sync',
          }));
          this.load(ed.pid);
        },
        error: (err: unknown) => this._handleSaveError(err),
      });
  }

  /** Retire: terminal transition — no DELETE (ADR-006) */
  retire(pid: string, row: ComandoRowVM): void {
    from(lastValueFrom(this.port.retire(pid, row.id, row.etag)))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.load(pid),
        error: (err: unknown) => this._error.set(err instanceof Error ? err.message : 'Erro ao aposentar comando'),
      });
  }

  /** Patch editor field — marks dirty */
  patchEditor(partial: Partial<Omit<EditorVM, 'pid' | 'cid' | 'etag' | 'saveState' | 'emergencyGateError'>>): void {
    this._editor.update((e) => ({ ...e, ...partial, dirty: true }));
  }

  /** Mark the current editor draft as tested (updates testedState on the catalog row) */
  markTested(cid: string): void {
    this._catalog.update((rows) =>
      rows.map((r) =>
        r.id === cid ? { ...r, testedState: 'tested' as const } : r,
      ),
    );
  }

  private _toRowVM(c: Comando, _tipos: TipoGesto[]): ComandoRowVM {
    return {
      id: c.id,
      rotulo: c.rotulo,
      urgencia: c.urgencia,
      // NOTE: in production, resolve names from TiposGestoReadFacade.getById()
      // For the mock, we store the id as the name until TipoGesto names are resolved
      primitivoPrincipalNome: c.primitivoPrincipal,
      primitivoSecundarioNome: c.primitivoSecundario,
      sensibilidade: c.sensibilidade,
      estado: c.estado,
      syncState: 'pending-sync',  // ADR-005: always pending until EDGE ack
      testedState: 'not-tested',
      etag: c.etag,
    };
  }

  private _handleSaveError(err: unknown): void {
    // Emergency gate (422) surfaces a blocking modal (PAC-FE-021)
    const msg = err instanceof Error ? err.message : 'Erro ao salvar comando';
    const isEmergencyGate = msg.includes('emergency-not-detectable') ||
      (err && typeof err === 'object' && 'status' in err && (err as {status: number}).status === 422);
    if (isEmergencyGate) {
      this._editor.update((e) => ({
        ...e,
        saveState: 'error',
        emergencyGateError: 'Este comando de emergência requer um gesto reconhecível pelo motor de detecção. Verifique o catálogo de gestos com a equipe técnica.',
      }));
    } else {
      this._editor.update((e) => ({ ...e, saveState: 'error', emergencyGateError: null }));
      this._error.set(msg);
    }
  }
}
