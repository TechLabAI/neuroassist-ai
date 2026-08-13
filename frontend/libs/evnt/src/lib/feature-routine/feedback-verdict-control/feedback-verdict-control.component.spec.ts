import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeedbackVerdictControl } from './feedback-verdict-control.component';

describe('FeedbackVerdictControl', () => {
  let fixture: ComponentFixture<FeedbackVerdictControl>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FeedbackVerdictControl],
    }).compileComponents();

    fixture = TestBed.createComponent(FeedbackVerdictControl);
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('disables both buttons when disabled input is true (CONCUR-01)', () => {
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();

    const buttons = fixture.nativeElement.querySelectorAll(
      'button',
    ) as NodeListOf<HTMLButtonElement>;
    expect(buttons.length).toBe(2);
    buttons.forEach((btn) => expect(btn.disabled).toBe(true));
  });

  it('emits "real" via (verdict) when the "Real" button is clicked', () => {
    const emitted: string[] = [];
    fixture.componentInstance.verdict.subscribe((v: string) => emitted.push(v));

    const btn = fixture.nativeElement.querySelector(
      'button[aria-label="Marcar como evento real"]',
    ) as HTMLButtonElement;
    expect(btn).toBeTruthy();
    btn.click();
    fixture.detectChanges();

    expect(emitted).toEqual(['real']);
  });
});
