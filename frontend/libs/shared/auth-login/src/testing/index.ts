// =============================================================================
// @neuroassist/shared/auth-login/testing — porta de entrada SÓ para teste.
//
// POR QUE É UMA PORTA SEPARADA, e não o barrel principal:
//
//   O barrel principal exporta componentes Angular. O runner do Playwright é
//   Node puro: ao importar de lá, ele arrasta `login-page.component.ts` →
//   `@angular/common` → o compiler facade do Angular, e estoura antes de rodar
//   qualquer teste ("JIT compilation failed"). Este arquivo não importa nada de
//   Angular — o único `import` do que ele expõe é `import type`, que o
//   transpilador apaga.
//
//   O segundo motivo é de produção: fixture de token não deveria ser alcançável
//   a partir do código do aplicativo. Separar a porta faz o `import` errado ser
//   visível na revisão em vez de silencioso.
// =============================================================================

export { scriptSemearSessao, scriptSemearSessaoCuidadora, conteudoSessaoSemeada } from './e2e-session';
export { RESPOSTA_TOKEN_COORD, RESPOSTA_TOKEN_CUIDADORA } from './token-fixtures';
