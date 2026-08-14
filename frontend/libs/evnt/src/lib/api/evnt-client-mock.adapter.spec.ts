import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { EvntClientMockAdapter } from './evnt-client-mock.adapter';
import { ActiveEventsDto } from './evnt-client.port';

// =============================================================================
// EvntClientMockAdapter spec — P5 (FE-EVNT-010)
// Validates that the dev/test mock adapter returns seed data in the correct shape.
// Uses fakeAsync + tick() to advance past the simulated 400 ms network latency.
// =============================================================================

describe('EvntClientMockAdapter', () => {
  let adapter: EvntClientMockAdapter;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [EvntClientMockAdapter],
    });
    adapter = TestBed.inject(EvntClientMockAdapter);
  });

  it('listActive() returns ActiveEventsDto with activeEvent of urgencia "emergencia"', fakeAsync(() => {
    let result: ActiveEventsDto | undefined;

    adapter.listActive().subscribe((dto) => {
      result = dto;
    });

    // Advance 400 ms (mock timer delay) so the switchMap emits
    tick(400);

    expect(result).toBeDefined();
    expect(result?.activeEvent).not.toBeNull();
    expect(result?.activeEvent?.urgencia).toBe('emergencia');
    expect(result?.activeEvent?.eventoId).toBe('evt-001');
  }));
});
