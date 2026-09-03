import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChatComposerComponent } from './chat-composer.component';

describe('ChatComposerComponent', () => {
  let fixture: ComponentFixture<ChatComposerComponent>;
  let component: ChatComposerComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChatComposerComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ChatComposerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should emit submitTurn with trimmed text when send button is clicked', () => {
    const spy = jest.spyOn(component.submitTurn, 'emit');
    const input = fixture.nativeElement.querySelector('[data-testid="composer-input"]') as HTMLInputElement;

    // Simulate typing via ngModel
    input.value = '  Quero água  ';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('[data-testid="send-btn"]') as HTMLButtonElement;
    btn.click();
    fixture.detectChanges();

    expect(spy).toHaveBeenCalledWith('Quero água');
  });

  it('should not emit submitTurn when input is empty', () => {
    const spy = jest.spyOn(component.submitTurn, 'emit');
    const btn = fixture.nativeElement.querySelector('[data-testid="send-btn"]') as HTMLButtonElement;
    btn.click();
    expect(spy).not.toHaveBeenCalled();
  });

  it('should not emit submitTurn when disabled=true even with text', () => {
    // With disabled=true, onSubmit() returns early — no emit even if text is present
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();

    const spy = jest.spyOn(component.submitTurn, 'emit');
    // Access the protected method via cast to bypass access modifier in tests
    (component as unknown as { text: string }).text = 'Hello';
    (component as unknown as { onSubmit: () => void }).onSubmit();

    expect(spy).not.toHaveBeenCalled();
  });
});
