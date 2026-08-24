import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthSessionStore } from '@neuroassist/shared/auth-session';
import { construirMenu, rotuloDoPapel, type ItemMenu } from './menu-trabalho';

// ---------------------------------------------------------------------------
// SidebarComponent — a navegação do dia, filtrada por perfil.
//
// O QUE aparece está em menu-trabalho.ts (o catálogo item→authority, com o
// porquê de cada escolha). Aqui está só COMO aparece.
//
// Duas coisas que este componente faz de propósito:
//
//  1. Não renderiza o que a pessoa não pode acessar — não esconde por CSS. Um
//     item invisível mas presente no DOM continua sendo navegável por teclado,
//     por leitor de tela e por quem inspeciona a página; "escondido" não é uma
//     resposta, é uma aparência de resposta.
//  2. Com sessão ausente, desenha zero itens. Ver a nota em construirMenu().
//
// Nada disto protege coisa alguma: quem barra o acesso é o guard da rota e o
// backend. O menu só evita oferecer uma porta que vai bater na cara da pessoa.
//
// O alarme de EMERGÊNCIA continua não sendo destino de navegação (§4.1 do
// wireframe): ele substitui a tela ativa via outlet global do app-shell. O item
// "Alarmes" daqui é a fila de rotina — o que chegou e o que falta confirmar.
// ---------------------------------------------------------------------------

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <aside class="flex h-full w-[248px] flex-col border-r border-sidebar-line bg-sidebar">
      <div class="flex h-24 flex-shrink-0 items-center border-b border-sidebar-line px-6">
        <img
          class="block max-h-[64px] w-full object-contain object-left"
          src="assets/logo-white.png"
          alt="NeuroAssist AI" />
      </div>

      <nav
        class="min-h-0 flex-1 overflow-y-auto px-3 py-4 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
        data-testid="sidebar-nav">
        @for (item of itensDeTrabalho(); track item.id) {
          <a
            [routerLink]="item.route"
            [routerLinkActiveOptions]="{ exact: item.route === '/' }"
            routerLinkActive="!bg-sidebar-active !text-sidebar-active-ink"
            [attr.data-menu-item]="item.id"
            class="mb-1 flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-semibold text-sidebar-muted transition-colors hover:bg-sidebar-hover hover:text-sidebar-ink">
            <span class="material-symbols-outlined text-[20px]">{{ item.icon }}</span>
            <span class="flex-1">{{ item.label }}</span>
          </a>
        }

        @if (itensDaClinica().length > 0) {
          <div class="my-3 border-t border-sidebar-line"></div>
          <div class="px-3 pb-1 text-[10px] font-bold uppercase tracking-wider text-sidebar-muted/60">
            Clínica
          </div>
        }

        @for (item of itensDaClinica(); track item.id) {
          <!--
            O item pendente não recebe realce de "ativo": ele aponta para a tela
            existente mais próxima, e marcá-lo como ativo afirmaria que aquela
            tela é o seu destino — que é justamente o que ainda não é verdade.
          -->
          <a
            [routerLink]="item.route"
            [routerLinkActive]="item.pendente ? [] : REALCE_ATIVO"
            [routerLinkActiveOptions]="{ exact: true }"
            [attr.data-menu-item]="item.id"
            class="mb-1 flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-semibold text-sidebar-muted transition-colors hover:bg-sidebar-hover hover:text-sidebar-ink">
            <span class="material-symbols-outlined text-[20px]">{{ item.icon }}</span>
            <span class="flex-1">{{ item.label }}</span>
            @if (item.pendente) {
              <span
                class="rounded-md bg-sidebar-hover px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wide text-sidebar-muted"
                title="Item previsto; a tela própria ainda não existe — leva à superfície mais próxima">
                em breve
              </span>
            }
          </a>
        }

        @if (itensDeConfig().length > 0) {
          <div class="my-3 border-t border-sidebar-line"></div>
        }

        @for (item of itensDeConfig(); track item.id) {
          <a
            [routerLink]="item.route"
            routerLinkActive="!bg-sidebar-active !text-sidebar-active-ink"
            [attr.data-menu-item]="item.id"
            class="mb-1 flex items-center gap-3 rounded-xl px-3 py-2.5 text-xs font-medium text-sidebar-muted/70 transition-colors hover:bg-sidebar-hover hover:text-sidebar-ink">
            <span class="material-symbols-outlined text-[18px]">{{ item.icon }}</span>
            <span class="flex-1">{{ item.label }}</span>
          </a>
        }
      </nav>

      <!--
        O rodapé só existe quando há sessão: sem ninguém dentro não há nome nem
        papel a mostrar, e um rodapé vazio insinuaria um usuário anônimo.
      -->
      @if (autenticado()) {
      <div class="flex flex-shrink-0 items-center gap-3 border-t border-sidebar-line p-4">
        <div
          class="grid size-8 place-items-center rounded-full bg-sidebar-accent/15 text-xs font-bold text-sidebar-active-ink">
          {{ iniciais() }}
        </div>
        <div class="min-w-0 leading-tight">
          <div class="truncate text-xs font-semibold text-sidebar-ink">{{ nome() }}</div>
          <div class="truncate text-[11px] text-sidebar-muted" data-testid="sidebar-papel">
            {{ papel() }}
          </div>
        </div>
      </div>
      }
    </aside>
  `,
})
export class SidebarComponent {
  /** Classes do item ativo. Constante para poder ser trocada por `[]` no pendente. */
  protected readonly REALCE_ATIVO = ['!bg-sidebar-active', '!text-sidebar-active-ink'];

  private readonly sessao = inject(AuthSessionStore);

  /**
   * O menu inteiro, recalculado quando a sessão muda.
   *
   * <p>Depende de `sessao.user()` (um signal) e não de `pode()` diretamente:
   * `pode` é um método, e chamá-lo não cria dependência reativa. Ler `user()`
   * aqui é o que faz o menu se redesenhar ao entrar e ao sair.
   */
  private readonly menu = computed<ItemMenu[]>(() => {
    const user = this.sessao.user();
    if (user === null) return [];
    return construirMenu({
      papel: user.papel,
      pode: (a) => user.authorities.includes(a),
    });
  });

  protected readonly itensDeTrabalho = computed(() =>
    this.menu().filter((i) => i.grupo === 'trabalho'),
  );
  protected readonly itensDaClinica = computed(() =>
    this.menu().filter((i) => i.grupo === 'clinica'),
  );
  protected readonly itensDeConfig = computed(() =>
    this.menu().filter((i) => i.grupo === 'config'),
  );

  /**
   * Nome de quem entrou, direto da sessão.
   *
   * <p>Antes vinha do read-model da home do DASH — o que obrigava a barra
   * lateral a depender de um módulo de negócio para escrever um nome, e a
   * mostrar o nome errado enquanto a home não tivesse carregado. O corpo de
   * `POST /oauth2/token` devolve `nome`; a sessão é a fonte certa.
   */
  protected readonly nome = computed(() => this.sessao.user()?.nome || '—');

  /** Há sessão viva — governa o rodapé de identidade. */
  protected readonly autenticado = computed(() => this.sessao.user() !== null);

  /** "Cuidadora" / "Coordenadora" / "Apoio ao paciente" — nunca fixo em código. */
  protected readonly papel = computed(() => rotuloDoPapel(this.sessao.user()?.papel ?? null));

  protected readonly iniciais = computed(() => iniciaisDe(this.nome()));
}

/** "Jamilly Amaral" → "JA". Vazio → "—". */
function iniciaisDe(nome: string): string {
  const partes = nome.trim().split(/\s+/).filter(Boolean);
  if (partes.length === 0) return '—';
  const primeira = partes[0][0] ?? '';
  const ultima = partes.length > 1 ? (partes[partes.length - 1][0] ?? '') : '';
  return (primeira + ultima).toUpperCase();
}
