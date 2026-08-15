import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EventListComponent } from './event-list.component';
import { EventoVm } from '../../evnt.types';

function mockEvento(id: string): EventoVm {
  return {
    eventoId: id,
    urgencia: 'rotina',
    tier: 'discreet',
    commandLabel: `Comando ${id}`,
    patientName: 'Márcio',
    recognizedAt: '2026-08-13T14:00:00Z',
    displayTime: 'às 14h00',
    feedback: 'nao_avaliado',
    confirmadoPor: null,
    confirmadoAt: null,
  };
}

describe('EventListComponent', () => {
  let fixture: ComponentFixture<EventListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EventListComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(EventListComponent);
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders 3 event cards when events input has 3 items', () => {
    fixture.componentRef.setInput('events', [
      mockEvento('EVT-001'),
      mockEvento('EVT-002'),
      mockEvento('EVT-003'),
    ]);
    fixture.detectChanges();

    const cards = fixture.nativeElement.querySelectorAll(
      'button[aria-label^="Ver detalhe de"]',
    );
    expect(cards.length).toBe(3);
  });

  it('emits the eventoId via (open) when a card is clicked', () => {
    const emitted: string[] = [];
    fixture.componentInstance.open.subscribe((id: string) => emitted.push(id));

    fixture.componentRef.setInput('events', [mockEvento('EVT-042')]);
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector(
      'button[aria-label^="Ver detalhe de"]',
    ) as HTMLButtonElement;
    expect(card).toBeTruthy();
    card.click();
    fixture.detectChanges();

    expect(emitted).toEqual(['EVT-042']);
  });
});
