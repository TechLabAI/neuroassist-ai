import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SairComponent } from '@neuroassist/shared/auth-login';
import {
  ToastOutletComponent,
  BannerOutletComponent,
  ModalOutletComponent,
} from '@neuroassist/shared/ui-feedback';

// =============================================================================
// Raiz da aplicação.
//
// `auth-sair` entra aqui, e não na topbar, porque a topbar (`app/shell/`) é de
// outro agente nesta rodada. O botão se auto-oculta quando não há sessão — sem
// sessão, a única tela é o próprio login. Quando a topbar ganhar o menu de
// perfil, mover para lá.
//
// OS OUTLETS DE AVISO VIVEM AQUI, e não no AppShellComponent.
//
// Estavam lá, e o `AppShellComponent` não envolve as rotas de topo:
// `/onboarding`, `/login` e `/entrada` ficam fora dele. O assistente de
// configuração disparava faixa e aviso que não tinham onde aparecer — o código
// parecia tratar o erro e a tela seguia muda, que é o mesmo defeito que a
// própria superfície de aviso existe para acabar.
//
// A faixa vem ANTES do `router-outlet` porque ela empurra o conteúdo em vez de
// flutuar por cima: descreve uma condição que continua valendo, e sobrepor
// esconderia justamente o que a pessoa precisa consertar. No topo do aplicativo,
// e não dentro da coluna de conteúdo, porque uma condição de sessão vale para a
// tela inteira.
//
// Aviso e modal são sobreposições posicionadas (`fixed`), então a posição no DOM
// não muda o desenho — mas o contêiner com `aria-live` precisa já existir quando
// o texto entra, senão o leitor de tela não anuncia. Por isso ficam montados
// sempre, mesmo vazios.
// =============================================================================

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet,
    SairComponent,
    BannerOutletComponent,
    ToastOutletComponent,
    ModalOutletComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ui-banner-outlet />
    <router-outlet />
    <ui-toast-outlet />
    <ui-modal-outlet />
    <auth-sair />
  `,
})
export class AppShellRootComponent {}
