import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ApprovalSummaryBarComponent } from './approval-summary-bar.component';

describe('ApprovalSummaryBarComponent', () => {
  let fixture: ComponentFixture<ApprovalSummaryBarComponent>;
  let component: ApprovalSummaryBarComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ApprovalSummaryBarComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ApprovalSummaryBarComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should render the summary bar container', () => {
    fixture.detectChanges();
    const bar = fixture.nativeElement.querySelector('[data-testid="approval-summary-bar"]') as HTMLElement;
    expect(bar).toBeTruthy();
  });

  it('should display rascunhoCount', () => {
    component.rascunhoCount = 3;
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('[data-testid="rascunho-count"]') as HTMLElement;
    expect(el.textContent?.trim()).toBe('3');
  });

  it('should display aguardandoCount', () => {
    component.aguardandoCount = 2;
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('[data-testid="aguardando-count"]') as HTMLElement;
    expect(el.textContent?.trim()).toBe('2');
  });

  it('should default to zero counts', () => {
    fixture.detectChanges();
    const rascunho = fixture.nativeElement.querySelector('[data-testid="rascunho-count"]') as HTMLElement;
    const aguardando = fixture.nativeElement.querySelector('[data-testid="aguardando-count"]') as HTMLElement;
    expect(rascunho.textContent?.trim()).toBe('0');
    expect(aguardando.textContent?.trim()).toBe('0');
  });
});
