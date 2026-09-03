import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { ProposalReviewPageComponent } from './proposal-review-page.component';
import { ProposalReviewFacade } from '../trei-proposal.facade';
import { DraftNodeVm, ProposalReviewScreenVm } from '../trei.types';

// ---------------------------------------------------------------------------
// Minimal ProposalReviewFacade mock
// ---------------------------------------------------------------------------

const emptyVm = (): ProposalReviewScreenVm => ({
  nodes: [],
  activeLocale: 'pt',
  status: 'idle',
  error: null,
});

function buildFacadeMock(nodes: DraftNodeVm[] = []) {
  const nodesSignal = signal<DraftNodeVm[]>(nodes);
  const vm = signal<ProposalReviewScreenVm>({ ...emptyVm(), nodes });

  return {
    screenVm: vm.asReadonly(),
    nodes: nodesSignal.asReadonly(),
    activeLocale: signal<'pt' | 'en'>('pt').asReadonly(),
    status: signal<'idle' | 'loading' | 'loaded' | 'empty' | 'error'>('idle').asReadonly(),
    error: signal(null).asReadonly(),
    approve: jest.fn().mockResolvedValue(undefined),
    discard: jest.fn().mockResolvedValue(undefined),
    loadNode: jest.fn().mockResolvedValue(undefined),
    setActiveLocale: jest.fn(),
    materialize: jest.fn().mockResolvedValue('no-new-id'),
  };
}

// ---------------------------------------------------------------------------

describe('ProposalReviewPageComponent', () => {
  let fixture: ComponentFixture<ProposalReviewPageComponent>;
  let component: ProposalReviewPageComponent;
  let facadeMock: ReturnType<typeof buildFacadeMock>;

  async function setup(
    queryParams: Record<string, string> = {},
    nodes: DraftNodeVm[] = [],
  ) {
    facadeMock = buildFacadeMock(nodes);

    await TestBed.configureTestingModule({
      imports: [ProposalReviewPageComponent, RouterTestingModule],
      providers: [
        { provide: ProposalReviewFacade, useValue: facadeMock },
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: of(convertToParamMap(queryParams)),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProposalReviewPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('should create', async () => {
    await setup();
    expect(component).toBeTruthy();
  });

  it('should render the page header', async () => {
    await setup();
    const h1 = fixture.nativeElement.querySelector('h1') as HTMLElement;
    expect(h1.textContent).toContain('Propostas para aprovação');
  });

  it('should render the authoring banner (TECH-007)', async () => {
    await setup();
    const banner = fixture.nativeElement.querySelector('[data-testid="authoring-banner"]') as HTMLElement;
    expect(banner).toBeTruthy();
  });

  it('should render ProposalEmptyState when no nodes exist', async () => {
    await setup();
    const empty = fixture.nativeElement.querySelector('[data-testid="proposal-empty-state"]') as HTMLElement;
    expect(empty).toBeTruthy();
  });

  it('should render ProposalList when nodes exist', async () => {
    const nodes: DraftNodeVm[] = [
      {
        noId: 'no-1', locale: 'pt', texto: 'Olá', tipo: 'frase',
        subEstado: 'rascunho', confirmadoCuidadora: false, confirmadoPaciente: false,
        approvalInProgress: false, discardInProgress: false,
      },
    ];
    await setup({}, nodes);
    const list = fixture.nativeElement.querySelector('[data-testid="proposal-list"]') as HTMLElement;
    expect(list).toBeTruthy();
  });

  it('should render locale tabs', async () => {
    await setup();
    const tabPt = fixture.nativeElement.querySelector('[data-testid="locale-tab-pt"]') as HTMLElement;
    const tabEn = fixture.nativeElement.querySelector('[data-testid="locale-tab-en"]') as HTMLElement;
    expect(tabPt).toBeTruthy();
    expect(tabEn).toBeTruthy();
  });

  it('G2: should call facade.loadNode when proposalId query param is present and no nodes loaded', async () => {
    await setup({ proposalId: 'no-deep-link-123' });
    expect(facadeMock.loadNode).toHaveBeenCalledWith('no-deep-link-123');
  });

  it('G2: should NOT call facade.loadNode when no proposalId in query params', async () => {
    await setup({});
    expect(facadeMock.loadNode).not.toHaveBeenCalled();
  });

  it('should call facade.setActiveLocale when locale tab is clicked', async () => {
    await setup();
    const tabEn = fixture.nativeElement.querySelector('[data-testid="locale-tab-en"]') as HTMLButtonElement;
    tabEn.click();
    expect(facadeMock.setActiveLocale).toHaveBeenCalledWith('en');
  });

  it('should render back-to-session navigation link', async () => {
    await setup();
    const link = fixture.nativeElement.querySelector('[data-testid="back-to-session"]') as HTMLAnchorElement;
    expect(link).toBeTruthy();
  });

  // ============================================================
  // INV-1: confirmadoPaciente must never appear in rendered HTML
  // ============================================================

  it('INV-1: rendered HTML must not contain the string "confirmadoPaciente"', async () => {
    const nodes: DraftNodeVm[] = [
      {
        noId: 'no-inv1', locale: 'pt', texto: 'Teste INV-1', tipo: 'frase',
        subEstado: 'rascunho', confirmadoCuidadora: false, confirmadoPaciente: false,
        approvalInProgress: false, discardInProgress: false,
      },
    ];
    await setup({}, nodes);
    const html = fixture.nativeElement.innerHTML as string;
    expect(html).not.toContain('confirmadoPaciente');
  });
});
