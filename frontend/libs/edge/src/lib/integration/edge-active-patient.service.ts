import { Injectable, signal } from '@angular/core';

// =============================================================================
// Quem é o paciente que o shell está exibindo.
//
// POR QUE É UM SERVIÇO PRÓPRIO, e não um sinal dentro do
// EdgeShellIntegrationService: aquele serviço IMPORTA a
// DevicePairingPageComponent (para entregá-la ao assistente por
// `resolvePairingSlot`). Se a página passasse a importá-lo de volta, fecharia um
// ciclo entre os dois módulos — o tipo de ciclo que o TypeScript aceita e que
// depois aparece em runtime como uma classe `undefined`, longe da causa.
//
// Este arquivo não importa nada do EDGE, então qualquer um dos dois lados pode
// depender dele.
// =============================================================================

@Injectable({ providedIn: 'root' })
export class EdgeActivePatientService {
  private readonly _pacienteAtivo = signal<string | null>(null);

  /** O paciente exibido pelo shell, ou null antes de ele resolver um. */
  readonly pacienteAtivo = this._pacienteAtivo.asReadonly();

  definir(pacienteId: string | null): void {
    this._pacienteAtivo.set(pacienteId);
  }
}
