import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AssistantThinkingIndicatorComponent } from './assistant-thinking-indicator.component';

describe('AssistantThinkingIndicatorComponent', () => {
  let fixture: ComponentFixture<AssistantThinkingIndicatorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AssistantThinkingIndicatorComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AssistantThinkingIndicatorComponent);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the testid and "thinking" text', () => {
    const el = fixture.nativeElement.querySelector('[data-testid="assistant-thinking"]') as HTMLElement;
    expect(el).toBeTruthy();
    expect(el.textContent).toContain('O assistente está pensando');
  });

  it('should render three bounce dots for the animation', () => {
    const dots = fixture.nativeElement.querySelectorAll('.animate-bounce') as NodeList;
    expect(dots.length).toBe(3);
  });
});
