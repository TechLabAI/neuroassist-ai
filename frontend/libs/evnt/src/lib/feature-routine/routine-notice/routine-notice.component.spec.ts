import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RoutineNoticeComponent } from './routine-notice.component';
import { EventoVm } from '../../evnt.types';

const MOCK_EVENTO: EventoVm = {
  eventoId: 'EVT-001',
  urgencia: 'rotina',
  tier: 'discreet',
  commandLabel: 'Ajustar travesseiro',
  patientName: 'Márcio',
  recognizedAt: '2026-08-13T14:00:00Z',
  displayTime: 'às 14h00',
  feedback: 'nao_avaliado',
  confirmadoPor: null,
  confirmadoAt: null,
};

describe('RoutineNoticeComponent', () => {
  let fixture: ComponentFixture<RoutineNoticeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RoutineNoticeComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(RoutineNoticeComponent);
    fixture.componentRef.setInput('evento', MOCK_EVENTO);
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('emits the eventoId via (dismiss) when the dismiss button is clicked', () => {
    const emitted: string[] = [];
    fixture.componentInstance.dismiss.subscribe((id: string) => emitted.push(id));

    const btn = fixture.nativeElement.querySelector(
      '[aria-label^="Dispensar notificação"]',
    ) as HTMLButtonElement;
    expect(btn).toBeTruthy();
    btn.click();
    fixture.detectChanges();

    expect(emitted).toEqual(['EVT-001']);
  });

  it('emits the eventoId via (open) when the "Ver" button is clicked', () => {
    const emitted: string[] = [];
    fixture.componentInstance.open.subscribe((id: string) => emitted.push(id));

    const btn = fixture.nativeElement.querySelector(
      '[aria-label^="Ver evento"]',
    ) as HTMLButtonElement;
    expect(btn).toBeTruthy();
    btn.click();
    fixture.detectChanges();

    expect(emitted).toEqual(['EVT-001']);
  });
});
