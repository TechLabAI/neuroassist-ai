import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProposedNodeCardComponent } from './proposed-node-card.component';
import { DraftNodeVm } from '../trei.types';

describe('ProposedNodeCardComponent', () => {
  let fixture: ComponentFixture<ProposedNodeCardComponent>;
  let component: ProposedNodeCardComponent;

  const rascunhoNode: DraftNodeVm = {
    noId: 'no-abc-123',
    locale: 'pt',
    texto: 'Quero água',
    tipo: 'frase',
    subEstado: 'rascunho',
    confirmadoCuidadora: false,
    confirmadoPaciente: false,
    approvalInProgress: false,
    discardInProgress: false,
  };

  const approvedNode: DraftNodeVm = {
    ...rascunhoNode,
    noId: 'no-xyz-456',
    subEstado: 'aguardando_aprovacao',
    confirmadoCuidadora: true,
    approvalInProgress: false,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProposedNodeCardComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ProposedNodeCardComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    component.node = rascunhoNode;
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should render the node card container', () => {
    component.node = rascunhoNode;
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('[data-testid="proposal-card"]') as HTMLElement;
    expect(el).toBeTruthy();
  });

  it('should display the node text', () => {
    component.node = rascunhoNode;
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Quero água');
  });

  it('should show Aprovar button when node is not yet confirmed by caregiver', () => {
    component.node = rascunhoNode;
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('[data-testid="aprovar-btn"]') as HTMLButtonElement;
    expect(btn).toBeTruthy();
  });

  it('should show aprovado-badge when node is already confirmed by caregiver', () => {
    component.node = approvedNode;
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('[data-testid="aprovado-badge"]') as HTMLElement;
    expect(badge).toBeTruthy();
    const btn = fixture.nativeElement.querySelector('[data-testid="aprovar-btn"]') as HTMLButtonElement;
    expect(btn).toBeNull();
  });

  it('should emit approve with correct noId and locale when Aprovar is clicked', () => {
    component.node = rascunhoNode;
    fixture.detectChanges();

    const approveEvents: Array<{ noId: string; locale: string }> = [];
    component.approve.subscribe((e) => approveEvents.push(e));

    const btn = fixture.nativeElement.querySelector('[data-testid="aprovar-btn"]') as HTMLButtonElement;
    btn.click();

    expect(approveEvents).toHaveLength(1);
    expect(approveEvents[0].noId).toBe(rascunhoNode.noId);
    expect(approveEvents[0].locale).toBe(rascunhoNode.locale);
  });

  it('should show descartar button for rascunho nodes', () => {
    component.node = rascunhoNode;
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('[data-testid="descartar-btn"]') as HTMLButtonElement;
    expect(btn).toBeTruthy();
  });

  it('should show descartar button for aguardando_aprovacao nodes', () => {
    component.node = approvedNode;
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('[data-testid="descartar-btn"]') as HTMLButtonElement;
    expect(btn).toBeTruthy();
  });

  it('should emit reject with correct noId and locale when Descartar is clicked', () => {
    component.node = rascunhoNode;
    fixture.detectChanges();

    const rejectEvents: Array<{ noId: string; locale: string }> = [];
    component.reject.subscribe((e) => rejectEvents.push(e));

    const btn = fixture.nativeElement.querySelector('[data-testid="descartar-btn"]') as HTMLButtonElement;
    btn.click();

    expect(rejectEvents).toHaveLength(1);
    expect(rejectEvents[0].noId).toBe(rascunhoNode.noId);
    expect(rejectEvents[0].locale).toBe(rascunhoNode.locale);
  });

  it('should disable Aprovar button while approvalInProgress', () => {
    component.node = { ...rascunhoNode, approvalInProgress: true };
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('[data-testid="aprovar-btn"]') as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
  });

  // ============================================================
  // P7 (FE-024): optimistic spinner in approved state
  // ============================================================

  it('FE-024: should show optimistic-pending spinner when confirmadoCuidadora=true and approvalInProgress=true', () => {
    // Optimistic: backend confirmed fired, UI shows approved badge + spinner
    component.node = { ...approvedNode, approvalInProgress: true };
    fixture.detectChanges();

    // Badge still visible (optimistic approval shown)
    const badge = fixture.nativeElement.querySelector('[data-testid="aprovado-badge"]') as HTMLElement;
    expect(badge).toBeTruthy();

    // Spinner visible (pending confirmation from backend)
    const spinner = fixture.nativeElement.querySelector('[data-testid="optimistic-pending"]') as HTMLElement;
    expect(spinner).toBeTruthy();
  });

  it('FE-024: should NOT show optimistic-pending spinner when approvalInProgress=false', () => {
    component.node = approvedNode; // approvalInProgress: false by default
    fixture.detectChanges();

    const spinner = fixture.nativeElement.querySelector('[data-testid="optimistic-pending"]');
    expect(spinner).toBeNull();
  });

  // ============================================================
  // INV-1 structural assertions (ADR-007 FE / TREI-FE-019)
  // ============================================================

  it('INV-1: should NOT have an @Output named confirmadoPaciente', () => {
    // Only allowed outputs: approve, reject
    // This test fails at build time if the property is ever added.
    expect((component as Record<string, unknown>)['confirmadoPaciente']).toBeUndefined();
    expect((component as Record<string, unknown>)['approvePatient']).toBeUndefined();
  });

  it('INV-1: approve output must exist; reject output must exist', () => {
    expect(component.approve).toBeDefined();
    expect(component.reject).toBeDefined();
  });

  it('INV-1: DualConfirmationComponent receives only caregiverApproved (no confirmadoPaciente binding)', () => {
    component.node = rascunhoNode;
    fixture.detectChanges();
    // DualConfirmationComponent rendered inside the card
    const dualConfirm = fixture.nativeElement.querySelector('[data-testid="dual-confirmation"]') as HTMLElement;
    expect(dualConfirm).toBeTruthy();
    // Patient-pending indicator is always shown (static — INV-1 enforced structurally)
    const patientEl = dualConfirm.querySelector('[data-testid="paciente-pending"]') as HTMLElement;
    expect(patientEl).toBeTruthy();
    // No element references or displays confirmadoPaciente value dynamically
    expect(dualConfirm.textContent).not.toContain('confirmadoPaciente');
  });
});
