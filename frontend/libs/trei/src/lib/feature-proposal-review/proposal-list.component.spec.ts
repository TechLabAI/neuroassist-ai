import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProposalListComponent } from './proposal-list.component';
import { DraftNodeVm } from '../trei.types';

describe('ProposalListComponent', () => {
  let fixture: ComponentFixture<ProposalListComponent>;
  let component: ProposalListComponent;

  const nodes: DraftNodeVm[] = [
    {
      noId: 'no-1',
      locale: 'pt',
      texto: 'Quero água',
      tipo: 'frase',
      subEstado: 'rascunho',
      confirmadoCuidadora: false,
      confirmadoPaciente: false,
      approvalInProgress: false,
      discardInProgress: false,
    },
    {
      noId: 'no-2',
      locale: 'pt',
      texto: 'Sinto dor',
      tipo: 'frase',
      subEstado: 'rascunho',
      confirmadoCuidadora: false,
      confirmadoPaciente: false,
      approvalInProgress: false,
      discardInProgress: false,
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProposalListComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ProposalListComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    component.nodes = nodes;
    component.activeLocale = 'pt';
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should render one card per node', () => {
    component.nodes = nodes;
    component.activeLocale = 'pt';
    fixture.detectChanges();
    const cards = fixture.nativeElement.querySelectorAll('[data-testid="proposal-card"]') as NodeList;
    expect(cards.length).toBe(2);
  });

  it('should render the proposal list container', () => {
    component.nodes = nodes;
    component.activeLocale = 'pt';
    fixture.detectChanges();
    const list = fixture.nativeElement.querySelector('[data-testid="proposal-list"]') as HTMLElement;
    expect(list).toBeTruthy();
  });

  it('should emit approve when a card emits approve', () => {
    component.nodes = nodes;
    component.activeLocale = 'pt';
    fixture.detectChanges();

    const approveEvents: Array<{ noId: string; locale: string }> = [];
    component.approve.subscribe((e) => approveEvents.push(e));

    const approveBtn = fixture.nativeElement.querySelector('[data-testid="aprovar-btn"]') as HTMLButtonElement;
    approveBtn.click();

    expect(approveEvents).toHaveLength(1);
    expect(approveEvents[0].noId).toBe('no-1');
  });

  it('should emit reject when a card emits reject', () => {
    component.nodes = nodes;
    component.activeLocale = 'pt';
    fixture.detectChanges();

    const rejectEvents: Array<{ noId: string; locale: string }> = [];
    component.reject.subscribe((e) => rejectEvents.push(e));

    const discardBtn = fixture.nativeElement.querySelector('[data-testid="descartar-btn"]') as HTMLButtonElement;
    discardBtn.click();

    expect(rejectEvents).toHaveLength(1);
    expect(rejectEvents[0].noId).toBe('no-1');
  });
});
