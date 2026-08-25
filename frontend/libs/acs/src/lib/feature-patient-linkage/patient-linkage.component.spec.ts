// =============================================================================
// patient-linkage.component.spec.ts — FE-ACS-022
//
// Critical cases: coverage badge display, revoke flow uses linkIdFor() correctly,
// linked vs. unlinked caregiver lists, loading state.
// =============================================================================

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal, computed } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { provideRouter } from '@angular/router';
import { PatientLinkageComponent } from './patient-linkage.component';
import { AcsCaregiverFacade } from '../acs-caregiver.facade';
import type { CuidadorVm, CoverageVm } from '../acs.types';

// ---------------------------------------------------------------------------
// Mock signals and facade
// ---------------------------------------------------------------------------

const loadingSignal = signal(false);
const savingSignal = signal(false);
const errorSignal = signal<string | null>(null);
const caregiversSignal = signal<readonly CuidadorVm[]>([]);
const coverageMapSignal = signal<ReadonlyMap<string, readonly string[]>>(new Map());

function makeCuidadorVm(id: string, nome = 'Test'): CuidadorVm {
  return {
    id, nome, email: `${id}@test.com`,
    papel: 'cuidadora', status: 'active', isActive: true, papelLabel: 'Cuidadora',
  };
}

// linkIdFor tracks cuidador:patient pairs
const linkMapSignal = signal<ReadonlyMap<string, string>>(new Map());

// ADR-005: a tela passou a desenhar o modal bloqueante (cross-empresa 422 e
// re-imposição do último-vínculo 409), então o dublê precisa expor o sinal —
// sem ele o template estoura em todos os casos, não só nos do modal.
const coverageModalSignal = signal<{ type: 'deactivate' | 'cross-tenant'; pacienteNome: string } | null>(null);

const mockFacade = {
  loading: loadingSignal.asReadonly(),
  saving: savingSignal.asReadonly(),
  error: errorSignal.asReadonly(),
  caregivers: caregiversSignal.asReadonly(),
  activeCaregivers: computed(() => caregiversSignal().filter((c) => c.isActive)),
  coverageByPatient: coverageMapSignal.asReadonly(),
  coverageFor: jest.fn((pid: string): CoverageVm => {
    const coveredBy = coverageMapSignal().get(pid) ?? [];
    return { pacienteId: pid, coveredBy, isCovered: coveredBy.length > 0 };
  }),
  linkIdFor: jest.fn((cuidadorId: string, pacienteId: string): string | null => {
    return linkMapSignal().get(`${cuidadorId}:${pacienteId}`) ?? null;
  }),
  coverageModal: coverageModalSignal.asReadonly(),
  dismissCoverageModal: jest.fn(() => coverageModalSignal.set(null)),
  loadRoster: jest.fn().mockResolvedValue(undefined),
  linkToPatient: jest.fn().mockResolvedValue(undefined),
  revokeLink: jest.fn().mockResolvedValue(undefined),
  // unused but required for type compatibility
  selected: signal(null).asReadonly(),
  invite: jest.fn(),
  deactivate: jest.fn(),
  loadOne: jest.fn(),
};

// Mock ActivatedRoute — simulates /acs/pacientes/pac-marcio/vinculos
const mockActivatedRoute = {
  snapshot: {
    paramMap: {
      get: (key: string) => (key === 'pacienteId' ? 'pac-marcio' : null),
    },
  },
};

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

describe('PatientLinkageComponent — FE-ACS-022 (revoke flow)', () => {
  let fixture: ComponentFixture<PatientLinkageComponent>;
  let component: PatientLinkageComponent;

  beforeEach(async () => {
    loadingSignal.set(false);
    savingSignal.set(false);
    errorSignal.set(null);
    caregiversSignal.set([]);
    coverageMapSignal.set(new Map());
    linkMapSignal.set(new Map());
    jest.clearAllMocks();
    mockFacade.loadRoster.mockResolvedValue(undefined);
    mockFacade.revokeLink.mockResolvedValue(undefined);
    mockFacade.linkToPatient.mockResolvedValue(undefined);

    // Re-bind jest.fn to reflect current signal state
    mockFacade.coverageFor.mockImplementation((pid: string): CoverageVm => {
      const coveredBy = Array.from(coverageMapSignal().get(pid) ?? []);
      return { pacienteId: pid, coveredBy, isCovered: coveredBy.length > 0 };
    });
    mockFacade.linkIdFor.mockImplementation((cuidadorId: string, pacienteId: string): string | null => {
      return linkMapSignal().get(`${cuidadorId}:${pacienteId}`) ?? null;
    });

    await TestBed.configureTestingModule({
      imports: [PatientLinkageComponent],
      providers: [
        provideRouter([]),
        { provide: AcsCaregiverFacade, useValue: mockFacade },
        { provide: ActivatedRoute, useValue: mockActivatedRoute },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PatientLinkageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges(); // triggers ngOnInit
  });

  // ---------------------------------------------------------------------------
  // Initialization
  // ---------------------------------------------------------------------------
  describe('ngOnInit', () => {
    it('calls facade.loadRoster() once on init', () => {
      expect(mockFacade.loadRoster).toHaveBeenCalledTimes(1);
    });

    it('sets pacienteId from route param', () => {
      expect(component.pacienteAlvo()).toBe('pac-marcio');
    });
  });

  // ---------------------------------------------------------------------------
  // Composição fora da própria rota — o passo de vínculo do assistente
  //
  // Em /onboarding não existe :pacienteId na rota. Sem estas duas entradas o
  // componente só sabia se localizar pela própria URL, e era por isso que o
  // passo 3 do tutorial ("a coordenadora vincula cuidadoras ao paciente") não
  // tinha superfície nenhuma no assistente.
  // ---------------------------------------------------------------------------
  describe('entradas de composição', () => {
    it('a entrada pacienteId VENCE o parâmetro de rota', () => {
      fixture.componentRef.setInput('pacienteId', 'pac-do-assistente');
      fixture.detectChanges();
      expect(component.pacienteAlvo()).toBe('pac-do-assistente');
    });

    // CONTROLE NEGATIVO: sem entrada, a rota continua mandando. Uma entrada que
    // sobrescrevesse sempre (inclusive com null) quebraria a rota
    // /acs/pacientes/:pacienteId/vinculos sem que nada mais acusasse.
    it('sem entrada, o parâmetro de rota continua mandando', () => {
      fixture.componentRef.setInput('pacienteId', null);
      fixture.detectChanges();
      expect(component.pacienteAlvo()).toBe('pac-marcio');
    });

    it('composto=false mantém o <h1> próprio (comportamento de página)', () => {
      const h1 = fixture.nativeElement.querySelector('h1');
      expect(h1?.textContent).toContain('Vínculos do paciente');
    });

    it('composto=true suprime o <h1> — o assistente já tem o seu', () => {
      fixture.componentRef.setInput('composto', true);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('h1')).toBeNull();
    });
  });

  // ---------------------------------------------------------------------------
  // Coverage badge
  // ---------------------------------------------------------------------------
  describe('coverage badge', () => {
    it('shows coverage badge element always', () => {
      const badge = fixture.nativeElement.querySelector('[data-testid="coverage-badge"]');
      expect(badge).not.toBeNull();
    });

    it('calls facade.coverageFor() with the route pacienteId', () => {
      expect(mockFacade.coverageFor).toHaveBeenCalledWith('pac-marcio');
    });

    it('badge text indicates "sem cobertura" when isCovered=false', () => {
      // No coverage set up — default is not covered
      fixture.detectChanges();
      const badge = fixture.nativeElement.querySelector('[data-testid="coverage-badge"]');
      expect(badge?.textContent).toContain('sem cobertura');
    });
  });

  // ---------------------------------------------------------------------------
  // Loading state
  // ---------------------------------------------------------------------------
  describe('loading state', () => {
    it('shows loading indicator when facade.loading()=true', () => {
      loadingSignal.set(true);
      fixture.detectChanges();
      const el = fixture.nativeElement.querySelector('[data-testid="linkage-loading"]');
      expect(el).not.toBeNull();
    });

    it('hides loading indicator when facade.loading()=false', () => {
      loadingSignal.set(false);
      fixture.detectChanges();
      const el = fixture.nativeElement.querySelector('[data-testid="linkage-loading"]');
      expect(el).toBeNull();
    });
  });

  // ---------------------------------------------------------------------------
  // Error state
  // ---------------------------------------------------------------------------
  describe('error state', () => {
    it('shows error banner when facade.error() non-null', () => {
      errorSignal.set('Vínculo não encontrado');
      fixture.detectChanges();
      const el = fixture.nativeElement.querySelector('[data-testid="linkage-error"]');
      expect(el).not.toBeNull();
      expect(el?.textContent).toContain('Vínculo não encontrado');
    });
  });

  // ---------------------------------------------------------------------------
  // Linked vs. unlinked caregivers
  // ---------------------------------------------------------------------------
  describe('linked and unlinked caregivers', () => {
    it('shows linked-caregiver-row for caregivers in coveredBy', () => {
      caregiversSignal.set([makeCuidadorVm('cuid-1', 'Jamilly')]);
      coverageMapSignal.set(new Map([['pac-marcio', ['cuid-1']]]));
      mockFacade.coverageFor.mockReturnValue({ pacienteId: 'pac-marcio', coveredBy: ['cuid-1'], isCovered: true });
      fixture.detectChanges();

      const rows = fixture.nativeElement.querySelectorAll('[data-testid="linked-caregiver-row"]');
      expect(rows.length).toBe(1);
    });

    it('shows unlinked-caregiver-row for active caregivers not in coveredBy', () => {
      caregiversSignal.set([makeCuidadorVm('cuid-2', 'Other')]);
      coverageMapSignal.set(new Map([['pac-marcio', []]]));
      mockFacade.coverageFor.mockReturnValue({ pacienteId: 'pac-marcio', coveredBy: [], isCovered: false });
      fixture.detectChanges();

      const rows = fixture.nativeElement.querySelectorAll('[data-testid="unlinked-caregiver-row"]');
      expect(rows.length).toBe(1);
    });

    it('link button calls facade.linkToPatient() with correct ids', async () => {
      caregiversSignal.set([makeCuidadorVm('cuid-2', 'Other')]);
      coverageMapSignal.set(new Map());
      mockFacade.coverageFor.mockReturnValue({ pacienteId: 'pac-marcio', coveredBy: [], isCovered: false });
      fixture.detectChanges();

      const linkBtn = fixture.nativeElement.querySelector('[data-testid="link-caregiver-btn"]');
      expect(linkBtn).not.toBeNull();
      linkBtn.click();
      await fixture.whenStable();
      expect(mockFacade.linkToPatient).toHaveBeenCalledWith('cuid-2', 'pac-marcio');
    });
  });

  // ---------------------------------------------------------------------------
  // Revoke flow — uses linkIdFor() (P4 critical: linkId must be obtained via facade)
  // ---------------------------------------------------------------------------
  describe('revoke flow — linkIdFor() usage', () => {
    beforeEach(() => {
      // Set up a linked caregiver with a known linkId
      caregiversSignal.set([makeCuidadorVm('cuid-1', 'Jamilly')]);
      coverageMapSignal.set(new Map([['pac-marcio', ['cuid-1']]]));
      linkMapSignal.set(new Map([['cuid-1:pac-marcio', 'link-abc-123']]));
      mockFacade.coverageFor.mockReturnValue({
        pacienteId: 'pac-marcio', coveredBy: ['cuid-1'], isCovered: true,
      });
      mockFacade.linkIdFor.mockReturnValue('link-abc-123');
      fixture.detectChanges();
    });

    it('shows revoke-link-btn for linked caregivers', () => {
      const btn = fixture.nativeElement.querySelector('[data-testid="revoke-link-btn"]');
      expect(btn).not.toBeNull();
    });

    it('clicking revoke-link-btn shows confirm and cancel buttons', () => {
      fixture.nativeElement.querySelector('[data-testid="revoke-link-btn"]').click();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="confirm-revoke-btn"]')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('[data-testid="cancel-revoke-btn"]')).not.toBeNull();
    });

    it('clicking cancel-revoke-btn hides confirmation', () => {
      fixture.nativeElement.querySelector('[data-testid="revoke-link-btn"]').click();
      fixture.detectChanges();

      fixture.nativeElement.querySelector('[data-testid="cancel-revoke-btn"]').click();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="revoke-link-btn"]')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('[data-testid="confirm-revoke-btn"]')).toBeNull();
    });

    it('confirmRevoke() calls facade.linkIdFor() to obtain the linkId', async () => {
      await component.confirmRevoke('cuid-1');
      expect(mockFacade.linkIdFor).toHaveBeenCalledWith('cuid-1', 'pac-marcio');
    });

    it('confirmRevoke() calls facade.revokeLink() with cuidadorId, linkId, pacienteId', async () => {
      await component.confirmRevoke('cuid-1');
      // linkIdFor returns 'link-abc-123' → revokeLink called with that linkId
      expect(mockFacade.revokeLink).toHaveBeenCalledWith('cuid-1', 'link-abc-123', 'pac-marcio');
    });

    it('confirmRevoke() does NOT call revokeLink() when linkIdFor returns null', async () => {
      mockFacade.linkIdFor.mockReturnValue(null);
      await component.confirmRevoke('cuid-1');
      expect(mockFacade.revokeLink).not.toHaveBeenCalled();
    });

    it('confirmRevoke() resets pendingRevokeId to null after operation', async () => {
      component.requestRevoke('cuid-1');
      expect(component.pendingRevokeId()).toBe('cuid-1');

      await component.confirmRevoke('cuid-1');
      expect(component.pendingRevokeId()).toBeNull();
    });
  });
});
