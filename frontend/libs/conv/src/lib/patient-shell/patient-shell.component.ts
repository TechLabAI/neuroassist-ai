import { Component, ChangeDetectionStrategy, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { PatientTopbarComponent } from './patient-topbar.component';
import { Locale } from '../conv.types';

// =============================================================================
// PatientShellComponent — restricted patient shell (CONV-FE-003 / ADR-012/014).
//
// Promoted from: design-reference/.../shell/patient-shell.component.ts (Emenda 3).
//
// Isolation guarantee (ADR-012 Opção C, architectural principle §Patient-shell isolation):
//   - NO sidebar.component imported here.
//   - NO caregiver route is rendered — isolation is by route ABSENCE, not CSS/state.
//   - NO caregiver credential (cuidadora session) is accessible from this shell.
//   - This shell renders exclusively CONV content: RouterOutlet for { /conversar, /confirmar }.
//
// Cross-module boundary (scope:conv → scope:web):
//   - PatientShellComponent is registered in app.routes.ts under /paciente (scope:web).
//   - INPUT_CHANNEL_PORT composition (GEST engine) happens at scope:web level via
//     GestShellIntegrationService — NOT imported here (CONV-INT-003 / nx-boundaries.md).
//
// Session context:
//   - The patient locale lives in a local signal for this promote.
//   - TODO[INT-FE-CONV-001]: read locale from the patient JWT claim once ACS
//     patient-session token carries it (CONV-FE-003 dependency on INT-FE-CONV-001).
//
// PatientTopbarComponent (CONV-FE-003):
//   - Presentational: receives locale + patientName as inputs, emits localeSwitchRequested.
//   - No admin navigation or credentials rendered.
// =============================================================================

@Component({
  selector: 'conv-patient-shell',
  standalone: true,
  imports: [RouterOutlet, PatientTopbarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="patient-layout">
      <conv-patient-topbar
        [locale]="locale()"
        [patientName]="patientName()"
        (localeSwitchRequested)="onLocaleSwitch($event)" />
      <main class="patient-content">
        <router-outlet />
      </main>
    </div>
  `,
  styles: [`:host { display: contents; }
    .patient-layout {
      display: flex;
      flex-direction: column;
      height: 100vh;
      overflow: hidden;
    }
    .patient-content {
      flex: 1;
      overflow: auto;
      background-color: var(--content-bg);
      background-image: var(--content-mesh);
    }`],
})
export class PatientShellComponent {
  /** Active locale — drives ConvArvoreComponent and ConfirmacaoTreinamentoComponent.
   *  Defaults to PT (ADR-018, CONV_FEATURE_FLAGS.defaultLocale = 'pt').
   *  TODO[INT-FE-CONV-001]: seed from JWT locale claim. */
  readonly locale = signal<Locale>('pt');

  /** Patient name for the topbar label.
   *  TODO[INT-FE-CONV-001]: seed from JWT patient identity claim. */
  readonly patientName = signal<string>('Paciente');

  protected onLocaleSwitch(locale: Locale): void {
    this.locale.set(locale);
  }
}
