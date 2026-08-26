import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { PAC_ACCESS_DECISION } from './pac-access-tokens';

// ---------------------------------------------------------------------------
// pacCommandWriteGuard — SPEC-01 §3
//
// Protege as rotas do editor de comando (/pac/comandos/novo e
// /pac/comandos/:comandoId/editar). Quem não pode escrever comando volta para o
// catálogo, que ele continua podendo ler.
//
// ANTES: `return true` incondicional (TODO[PAC-FE-015]). O backend segue sendo
// a rede de segurança — mas depender só dele significa oferecer a tela, deixar
// a pessoa preencher o formulário e só então devolver 403 no salvar. Barrar na
// rota é ergonomia; barrar no backend é segurança. As duas coisas são
// necessárias e nenhuma substitui a outra.
//
// 'commandWrite' é CHAVE DE TELA, não authority. A tradução acontece no
// provider de PAC_ACCESS_DECISION em app.config.ts, via PAC_PERMISSION_MAP —
// que hoje aponta para `pac.comando-authoring.criar`. Citar o literal aqui é o
// defeito que o ACS já pagou: o nome muda no acs-service e o frontend vira um
// 403 silencioso sem ninguém encostar neste arquivo.
// ---------------------------------------------------------------------------

export const pacCommandWriteGuard: CanActivateFn = () => {
  const acesso = inject(PAC_ACCESS_DECISION, { optional: true });

  // Token não providenciado (lib montada isolada em teste de unidade).
  if (acesso === null) return true;

  if (!acesso.can('commandWrite')) {
    return inject(Router).createUrlTree(['/pac']);
  }

  return true;
};
