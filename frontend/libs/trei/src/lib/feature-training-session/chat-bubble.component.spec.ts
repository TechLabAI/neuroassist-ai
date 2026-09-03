import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChatBubbleComponent } from './chat-bubble.component';
import { ChatMessage } from '../trei.types';

describe('ChatBubbleComponent', () => {
  let fixture: ComponentFixture<ChatBubbleComponent>;
  let component: ChatBubbleComponent;

  const caregiverTurn: ChatMessage = {
    id: 'msg-1',
    role: 'caregiver',
    content: 'Olá assistente',
    timestamp: '2026-01-01T00:00:00Z',
  };

  const assistantTurn: ChatMessage = {
    id: 'msg-2',
    role: 'assistant',
    content: 'Aqui estão as propostas',
    timestamp: '2026-01-01T00:00:01Z',
    aiDisclosed: true,
    proposals: [{ propostaId: 'p-1', texto: 'Quero água', tipo: 'frase', locale: 'pt' }],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChatBubbleComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ChatBubbleComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    component.turn = caregiverTurn;
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should render caregiver bubble aligned to the right', () => {
    component.turn = caregiverTurn;
    fixture.detectChanges();

    const bubble = fixture.nativeElement.querySelector('[data-testid="msg-caregiver"]') as HTMLElement;
    expect(bubble).toBeTruthy();
    expect(bubble.classList).toContain('justify-end');
  });

  it('should render assistant bubble aligned to the left', () => {
    component.turn = assistantTurn;
    fixture.detectChanges();

    const bubble = fixture.nativeElement.querySelector('[data-testid="msg-assistant"]') as HTMLElement;
    expect(bubble).toBeTruthy();
    expect(bubble.classList).toContain('justify-start');
  });

  it('should show AI disclosure badge when aiDisclosed is true', () => {
    component.turn = assistantTurn;
    fixture.detectChanges();

    const badge = fixture.nativeElement.querySelector('.material-symbols-outlined');
    expect(badge).toBeTruthy();
    const badgeParent = badge.parentElement as HTMLElement;
    expect(badgeParent.textContent).toContain('Gerado por IA');
  });

  it('should not show AI disclosure badge on caregiver turns', () => {
    component.turn = caregiverTurn;
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).not.toContain('Gerado por IA');
  });

  it('should render proposals list when proposals are present', () => {
    component.turn = assistantTurn;
    fixture.detectChanges();

    const proposalList = fixture.nativeElement.querySelector('[data-testid="proposals-list"]');
    expect(proposalList).toBeTruthy();
    expect(proposalList.textContent).toContain('Quero água');
  });
});
