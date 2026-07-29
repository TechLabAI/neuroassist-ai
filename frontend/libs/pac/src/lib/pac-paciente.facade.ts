import { Injectable, inject, signal, computed, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { lastValueFrom, from } from 'rxjs';
import { PAC_PACIENTE_PORT, Paciente, PacienteCreate, PacienteUpdate } from './api/paciente.port';

/** Crypto-safe idempotency key (browser-native — no uuid package required). */
function generateIdempotencyKey(): string {
  return typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2, 11)}`;
}

// ---------------------------------------------------------------------------
// PacienteVM — caregiver-facing view model derived from Paciente
// ---------------------------------------------------------------------------

export interface PacienteVM {
  readonly id: string;
  readonly nome: string;
  readonly tenantId: string;
  readonly dispositivo: string | null;
}

export type PatientSaveState = 'idle' | 'saving' | 'saved' | 'error';

// ---------------------------------------------------------------------------
// PacPacienteFacade — SPEC-04 §3
// Responsible for: loading the active patient, creating and updating Paciente.
// Uses Signals (ADR-007 / FE 04 §6).
// ---------------------------------------------------------------------------

@Injectable()
export class PacPacienteFacade {
  private readonly port = inject(PAC_PACIENTE_PORT);
  private readonly destroyRef = inject(DestroyRef);

  private readonly _patient = signal<PacienteVM | null>(null);
  private readonly _saveState = signal<PatientSaveState>('idle');
  private readonly _loading = signal(false);
  private readonly _error = signal<string | null>(null);

  readonly patient = this._patient.asReadonly();
  readonly saveState = this._saveState.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  /** Derived: true if there is a registered patient for the current empresa */
  readonly hasPatient = computed(() => this._patient() !== null);

  loadCurrent(): void {
    this._loading.set(true);
    this._error.set(null);
    from(lastValueFrom(this.port.list(0, 1)))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          const first = page.content[0] ?? null;
          this._patient.set(first ? this._toVM(first) : null);
          this._loading.set(false);
        },
        error: (err: unknown) => {
          this._error.set(err instanceof Error ? err.message : 'Erro ao carregar paciente');
          this._loading.set(false);
        },
      });
  }

  save(dto: PacienteCreate): void {
    this._saveState.set('saving');
    const key = generateIdempotencyKey();
    from(lastValueFrom(this.port.create(dto, key)))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (resp) => {
          if (resp.body) this._patient.set(this._toVM(resp.body));
          this._saveState.set('saved');
        },
        error: () => {
          this._saveState.set('error');
        },
      });
  }

  update(id: string, dto: PacienteUpdate, ifMatch: string): void {
    this._saveState.set('saving');
    from(lastValueFrom(this.port.update(id, dto, ifMatch)))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this._patient.set(this._toVM(updated));
          this._saveState.set('saved');
        },
        error: () => {
          this._saveState.set('error');
        },
      });
  }

  private _toVM(p: Paciente): PacienteVM {
    return { id: p.id, nome: p.nome, tenantId: p.tenantId, dispositivo: p.dispositivo };
  }
}
