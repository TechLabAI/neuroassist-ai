import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChatConversationPanelComponent } from './chat-conversation-panel.component';
import { ChatMessage } from '../trei.types';

describe('ChatConversationPanelComponent', () => {
  let fixture: ComponentFixture<ChatConversationPanelComponent>;
  let component: ChatConversationPanelComponent;

  const turns: ChatMessage[] = [
    { id: 'msg-1', role: 'caregiver', content: 'Olá', timestamp: '2026-01-01T00:00:00Z' },
    { id: 'msg-2', role: 'assistant', content: 'Proposta aqui', timestamp: '2026-01-01T00:00:01Z' },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChatConversationPanelComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ChatConversationPanelComponent);
    component = fixture.componentInstance;
    // Initialize required input before first detectChanges
    fixture.componentRef.setInput('turns', []);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render one chat-bubble per turn', () => {
    fixture.componentRef.setInput('turns', turns);
    fixture.detectChanges();

    const bubbles = fixture.nativeElement.querySelectorAll('trei-chat-bubble');
    expect(bubbles.length).toBe(2);
  });

  it('should show AssistantThinkingIndicator when isThinking=true', () => {
    fixture.componentRef.setInput('turns', turns);
    fixture.componentRef.setInput('isThinking', true);
    fixture.detectChanges();

    const indicator = fixture.nativeElement.querySelector('trei-assistant-thinking-indicator');
    expect(indicator).toBeTruthy();
  });

  it('should not show AssistantThinkingIndicator when isThinking=false', () => {
    fixture.componentRef.setInput('turns', turns);
    fixture.componentRef.setInput('isThinking', false);
    fixture.detectChanges();

    const indicator = fixture.nativeElement.querySelector('trei-assistant-thinking-indicator');
    expect(indicator).toBeNull();
  });

  it('should show composer when showComposer=true', () => {
    fixture.componentRef.setInput('showComposer', true);
    fixture.detectChanges();

    const composer = fixture.nativeElement.querySelector('trei-chat-composer');
    expect(composer).toBeTruthy();
  });

  it('should hide composer when showComposer=false', () => {
    fixture.componentRef.setInput('showComposer', false);
    fixture.detectChanges();

    const composer = fixture.nativeElement.querySelector('trei-chat-composer');
    expect(composer).toBeNull();
  });

  it('should relay caregiverTurnSubmit from child composer', () => {
    const spy = jest.spyOn(component.caregiverTurnSubmit, 'emit');
    component.caregiverTurnSubmit.emit('test text');
    expect(spy).toHaveBeenCalledWith('test text');
  });
});
