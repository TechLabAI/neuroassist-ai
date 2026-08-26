import { ChangeDetectionStrategy, Component, ElementRef, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthSessionStore } from '@neuroassist/shared/auth-session';
import { AuthApiService, LoginFalhou, type LoginErro } from './auth-api.service';
import { ROTA_HOME } from './auth.config';

// =============================================================================
// LoginPageComponent — a porta de entrada do sistema.
//
// Três cuidados que valem mais que o layout:
//
// 1. A mensagem de erro de credencial é UMA só. O acs-service responde o mesmo
//    401 opaco para "não existe esse usuário" e para "senha errada" — de
//    propósito, porque diferenciar transforma a tela de login num verificador de
//    quem tem conta. A tela não pode desfazer isso: "e-mail não cadastrado" e
//    "senha incorreta" seriam duas mensagens onde o backend fez questão de ter
//    uma.
//
// 2. Falha de rede NÃO é falha de credencial. Se o backend está fora, dizer
//    "e-mail ou senha incorretos" faz a pessoa duvidar do que ela sabe e tentar
//    de novo até bloquear a conta. São textos diferentes porque são fatos
//    diferentes.
//
// 3. A senha não vai para lugar nenhum além do corpo do POST: não é logada, não
//    entra na URL, não aparece em mensagem de erro, e o signal é limpo assim que
//    a sessão é registrada.
//
// 4. AUTOPREENCHIMENTO DO NAVEGADOR. O gerenciador de senhas escreve direto no
//    `value` do input e, no Chrome, não dispara `input` — então o Angular não
//    fica sabendo. A primeira versão disto travava o botão em "desabilitado"
//    para exatamente quem usa gerenciador de senhas, que é quem mais deveria
//    ser incentivado a usar. Por isso `entrar()` lê os inputs no momento do
//    envio em vez de confiar só nos signals, e `[disabled]` reflete apenas
//    "estou enviando".
// =============================================================================

@Component({
  selector: 'auth-login-page',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="flex min-h-screen items-center justify-center bg-surface-2 p-6">
      <form
        class="w-full max-w-sm rounded-2xl border border-line bg-surface p-8 shadow-sm"
        (submit)="entrar($event)"
        data-testid="login-form"
        novalidate
      >
        <h1 class="text-xl font-semibold text-ink">Entrar</h1>
        <p class="mt-1 text-sm text-ink-soft">NeuroAssist AI</p>

        <div class="mt-6">
          <label for="login-email" class="mb-1 block text-xs font-semibold text-ink-soft">
            E-mail
          </label>
          <input
            id="login-email"
            name="login"
            type="email"
            autocomplete="username"
            autocapitalize="none"
            spellcheck="false"
            class="w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-brand"
            [value]="login()"
            (input)="login.set($any($event.target).value)"
            [disabled]="carregando()"
            data-testid="login-email"
          />
        </div>

        <div class="mt-4">
          <label for="login-senha" class="mb-1 block text-xs font-semibold text-ink-soft">
            Senha
          </label>
          <input
            id="login-senha"
            name="senha"
            type="password"
            autocomplete="current-password"
            class="w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-brand"
            [value]="senha()"
            (input)="senha.set($any($event.target).value)"
            [disabled]="carregando()"
            data-testid="login-senha"
          />
        </div>

        <!-- role=alert + aria-live: quem usa leitor de tela precisa saber que a
             tentativa falhou sem ter de sair caçando o texto na página. -->
        @if (erro(); as motivo) {
          <p
            class="mt-4 rounded-lg bg-error-subtle px-3 py-2 text-sm text-error-ink"
            role="alert"
            aria-live="assertive"
            data-testid="login-erro"
          >
            {{ mensagem(motivo) }}
          </p>
        }

        <button
          type="submit"
          class="mt-6 w-full rounded-xl bg-brand px-5 py-2.5 text-sm font-semibold text-brand-ink hover:bg-brand-strong disabled:opacity-60"
          [disabled]="carregando()"
          data-testid="login-submit"
        >
          {{ carregando() ? 'Entrando…' : 'Entrar' }}
        </button>
      </form>
    </main>
  `,
})
export class LoginPageComponent {
  private readonly api = inject(AuthApiService);
  private readonly sessao = inject(AuthSessionStore);
  private readonly router = inject(Router);
  private readonly rota = inject(ActivatedRoute);

  /**
   * O próprio elemento do componente. Existe por causa do autopreenchimento
   * (ver o cuidado nº 4 no topo): na hora do envio o valor autoritativo é o que
   * está no DOM, não o que o Angular acha que está.
   */
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly login = signal('');
  readonly senha = signal('');
  readonly carregando = signal(false);
  readonly erro = signal<LoginErro | null>(null);

  /** Não faz sentido chamar o backend com campo vazio — o 400 não ajuda ninguém. */
  preenchido(): boolean {
    return this.login().trim().length > 0 && this.senha().length > 0;
  }

  entrar(evento: Event): void {
    evento.preventDefault();
    this.sincronizarComODom();

    // Duplo envio: o `[disabled]` do botão cobre o clique, mas não cobre o Enter
    // no campo nem um submit programático. A guarda de verdade é esta.
    if (this.carregando() || !this.preenchido()) return;

    this.carregando.set(true);
    this.erro.set(null);

    this.api.login(this.login().trim(), this.senha()).subscribe({
      next: (resposta) => {
        this.sessao.entrar(resposta);
        // A senha some da memória do componente assim que deixa de ser
        // necessária. Não é blindagem — é não deixá-la à toa.
        this.senha.set('');
        this.carregando.set(false);
        void this.router.navigateByUrl(this.destino());
      },
      error: (falha: unknown) => {
        this.carregando.set(false);
        // Qualquer coisa que não seja `LoginFalhou` é bug nosso, não erro do
        // usuário — mas a tela ainda precisa dizer algo, e 'servidor' é o texto
        // honesto para "não foi culpa sua e não sei o que houve".
        this.erro.set(falha instanceof LoginFalhou ? falha.motivo : 'servidor');
      },
    });
  }

  /**
   * Puxa para os signals o que o navegador escreveu nos inputs sem avisar.
   *
   * <p>Só sobrescreve quando há algo escrito: nos testes os campos são
   * preenchidos pelos signals e o DOM ainda está vazio no momento do envio —
   * copiar vazio por cima apagaria o que o teste acabou de pôr.
   */
  private sincronizarComODom(): void {
    const noDom = this.valorDoCampo('#login-email');
    if (noDom) this.login.set(noDom);
    const senhaNoDom = this.valorDoCampo('#login-senha');
    if (senhaNoDom) this.senha.set(senhaNoDom);
  }

  private valorDoCampo(seletor: string): string {
    const el = (this.host.nativeElement as HTMLElement).querySelector<HTMLInputElement>(seletor);
    return el?.value ?? '';
  }

  mensagem(motivo: LoginErro): string {
    switch (motivo) {
      case 'credencial-invalida':
        // Uma frase para os dois casos. Ver o cuidado nº 1 no topo do arquivo.
        return 'E-mail ou senha incorretos.';
      case 'rede':
        return 'Não consegui falar com o servidor. Verifique a conexão e tente de novo.';
      case 'servidor':
        return 'O servidor teve um problema ao entrar. Tente de novo em instantes.';
    }
  }

  /**
   * Para onde ir depois de entrar.
   *
   * <p>O `destino` vem da URL, ou seja, de fora — e um destino vindo de fora que
   * não é validado é redirecionamento aberto: bastaria mandar alguém para
   * `/login?destino=https://sitefalso` para que o nosso login servisse de trampolim.
   * Por isso só caminho interno passa, e `//host` (que o navegador lê como
   * "outro host, mesmo protocolo") é recusado junto.
   */
  private destino(): string {
    const pedido = this.rota.snapshot.queryParamMap.get('destino');
    if (!pedido || !pedido.startsWith('/') || pedido.startsWith('//')) return ROTA_HOME;
    return pedido;
  }
}
