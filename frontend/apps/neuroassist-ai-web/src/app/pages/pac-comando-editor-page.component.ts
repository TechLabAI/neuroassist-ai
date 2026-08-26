import { Component, ChangeDetectionStrategy, viewChild, computed, inject } from '@angular/core';
import { CommandEditorComponent, TiposGestoReadFacade } from '@neuroassist/pac';
import { GestureLiveTestComponent } from '@neuroassist/gest';
import { ComandoDraft, GestureTestOutcome } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// PacComandoEditorPageComponent — scope:web composition root (GEST-INT-005)
//
// PAC never imports scope:gest directly (nx-boundary, ADR-002); PAC exposes
// [slot=live-test] as an ng-content outlet and this page — living in the app,
// not in a lib — is what actually projects GestureLiveTestComponent into it.
// Builds the ComandoDraft the live-test component needs from
// CommandEditorComponent's own public `editor` signal (EditorVM), so the
// live-test panel always reflects whatever the caregiver currently has typed
// into the form, without PAC needing to know GEST's contract types exist.
// ---------------------------------------------------------------------------

@Component({
  selector: 'app-pac-comando-editor-page',
  standalone: true,
  imports: [CommandEditorComponent, GestureLiveTestComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pac-command-editor>
      @if (draft(); as d) {
        <gest-gesture-live-test slot="live-test" [draft]="d" (outcome)="onOutcome($event)" />
      }
    </pac-command-editor>
  `,
})
export class PacComandoEditorPageComponent {
  private readonly editorRef = viewChild(CommandEditorComponent);

  /** Provida por `providePac()` no nível do ambiente — a MESMA instância que o
   * editor usa, então o catálogo já está carregado quando o rascunho é montado. */
  private readonly tiposFacade = inject(TiposGestoReadFacade);

  protected readonly draft = computed<ComandoDraft | null>(() => {
    const editor = this.editorRef();
    if (!editor) return null;
    const ed = editor.editor();
    return {
      commandDraftRef: ed.cid ?? 'draft-new',
      rotulo: ed.rotulo,
      primitivoPrincipal: ed.primitivoPrincipal,
      primitivoSecundario: ed.primitivoSecundario,
      // A tradução catálogo → motor acontece AQUI, no hospedeiro, porque é ele
      // quem tem o catálogo em mãos. O GEST recebe a resolução pronta: por
      // contrato ele "does not own or fetch the catalog".
      //
      // `null` quando o tipo escolhido não tem motor — e é um valor que a tela
      // precisa mostrar, não esconder: antes disso o teste ao vivo simplesmente
      // rodava para sempre sem veredito nenhum.
      engineFeatureRefPrincipal: this.tiposFacade.engineFeatureRefDe(ed.primitivoPrincipal),
      engineFeatureRefSecundario: this.tiposFacade.engineFeatureRefDe(ed.primitivoSecundario),
      sensibilidade: ed.sensibilidade,
      urgencia: ed.urgencia,
      tempoMinimoGestoMs: ed.tempoMinimoGestoMs,
      tempoMaximoGestoMs: ed.tempoMaximoGestoMs,
      composite: ed.primitivoSecundario !== null,
    };
  });

  protected onOutcome(outcome: GestureTestOutcome): void {
    // TODO(follow-up): PacComandoFacade doesn't yet expose a hook to record a
    // verified live-test outcome against the draft (e.g. gating "Salvar" on
    // a real `recognized` result). Wire this once that facade API exists.
    void outcome;
  }
}
