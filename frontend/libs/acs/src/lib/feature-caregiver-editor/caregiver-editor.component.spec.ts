// =============================================================================
// caregiver-editor.component.spec.ts — FE-ACS-022
//
// Critical cases: form validation blocks submit, valid form calls invite(),
// email validation error shown, draft preserved in edit mode from facade.selected().
// =============================================================================

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { CaregiverEditorComponent } from './caregiver-editor.component';
import { AcsCaregiverFacade } from '../acs-caregiver.facade';
import type { CuidadorVm } from '../acs.types';

// ---------------------------------------------------------------------------
// Mock signals
// ---------------------------------------------------------------------------

const selectedSignal = signal<CuidadorVm | null>(null);
const savingSignal = signal(false);
const errorSignal = signal<string | null>(null);

const mockFacade = {
  selected: selectedSignal.asReadonly(),
  saving: savingSignal.asReadonly(),
  error: errorSignal.asReadonly(),
  invite: jest.fn().mockResolvedValue({
    id: 'cuid-new', nome: 'Test', email: 'test@test.com',
    papel: 'cuidadora', status: 'active', isActive: true, papelLabel: 'Cuidadora',
  }),
  loadOne: jest.fn().mockResolvedValue(undefined),
  edit: jest.fn().mockResolvedValue({
    id: 'cuid-1', nome: 'Nome Corrigido', email: 'corrigido@test.com',
    papel: 'cuidadora', status: 'active', isActive: true, papelLabel: 'Cuidadora',
  }),
  // unused but needed for DI type compatibility
  loading: signal(false).asReadonly(),
  caregivers: signal([]).asReadonly(),
  activeCaregivers: signal([]).asReadonly(),
  coverageByPatient: signal(new Map()).asReadonly(),
  coverageFor: jest.fn().mockReturnValue({ pacienteId: '', coveredBy: [], isCovered: false }),
  linkIdFor: jest.fn().mockReturnValue(null),
  loadRoster: jest.fn().mockResolvedValue(undefined),
  deactivate: jest.fn().mockResolvedValue(undefined),
  linkToPatient: jest.fn().mockResolvedValue(undefined),
  revokeLink: jest.fn().mockResolvedValue(undefined),
};

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

describe('CaregiverEditorComponent — FE-ACS-022 (form validation)', () => {
  let fixture: ComponentFixture<CaregiverEditorComponent>;
  let component: CaregiverEditorComponent;

  beforeEach(async () => {
    selectedSignal.set(null);
    savingSignal.set(false);
    errorSignal.set(null);
    jest.clearAllMocks();
    mockFacade.invite.mockResolvedValue({
      id: 'cuid-new', nome: 'Test', email: 'test@test.com',
      papel: 'cuidadora', status: 'active', isActive: true, papelLabel: 'Cuidadora',
    });
    mockFacade.loadOne.mockResolvedValue(undefined);
    mockFacade.edit.mockResolvedValue({
      id: 'cuid-1', nome: 'Nome Corrigido', email: 'corrigido@test.com',
      papel: 'cuidadora', status: 'active', isActive: true, papelLabel: 'Cuidadora',
    });

    await TestBed.configureTestingModule({
      imports: [CaregiverEditorComponent],
      providers: [
        provideRouter([]),
        { provide: AcsCaregiverFacade, useValue: mockFacade },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CaregiverEditorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  // ---------------------------------------------------------------------------
  // Form validation — invalid form blocks submit
  // ---------------------------------------------------------------------------
  describe('form validation', () => {
    it('submit button is disabled when form is invalid (initial state — empty fields)', () => {
      const submitBtn = fixture.nativeElement.querySelector('[data-testid="editor-submit"]');
      // form is pristine and invalid by default (required fields empty)
      expect(submitBtn.disabled).toBe(true);
    });

    it('submit button is enabled when all required fields are valid', () => {
      // Fill all required fields with valid values
      const nomeInput = fixture.nativeElement.querySelector('[data-testid="editor-nome"]');
      const emailInput = fixture.nativeElement.querySelector('[data-testid="editor-email"]');

      nomeInput.value = 'Jamilly Amaral';
      nomeInput.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      emailInput.value = 'jamilly@test.com';
      emailInput.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      // Set via form control directly (more reliable in unit tests)
      component.draftForm.setValue({
        nome: 'Jamilly Amaral',
        email: 'jamilly@test.com',
        papel: 'cuidadora',
      });
      fixture.detectChanges();

      const submitBtn = fixture.nativeElement.querySelector('[data-testid="editor-submit"]');
      expect(submitBtn.disabled).toBe(false);
    });

    it('nome field invalid with fewer than 2 characters', () => {
      component.draftForm.get('nome')!.setValue('A');
      component.draftForm.get('nome')!.markAsTouched();
      fixture.detectChanges();
      expect(component.draftForm.get('nome')?.hasError('minlength')).toBe(true);
    });

    it('email field invalid with non-email value', () => {
      component.draftForm.get('email')!.setValue('not-an-email');
      component.draftForm.get('email')!.markAsTouched();
      fixture.detectChanges();
      expect(component.draftForm.get('email')?.hasError('email')).toBe(true);
    });

    it('email validation error message rendered when email invalid and touched', () => {
      component.draftForm.get('email')!.setValue('bad-email');
      component.draftForm.get('email')!.markAsTouched();
      fixture.detectChanges();
      const alert = fixture.nativeElement.querySelector('[role="alert"]');
      expect(alert).not.toBeNull();
    });

    it('form markAllAsTouched() called when invalid form is submitted', async () => {
      // draftForm is invalid; call save()
      const markAllSpy = jest.spyOn(component.draftForm, 'markAllAsTouched');
      await component.save();
      expect(markAllSpy).toHaveBeenCalled();
    });

    it('facade.invite() NOT called when form is invalid', async () => {
      // form is invalid (empty)
      await component.save();
      expect(mockFacade.invite).not.toHaveBeenCalled();
    });

    it('facade.invite() called with form values when form is valid', async () => {
      component.draftForm.setValue({
        nome: 'Jamilly Amaral',
        email: 'jamilly@test.com',
        papel: 'cuidadora',
      });
      fixture.detectChanges();
      await component.save();
      expect(mockFacade.invite).toHaveBeenCalledWith({
        nome: 'Jamilly Amaral',
        email: 'jamilly@test.com',
        papel: 'cuidadora',
      });
    });
  });

  // ---------------------------------------------------------------------------
  // Title renders according to mode
  // ---------------------------------------------------------------------------
  describe('mode detection', () => {
    it('isEdit() is false by default (invite mode — no cuidadorId in route)', () => {
      expect(component.isEdit()).toBe(false);
    });

    it('title contains "Convidar" in invite mode', () => {
      const h1 = fixture.nativeElement.querySelector('h1');
      expect(h1?.textContent).toContain('Convidar');
    });
  });

  // ---------------------------------------------------------------------------
  // COMPOSTO — o editor virou o passo "Convidar cuidadora" do assistente
  //
  // Ele NAVEGAVA para /acs ao convidar com sucesso. Composto no assistente, isso
  // EJETAVA a pessoa da configuração no meio — depois de uma ação bem-sucedida e
  // sem aviso: ela perdia o assistente, o progresso visível e o passo seguinte.
  // Navegar é comportamento de PÁGINA, e composto ele não é página.
  // ---------------------------------------------------------------------------
  describe('composto no assistente', () => {
    function preencher(): void {
      component.draftForm.setValue({ nome: 'Ana', email: 'ana@x.com', papel: 'cuidadora' });
    }

    it('COMPOSTO: convida, ANUNCIA por `convidada` e NÃO navega', async () => {
      const router = TestBed.inject(Router);
      const navegar = jest.spyOn(router, 'navigate').mockResolvedValue(true);
      const anunciado: unknown[] = [];
      component.composto = true;
      component.convidada.subscribe((v) => anunciado.push(v));
      fixture.detectChanges();

      preencher();
      await component.save();

      expect(mockFacade.invite).toHaveBeenCalledTimes(1);
      expect(anunciado).toEqual([{ cuidadorId: 'cuid-new', nome: 'Ana', email: 'ana@x.com' }]);
      expect(navegar).not.toHaveBeenCalled();
    });

    // CONTROLE NEGATIVO: como PÁGINA (rota /acs/cuidadores/novo) ele continua
    // navegando. Sem este par, um `navigate` que nunca acontece — por qualquer
    // motivo — passaria pelo teste acima.
    it('COMO PÁGINA: convida e navega para /acs', async () => {
      const router = TestBed.inject(Router);
      const navegar = jest.spyOn(router, 'navigate').mockResolvedValue(true);

      preencher();
      await component.save();

      expect(mockFacade.invite).toHaveBeenCalledTimes(1);
      expect(navegar).toHaveBeenCalledWith(['/acs']);
    });

    it('COMPOSTO: não contribui <h1> nem o botão Cancelar', () => {
      component.composto = true;
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('h1')).toBeNull();
      expect(fixture.nativeElement.querySelector('[data-testid="editor-cancel"]')).toBeNull();
    });

    // CONTROLE NEGATIVO do item acima: como página, os dois voltam.
    it('COMO PÁGINA: contribui <h1> e o botão Cancelar', () => {
      expect(fixture.nativeElement.querySelector('h1')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('[data-testid="editor-cancel"]')).not.toBeNull();
    });

    it('COMPOSTO: confirma o convite na própria superfície', async () => {
      component.composto = true;
      fixture.detectChanges();
      preencher();
      await component.save();
      fixture.detectChanges();
      expect(
        fixture.nativeElement.querySelector('[data-testid="editor-convidada"]'),
      ).not.toBeNull();
    });
  });

  // ---------------------------------------------------------------------------
  // Error display
  // ---------------------------------------------------------------------------
  describe('error display', () => {
    it('renders facade.error() in error banner', () => {
      errorSignal.set('Convite falhou');
      fixture.detectChanges();
      const alert = fixture.nativeElement.querySelector('[role="alert"]');
      expect(alert).not.toBeNull();
      expect(alert?.textContent).toContain('Convite falhou');
    });

    it('does NOT render error banner when facade.error()=null', () => {
      errorSignal.set(null);
      fixture.detectChanges();
      // When facade.error()=null, the facade error paragraph is not rendered
      const template = fixture.nativeElement as HTMLElement;
      expect(template.querySelector('p[role="alert"]')).toBeNull();
    });
  });

  // ---------------------------------------------------------------------------
  // EDICAO — o ramo que era NO-OP SILENCIOSO
  //
  // O `if` cobria so a criacao e nao havia `else`: quem corrigia um nome clicava
  // "Salvar", nada acontecia, e a tela nao dizia nem sucesso nem erro. A causa
  // era mais funda que a tela — nao existia endpoint de edicao no acs-service.
  //
  // Estes testes existem porque, quando liguei o ramo, NENHUM teste ficou
  // vermelho ao eu reverte-lo para `return;` — a mutacao sobrevivia, e uma
  // mutacao que sobrevive e a mesma coisa que nao ter teste.
  // ---------------------------------------------------------------------------

  describe('edicao', () => {
    async function montarEmModoEdicao(): Promise<void> {
      component.isEdit.set(true);
      component['editingId'].set('cuid-1');
      component.draftForm.setValue({
        nome: 'Nome Corrigido',
        email: 'corrigido@test.com',
        papel: 'cuidadora',
      });
      await component.save();
    }

    it('salva de verdade — chama facade.edit', async () => {
      await montarEmModoEdicao();
      expect(mockFacade.edit).toHaveBeenCalledTimes(1);
    });

    it('manda APENAS nome e login — nunca o papel', async () => {
      // O endpoint nao aceita papel; mandar daqui faria a tela oferecer um campo
      // que o servidor ignoraria em silencio: a pessoa mudaria o papel, veria
      // "salvo", e nada teria mudado.
      await montarEmModoEdicao();
      expect(mockFacade.edit).toHaveBeenCalledWith('cuid-1', {
        nome: 'Nome Corrigido',
        email: 'corrigido@test.com',
      });
    });

    it('nao convida ao editar — sao operacoes diferentes', async () => {
      await montarEmModoEdicao();
      expect(mockFacade.invite).not.toHaveBeenCalled();
    });

    it('formulario invalido nao chega ao servidor', async () => {
      component.isEdit.set(true);
      component['editingId'].set('cuid-1');
      component.draftForm.setValue({ nome: '', email: 'x', papel: 'cuidadora' });

      await component.save();

      expect(mockFacade.edit).not.toHaveBeenCalled();
    });

    it('falha do servidor nao navega — a pessoa fica onde pode corrigir', async () => {
      mockFacade.edit.mockResolvedValue(null);
      const navegou = jest.spyOn(TestBed.inject(Router), 'navigate');

      await montarEmModoEdicao();

      expect(navegou).not.toHaveBeenCalled();
    });
  });
});
