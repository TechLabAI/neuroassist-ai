import { TestBed } from '@angular/core/testing';
import { ObservabilityService } from '@neuroassist/shared/observability';
import { EvntTelemetryService } from './evnt-telemetry.service';

describe('EvntTelemetryService', () => {
  let service: EvntTelemetryService;
  let emitSpy: jest.SpyInstance;

  beforeEach(() => {
    const mockObs = { emit: jest.fn() };

    TestBed.configureTestingModule({
      providers: [
        EvntTelemetryService,
        { provide: ObservabilityService, useValue: mockObs },
      ],
    });

    service = TestBed.inject(EvntTelemetryService);
    emitSpy = jest.spyOn(TestBed.inject(ObservabilityService), 'emit');
  });

  it('trackConfirmSuccess() emits evnt.alarm.confirm.success with opaque eventoId', () => {
    service.trackConfirmSuccess('evt-abc-123');

    expect(emitSpy).toHaveBeenCalledWith({
      event: 'evnt.alarm.confirm.success',
      eventoId: 'evt-abc-123',
    });
  });

  it('trackAlarmReceived("emergencia") emits evnt.alarm.received with urgencia', () => {
    service.trackAlarmReceived('emergencia');

    expect(emitSpy).toHaveBeenCalledWith({
      event: 'evnt.alarm.received',
      urgencia: 'emergencia',
    });
  });

  it('trackDegradedActivated("sse_error") emits evnt.delivery.degraded.activated with reason', () => {
    service.trackDegradedActivated('sse_error');

    expect(emitSpy).toHaveBeenCalledWith({
      event: 'evnt.delivery.degraded.activated',
      reason: 'sse_error',
    });
  });
});
