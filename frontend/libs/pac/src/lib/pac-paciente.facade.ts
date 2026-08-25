import { Injectable, inject, signal, computed, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { lastValueFrom, from } from 'rxjs';
import { PAC_PACIENTE_PORT, Paciente, PacienteCreate, PacienteUpdate } from './api/paciente.port';
import type { AppError } from '@neuroassist/shared/error-model';
import { PacErrorHandler } from './error-surface/pac-error.handler';
import {
  mapProblemDetailToAppError,
  type ProblemDetail,
} from './error-surface/pac-problem-detail.mapper';

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
  private readonly errorHandler = inject(PacErrorHandler);

  private readonly _patient = signal<PacienteVM | null>(null);
  private readonly _saveState = signal<PatientSaveState>('idle');
  private readonly _loading = signal(false);
  private readonly _error = signal<string | null>(null);
  /** Erros atribuídos a campos do formulário, quando o backend os manda. */
  private readonly _fieldErrors = signal<readonly { field: string; message: string }[]>([]);

  readonly patient = this._patient.asReadonly();
  readonly saveState = this._saveState.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  /**
   * Erros por campo da última escrita.
   *
   * <p>Separado de {@link error} de propósito: a frase geral vai para uma faixa
   * ou aviso, e estas vão ao LADO do campo — que é onde a pessoa pode agir.
   */
  readonly fieldErrors = this._fieldErrors.asReadonly();

  /** A mensagem do backend para um campo específico, ou null. */
  erroDoCampo(campo: string): string | null {
    return this._fieldErrors().find((e) => e.field === campo)?.message ?? null;
  }

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
    // Uma tentativa nova não herda os erros da anterior: deixar a frase do campo
    // na tela enquanto a chamada corre sugere que ela ainda vale.
    this._fieldErrors.set([]);
    this._error.set(null);
    const key = generateIdempotencyKey();
    from(lastValueFrom(this.port.create(dto, key)))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (resp) => {
          if (resp.body) this._patient.set(this._toVM(resp.body));
          this._saveState.set('saved');
        },
        error: (err: unknown) => this._tratarErroDeEscrita(err),
      });
  }

  update(id: string, dto: PacienteUpdate, ifMatch: string): void {
    this._saveState.set('saving');
    this._fieldErrors.set([]);
    this._error.set(null);
    from(lastValueFrom(this.port.update(id, dto, ifMatch)))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this._patient.set(this._toVM(updated));
          this._saveState.set('saved');
        },
        error: (err: unknown) => this._tratarErroDeEscrita(err),
      });
  }

  /**
   * Encaminha a falha de escrita para a superfície certa.
   *
   * <p><b>O que havia aqui antes:</b> {@code error: () => { this._saveState.set('error'); }}
   * — callback SEM PARÂMETRO, nos dois métodos. O erro chegava e era descartado
   * inteiro: nem código, nem mensagem, nem erros por campo. Toda falha virava o
   * mesmo estado mudo, e a tela dizia no máximo uma frase fixa sobre conexão —
   * inclusive quando o problema era permissão, sessão sem empresa ou um campo
   * inválido que a pessoa poderia corrigir sozinha.
   *
   * <p>O {@code PacErrorHandler} e o mapeador existiam, com testes, e só a
   * facade de COMANDO os chamava. Este é o segundo ponto de invocação.
   */
  private _tratarErroDeEscrita(err: unknown): void {
    const appError = this._paraAppError(err);
    this._saveState.set('error');

    // 'handled' = o handler já pôs na tela (aviso, faixa ou modal).
    // 'inline'  = o erro pertence a esta tela, e é ela quem o desenha.
    const superficie = this.errorHandler.handle(appError, 'patient-register');

    // Erros por campo são sempre da tela: nenhuma faixa sabe dizer "ao lado do
    // campo dispositivo".
    this._fieldErrors.set(appError.fieldErrors ?? []);

    // Não repetir inline o que o handler já mostrou: a mesma frase em dois
    // lugares faz procurar dois problemas.
    this._error.set(superficie === 'inline' ? appError.message : null);
  }

  /**
   * Extrai o ProblemDetail do erro do HttpClient.
   *
   * <p>O corpo vem em {@code HttpErrorResponse.error}. Sem {@code type} não há
   * ProblemDetail — falha de rede, corpo vazio, erro que não veio do nosso
   * backend — e o código é {@code 'unknown'}, que o handler trata como
   * consultivo. Nunca devolver o erro cru: {@code String(HttpErrorResponse)} é
   * {@code "[object Object]"}, e foi assim que a tela do EDGE exibiu isso a uma
   * cuidadora.
   */
  private _paraAppError(err: unknown): AppError {
    const corpo = (err as { error?: unknown } | null)?.error;
    if (corpo && typeof corpo === 'object' && 'type' in corpo) {
      return mapProblemDetailToAppError(corpo as ProblemDetail);
    }
    return {
      code: 'unknown',
      message: err instanceof Error ? err.message : 'Erro ao salvar paciente',
    };
  }

  private _toVM(p: Paciente): PacienteVM {
    return { id: p.id, nome: p.nome, tenantId: p.tenantId, dispositivo: p.dispositivo };
  }
}
