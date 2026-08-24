import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
} from '@angular/core';
import { ModalService } from './modal.service';

// =============================================================================
// O modal na tela.
//
// DECISÕES DE ACESSIBILIDADE
//
// `role="alertdialog"` quando bloqueante, `role="dialog"` quando não: alertdialog
// diz ao leitor de tela que há uma mensagem importante junto, e é o caso do
// portão de emergência.
//
// O FOCO é o ponto que costuma ser esquecido, e sem ele um modal não é um modal:
//   1. ao abrir, o foco vai para dentro — senão quem usa teclado continua
//      navegando a página atrás, que está inerte;
//   2. Tab circula DENTRO do diálogo (armadilha de foco) — sem isso a pessoa sai
//      do modal sem perceber e não encontra o caminho de volta;
//   3. ao fechar, o foco VOLTA para onde estava. Devolver o foco ao começo da
//      página faria perder o lugar no formulário que se está corrigindo.
//
// A camada é z-40: abaixo do alarme de emergência (z-50) e acima do aviso (z-30).
// Nem o modal bloqueante do portão de segurança compete com um pedido de socorro.
// =============================================================================

@Component({
  selector: 'ui-modal-outlet',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (modais.modal(); as modal) {
      <div
        class="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4"
        (click)="cliqueNoFundo($event)">
        <div
          #caixa
          [attr.role]="modal.bloqueante ? 'alertdialog' : 'dialog'"
          aria-modal="true"
          [attr.aria-labelledby]="modal.titulo ? 'ui-modal-titulo' : null"
          aria-describedby="ui-modal-mensagem"
          class="w-[min(28rem,100%)] rounded-2xl border border-line bg-surface p-6 shadow-card"
          data-testid="modal"
          (click)="$event.stopPropagation()"
          (keydown)="aoTeclar($event)">
          @if (modal.titulo) {
            <h2 id="ui-modal-titulo" class="font-display text-base font-bold text-ink">
              {{ modal.titulo }}
            </h2>
          }
          <p id="ui-modal-mensagem" class="mt-2 text-sm text-ink-soft">{{ modal.mensagem }}</p>

          <div class="mt-5 flex justify-end gap-2">
            <button
              type="button"
              class="rounded-xl bg-brand px-5 py-2.5 text-sm font-bold text-brand-ink"
              data-testid="modal-acao"
              (click)="executar()">
              {{ modal.acao?.label ?? 'Entendi' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
})
export class ModalOutletComponent {
  protected readonly modais = inject(ModalService);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);
  private readonly injector = inject(Injector);
  private focoAnterior: HTMLElement | null = null;

  protected readonly bloqueante = computed(() => this.modais.modal()?.bloqueante ?? false);

  constructor() {
    effect(() => {
      const aberto = this.modais.modal() !== null;

      if (aberto) {
        // Guardado ANTES de mover: é para cá que o foco volta ao fechar.
        this.focoAnterior ??= document.activeElement as HTMLElement | null;
        // O foco só pode ir para dentro DEPOIS que a caixa existe no DOM. Uma
        // primeira versão lia a caixa por `viewChild` dentro do próprio efeito e
        // encontrava `undefined` — o efeito roda antes de a view do `@if` ser
        // criada. O sintoma foi cruel: o teste que verificava a DEVOLUÇÃO do
        // foco passava assim mesmo, porque o foco nunca saía do lugar e
        // "voltava" para onde já estava. Passava por vacuidade.
        afterNextRender(() => this.focarPrimeiro(), { injector: this.injector });
      } else if (this.focoAnterior) {
        this.focoAnterior.focus();
        this.focoAnterior = null;
      }
    });
  }

  private focarPrimeiro(): void {
    const caixa = this.host.nativeElement.querySelector<HTMLElement>('[data-testid="modal"]');
    if (caixa && !caixa.contains(document.activeElement)) {
      this.focavel(caixa)[0]?.focus();
    }
  }

  protected aoTeclar(evento: KeyboardEvent): void {
    if (evento.key === 'Escape' && !this.bloqueante()) {
      this.modais.fechar();
      return;
    }
    if (evento.key !== 'Tab') return;

    // Armadilha de foco. Sem isto o Tab sai do diálogo para a página atrás, que
    // está visualmente inerte — a pessoa perde o modal de vista sem fechá-lo.
    const alvos = this.focavel(evento.currentTarget as HTMLElement);
    if (alvos.length === 0) return;
    const primeiro = alvos[0];
    const ultimo = alvos[alvos.length - 1];

    if (evento.shiftKey && document.activeElement === primeiro) {
      evento.preventDefault();
      ultimo.focus();
    } else if (!evento.shiftKey && document.activeElement === ultimo) {
      evento.preventDefault();
      primeiro.focus();
    }
  }

  protected cliqueNoFundo(_evento: MouseEvent): void {
    if (this.bloqueante()) return;
    this.modais.fechar();
  }

  protected executar(): void {
    const acao = this.modais.modal()?.acao ?? null;
    this.modais.fechar();
    acao?.callback();
  }

  private focavel(raiz: HTMLElement): HTMLElement[] {
    return Array.from(
      raiz.querySelectorAll<HTMLElement>(
        'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
      ),
    ).filter((el) => !el.hasAttribute('disabled'));
  }
}
