// =============================================================================
// caregiver-editor.component.spec.ts — FE-ACS-022
//
// Critical cases: form validation blocks submit, valid form calls invite(),
// email validation error shown, draft preserved in edit mode from facade.selected().
// =============================================================================

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
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
});
