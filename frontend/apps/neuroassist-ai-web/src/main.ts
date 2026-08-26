import { isDevMode } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { AppShellRootComponent } from './app/app-shell-root.component';
import { appConfig } from './app/app.config';

// Marcador de BUILD DE DESENVOLVIMENTO.
//
// Existe para que a suíte de e2e possa dizer a verdade sobre onde está rodando.
// Alguns cenários do GEST só são executáveis contra um build de desenvolvimento:
// eles exercitam a máquina de estados do facade com um motor de detecção
// simulado, e o gancho que troca o motor é fechado por `isDevMode()` — de
// propósito, porque uma variável global capaz de desligar a detecção de gesto
// não pode existir onde há paciente. Sem este marcador esses cenários reprovavam
// contra o container, dando a impressão de defeito onde há uma decisão.
//
// `isDevMode()` é falso no build de produção, então a atribuição inteira some do
// pacote: o marcador não existe onde não deve existir.
if (isDevMode()) {
  (globalThis as Record<string, unknown>)['__NEUROASSIST_DEV__'] = true;
}

bootstrapApplication(AppShellRootComponent, appConfig).catch((err) =>
  console.error(err),
);
