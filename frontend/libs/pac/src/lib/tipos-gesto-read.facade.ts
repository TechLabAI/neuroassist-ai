import { Injectable, inject, signal, computed, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { from, lastValueFrom } from 'rxjs';
import { TIPOS_GESTO_READ_PORT, TipoGesto } from './api/tipos-gesto-read.port';
import {
  ehDetectavel,
  motivoIndisponivel,
  guidanceDe,
  type DetectabilityState,
  type EngineFeatureRef,
} from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// TipoGestoVM — flat view model (same shape as TipoGesto for simplicity)
// ---------------------------------------------------------------------------

export interface TipoGestoVM {
  readonly id: string;
  readonly nome: string;
  readonly detectabilityState: DetectabilityState;
  readonly engineFeatureRef: EngineFeatureRef | null;
  /** `true` quando pode ser escolhido para um comando. */
  readonly detectavel: boolean;
  /** Por que não pode ser escolhido — `null` quando pode. */
  readonly motivoIndisponivel: string | null;
  /** Como executar o gesto. `null` para os que o motor não detecta. */
  readonly comoFazer: string | null;
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

  /**
   * Primeiro tipo DETECTÁVEL — é o default do formulário de comando novo.
   *
   * <p>Era `this._tipos()[0]`, o primeiro da lista qualquer que fosse. Bastava o
   * catálogo devolver um pedido sem motor na primeira posição para o formulário
   * nascer apontando para algo indetectável, e o comando ser salvo assim.
   */
  readonly firstTipoId = computed(
    () => this._tipos().find((t) => t.detectavel)?.id ?? this._tipos()[0]?.id ?? '',
  );

  /** Só os que podem ser escolhidos para um comando. */
  readonly detectaveis = computed(() => this._tipos().filter((t) => t.detectavel));

  /**
   * Resolve id do CATÁLOGO para a referência que o MOTOR entende.
   *
   * <p>Esta é a tradução que faltava no projeto inteiro: o teste ao vivo
   * comparava id de catálogo com emissão de motor, e nunca casava. Quem traduz é
   * quem tem o catálogo — o PAC — e não o GEST, que por contrato não busca
   * catálogo nenhum.
   */
  engineFeatureRefDe(id: string | null): EngineFeatureRef | null {
    if (!id) return null;
    return this._tipos().find((t) => t.id === id)?.engineFeatureRef ?? null;
  }

  load(): void {
    this._loading.set(true);
    this._error.set(null);
    from(lastValueFrom(this.port.list()))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (tipos) => {
          this._tipos.set(tipos.map(paraVM));
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
            return already ? list : [...list, paraVM(novo)];
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

/**
 * Enriquece o tipo cru com o que a tela precisa decidir: pode escolher? por que
 * não? como se faz?
 *
 * <p>Tudo derivado do vocabulário canônico — nenhuma dessas respostas é digitada
 * uma segunda vez aqui, e é o que impede a tela de voltar a divergir do motor.
 */
function paraVM(t: TipoGesto): TipoGestoVM {
  // FALHA FECHADA. Um tipo que chega sem estado — backend antigo, campo novo
  // ainda não propagado, resposta truncada — é tratado como NÃO detectável.
  //
  // O inverso seria muito pior: um gesto sem motor apresentado como escolhível
  // produz um comando que nunca dispara, e o paciente descobre isso no momento
  // em que precisa pedir ajuda. Diante da dúvida, o custo certo é a cuidadora
  // ver uma opção a menos, não o alarme não tocar.
  //
  // `engineFeatureRef` passa por `guidanceDe`, que valida contra o vocabulário:
  // um valor desconhecido vira instrução nula em vez de texto inventado.
  const estado: DetectabilityState = ehEstadoConhecido(t.detectabilityState)
    ? t.detectabilityState
    : 'registered';

  return {
    id: t.id,
    nome: t.nome,
    detectabilityState: estado,
    engineFeatureRef: guidanceDe(t.engineFeatureRef)?.engineFeatureRef ?? null,
    detectavel: ehDetectavel(estado),
    motivoIndisponivel: motivoIndisponivel(estado),
    comoFazer: guidanceDe(t.engineFeatureRef)?.comoFazer ?? null,
  };
}

const ESTADOS: readonly DetectabilityState[] = [
  'registered',
  'pending_training',
  'supported',
  'deprecated',
];

function ehEstadoConhecido(v: unknown): v is DetectabilityState {
  return typeof v === 'string' && (ESTADOS as readonly string[]).includes(v);
}
