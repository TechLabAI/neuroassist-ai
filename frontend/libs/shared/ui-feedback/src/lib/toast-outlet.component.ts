import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService, type TipoDeAviso } from './toast.service';

// =============================================================================
// Onde os avisos aparecem. Um só por aplicação, montado no shell.
//
// DECISÕES DE ACESSIBILIDADE, E POR QUE SÃO ESTAS
//
// `aria-live="polite"` e não `assertive`. Assertive interrompe o leitor de tela
// no meio da frase — e neste produto o que tem licença para interromper é o
// ALARME de emergência, não um aviso de falha de rede. Gastar a interrupção com
// um toast é ensinar a pessoa a ignorá-la.
//
// `role="status"` no contêiner, não `role="alert"`: `alert` implica assertive.
//
// O contêiner com `aria-live` existe SEMPRE no DOM, mesmo vazio. Um live region
// criado junto com o conteúdo costuma não ser anunciado: o leitor de tela
// precisa já estar observando a região quando o texto entra.
//
// CAMADAS: o alarme de emergência é `z-50` e cobre a tela inteira. O aviso fica
// ABAIXO dele (z-30), e o modal entre os dois (z-40). Durante uma emergência não
// existe nada mais para ler — e a primeira versão deste componente estava em
// z-[60], por cima do alarme, exatamente ao contrário da prioridade do produto.
//
// O toast NÃO rouba o foco. Mover o foco tira a pessoa de onde ela estava, e o
// aviso é informação, não um pedido de ação. Por isso o botão de ação fica na
// ordem natural de tabulação, e por isso avisos com ação não somem sozinhos.
// =============================================================================

@Component({
  selector: 'ui-toast-outlet',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      role="status"
      aria-live="polite"
      aria-relevant="additions"
      class="pointer-events-none fixed bottom-4 right-4 z-30 flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2">
      @for (aviso of toasts.avisos(); track aviso.id) {
        <div
          class="pointer-events-auto rounded-xl border px-4 py-3 shadow-card"
          [class]="classesDe(aviso.tipo)"
          data-testid="toast">
          <div class="flex items-start gap-3">
            <span aria-hidden="true" class="material-symbols-outlined text-[20px] leading-none">
              {{ iconeDe(aviso.tipo) }}
            </span>
            <p class="min-w-0 flex-1 text-sm">{{ aviso.mensagem }}</p>

            @if (aviso.acao; as acao) {
              <button
                type="button"
                class="shrink-0 rounded-lg px-2 py-1 text-sm font-bold underline underline-offset-2"
                data-testid="toast-acao"
                (click)="executar(aviso.id, acao.callback)">
                {{ acao.label }}
              </button>
            }

            <button
              type="button"
              class="shrink-0 rounded-lg p-1 opacity-70 hover:opacity-100"
              [attr.aria-label]="'Dispensar aviso: ' + aviso.mensagem"
              data-testid="toast-dispensar"
              (click)="toasts.dismiss(aviso.id)">
              <span aria-hidden="true" class="material-symbols-outlined text-[18px] leading-none">close</span>
            </button>
          </div>
        </div>
      }
    </div>
  `,
})
export class ToastOutletComponent {
  protected readonly toasts = inject(ToastService);

  protected classesDe(tipo: TipoDeAviso): string {
    switch (tipo) {
      case 'erro':
        return 'border-danger bg-danger-bg text-danger-ink';
      case 'sucesso':
        return 'border-success bg-success-bg text-success-ink';
      default:
        return 'border-line bg-surface text-ink';
    }
  }

  protected iconeDe(tipo: TipoDeAviso): string {
    switch (tipo) {
      case 'erro':
        return 'error';
      case 'sucesso':
        return 'check_circle';
      default:
        return 'info';
    }
  }

  /**
   * Executa a ação e retira o aviso.
   *
   * <p>Retirar faz parte: deixar na tela um "Tentar novamente" já acionado
   * convida ao clique duplo, e no caso do EDGE cada clique é uma escrita.
   */
  protected executar(id: number, callback: () => void): void {
    this.toasts.dismiss(id);
    callback();
  }
}
