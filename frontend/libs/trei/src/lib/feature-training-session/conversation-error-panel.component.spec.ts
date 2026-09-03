import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConversationErrorPanelComponent } from './conversation-error-panel.component';
import { AppError } from '../trei.types';

describe('ConversationErrorPanelComponent', () => {
  let fixture: ComponentFixture<ConversationErrorPanelComponent>;
  let component: ConversationErrorPanelComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConversationErrorPanelComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ConversationErrorPanelComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display error message text when error is provided', () => {
    const error: AppError = {
      code: 'HTTP_503',
      message: 'Serviço temporariamente indisponível',
      correlationId: 'corr-abc-123',
    };
    fixture.componentRef.setInput('error', error);
    fixture.detectChanges();

    const msg = fixture.nativeElement.querySelector('[data-testid="error-message"]') as HTMLElement;
    expect(msg).toBeTruthy();
    expect(msg.textContent?.trim()).toBe('Serviço temporariamente indisponível');
  });

  it('should NEVER render correlationId in visible text (G5)', () => {
    const error: AppError = {
      code: 'HTTP_500',
      message: 'Erro interno',
      correlationId: 'corr-secret-xyz',
    };
    fixture.componentRef.setInput('error', error);
    fixture.detectChanges();

    const allText = fixture.nativeElement.textContent as string;
    expect(allText).not.toContain('corr-secret-xyz');
  });

  it('should emit retry when retry button is clicked', () => {
    const spy = jest.spyOn(component.retry, 'emit');
    const btn = fixture.nativeElement.querySelector('[data-testid="retry-btn"]') as HTMLButtonElement;
    btn.click();
    expect(spy).toHaveBeenCalledTimes(1);
  });
});
