import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';

// =============================================================================
// EntradaComponent — Tela 00 (entrada.component promoted from design-reference)
// Route: /entrada — CHROMELESS (rendered outside the AppShellComponent chrome).
// Wireframe ref: §5.1–5.5 — Entrada — TELA-MOMENTO (fora do shell)
// Source: design-reference/angular-prototype/screens/entrada/entrada.component.ts
// Promoted: P3 of /fe-architect-promote neuroassist-ai DASH
// This is a brand entry / CTA screen — no data fetching, no auth required.
// =============================================================================

@Component({
  selector: 'dash-entrada',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="relative flex min-h-screen items-center overflow-hidden bg-sidebar">
      <div class="pointer-events-none absolute -left-40 -top-40 size-[34rem] rounded-full bg-brand/25 blur-[130px]"></div>
      <div class="pointer-events-none absolute -right-32 top-1/3 size-[28rem] rounded-full bg-ai/15 blur-[130px]"></div>
      <div class="pointer-events-none absolute bottom-0 left-1/3 size-[24rem] rounded-full bg-success/10 blur-[130px]"></div>

      <main class="relative z-10 mx-auto flex max-w-xl flex-col items-center px-8 py-24 text-center">
        <img
          src="assets/logo-white.png"
          alt="NeuroAssist AI"
          class="mb-10 h-14 w-auto object-contain" />

        <h1 class="max-w-md font-display text-4xl font-extrabold leading-tight tracking-tight text-sidebar-ink">
          Um gesto que você não vai perder.
        </h1>
        <p class="mt-5 max-w-sm text-base leading-relaxed text-sidebar-muted">
          Webcam comum, alarme confiável, sem hardware caro. NeuroAssist AI observa em silêncio —
          e avisa alto quando importa.
        </p>

        <a
          routerLink="/"
          class="mt-10 inline-flex items-center gap-2 rounded-xl bg-brand px-7 py-3.5 text-sm font-bold text-brand-ink shadow-card transition-colors hover:bg-brand-strong">
          Entrar no painel
          <span class="material-symbols-outlined text-[20px]">arrow_forward</span>
        </a>

        <div class="mt-14 flex items-center gap-2 text-xs font-medium text-sidebar-muted">
          <span class="material-symbols-outlined text-[16px] text-success">verified_user</span>
          Calma por padrão · alarme reservado só para o que importa
        </div>
      </main>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EntradaComponent {}
