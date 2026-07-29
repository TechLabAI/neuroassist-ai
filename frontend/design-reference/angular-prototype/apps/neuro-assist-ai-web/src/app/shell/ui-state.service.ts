import { Injectable, signal, effect } from '@angular/core';

/**
 * Estado de UI do shell — apenas tema claro/escuro (produto de tenant único, sem RBAC/contexto).
 * CLARO é o padrão (LBC Emenda 0 / design-system: "guardião silencioso" para cuidadora
 * não-técnica em ambiente doméstico). O escuro é um toggle, nunca o default.
 */
@Injectable({ providedIn: 'root' })
export class UiStateService {
  readonly theme = signal<'light' | 'dark'>('light');

  constructor() {
    // Aplica o tema no <html> — theme.css usa [data-theme="dark"] como override;
    // ausência do atributo = claro (default do @theme).
    effect(() => {
      if (this.theme() === 'dark') {
        document.documentElement.setAttribute('data-theme', 'dark');
      } else {
        document.documentElement.removeAttribute('data-theme');
      }
    });
  }

  toggleTheme(): void {
    this.theme.update(v => (v === 'light' ? 'dark' : 'light'));
  }
}
