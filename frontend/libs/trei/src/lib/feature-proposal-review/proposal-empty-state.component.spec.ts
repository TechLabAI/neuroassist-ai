import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { ProposalEmptyStateComponent } from './proposal-empty-state.component';

describe('ProposalEmptyStateComponent', () => {
  let fixture: ComponentFixture<ProposalEmptyStateComponent>;
  let component: ProposalEmptyStateComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProposalEmptyStateComponent, RouterTestingModule],
    }).compileComponents();

    fixture = TestBed.createComponent(ProposalEmptyStateComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render the empty state container', () => {
    const el = fixture.nativeElement.querySelector('[data-testid="proposal-empty-state"]') as HTMLElement;
    expect(el).toBeTruthy();
  });

  it('should render the session navigation link', () => {
    const link = fixture.nativeElement.querySelector('[data-testid="go-to-session-link"]') as HTMLAnchorElement;
    expect(link).toBeTruthy();
  });

  it('should not render any input or button that could trigger patient actions', () => {
    // INV-1 structural check: no action to write confirmadoPaciente
    const buttons = fixture.nativeElement.querySelectorAll('button') as NodeList;
    expect(buttons.length).toBe(0);
  });
});
