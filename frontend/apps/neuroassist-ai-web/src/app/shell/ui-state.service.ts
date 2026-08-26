import { Injectable, effect, signal } from '@angular/core';

// ---------------------------------------------------------------------------
// UiStateService — estado de chrome do shell (tema claro/escuro).
//
// O tema é aplicado como `data-theme` no <html>; theme.css define o bloco
// [data-theme="dark"] com os overrides. CLARO é o padrão deliberado do produto
// ("guardião silencioso" — ver cabeçalho de theme.css), então a ausência do
// atributo já é o tema correto.
//
// A preferência é persistida em localStorage. Todo acesso é protegido: em aba
// anônima ou com armazenamento bloqueado o getter lança, e o shell não pode cair
// por causa de um toggle de tema.
// ---------------------------------------------------------------------------

const STORAGE_KEY = 'neuroassist.theme';

export type ThemeMode = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class UiStateService {
  private readonly _theme = signal<ThemeMode>(readStoredTheme());

  readonly theme = this._theme.asReadonly();

  constructor() {
    effect(() => {
      const mode = this._theme();
      const root = document.documentElement;
      if (mode === 'dark') {
        root.setAttribute('data-theme', 'dark');
      } else {
        root.removeAttribute('data-theme');
      }
      try {
        localStorage.setItem(STORAGE_KEY, mode);
      } catch {
        /* armazenamento indisponível — a preferência vale só para esta sessão */
      }
    });
  }

  toggleTheme(): void {
    this._theme.update((m) => (m === 'light' ? 'dark' : 'light'));
  }

  setTheme(mode: ThemeMode): void {
    this._theme.set(mode);
  }
}

function readStoredTheme(): ThemeMode {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'dark' ? 'dark' : 'light';
  } catch {
    return 'light';
  }
}
