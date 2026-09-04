// =============================================================================
// TreiTelemetryService spec — TREI-FE-026 / TREI-INT-006
//
// Verifies:
//   1. trackSessionStarted() emits 'trei.session.started' with opaque sessaoId
//   2. trackTurnSubmitted() emits 'trei.turn.submitted' with opaque sessaoId
//   3. trackNodeApproved() emits 'trei.node.approved' with opaque noId
//   4. trackSessionError() emits 'trei.session.error' with errorCode
//   5. trackTurnError() emits 'trei.turn.error' with sessaoId
//   6. trackNodeApprovalError() emits 'trei.node.approval.error' with noId
//   7. SEC-002: no 'patientId' field in any emitted payload
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { TreiTelemetryService } from './trei-telemetry.service';
import { ObservabilityService } from '@neuroassist/shared/observability';

describe('TreiTelemetryService', () => {
  let service: TreiTelemetryService;
  let mockObs: { emit: jest.Mock };

  beforeEach(() => {
    mockObs = { emit: jest.fn() };

    TestBed.configureTestingModule({
      providers: [
        TreiTelemetryService,
        { provide: ObservabilityService, useValue: mockObs },
      ],
    });

    service = TestBed.inject(TreiTelemetryService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  // ---------------------------------------------------------------------------
  // Event contract tests
  // ---------------------------------------------------------------------------

  it('trackSessionStarted() emits trei.session.started with opaque sessaoId', () => {
    service.trackSessionStarted('sess-uuid-001');
    expect(mockObs.emit).toHaveBeenCalledWith({
      event: 'trei.session.started',
      sessaoId: 'sess-uuid-001',
    });
  });

  it('trackTurnSubmitted() emits trei.turn.submitted with opaque sessaoId', () => {
    service.trackTurnSubmitted('sess-uuid-001');
    expect(mockObs.emit).toHaveBeenCalledWith({
      event: 'trei.turn.submitted',
      sessaoId: 'sess-uuid-001',
    });
  });

  it('trackNodeApproved() emits trei.node.approved with opaque noId', () => {
    service.trackNodeApproved('no-uuid-042');
    expect(mockObs.emit).toHaveBeenCalledWith({
      event: 'trei.node.approved',
      noId: 'no-uuid-042',
    });
  });

  it('trackSessionError() emits trei.session.error with errorCode', () => {
    service.trackSessionError('HTTP_404');
    expect(mockObs.emit).toHaveBeenCalledWith({
      event: 'trei.session.error',
      errorCode: 'HTTP_404',
    });
  });

  it('trackTurnError() emits trei.turn.error with sessaoId', () => {
    service.trackTurnError('sess-uuid-001');
    expect(mockObs.emit).toHaveBeenCalledWith({
      event: 'trei.turn.error',
      sessaoId: 'sess-uuid-001',
    });
  });

  it('trackNodeApprovalError() emits trei.node.approval.error with noId', () => {
    service.trackNodeApprovalError('no-uuid-042');
    expect(mockObs.emit).toHaveBeenCalledWith({
      event: 'trei.node.approval.error',
      noId: 'no-uuid-042',
    });
  });

  // ---------------------------------------------------------------------------
  // SEC-002: no patientId in plaintext in any emitted payload
  // ---------------------------------------------------------------------------

  it('SEC-002: trackSessionStarted payload does not contain patientId', () => {
    service.trackSessionStarted('sess-uuid-001');
    const payload = mockObs.emit.mock.calls[0][0];
    expect(payload).not.toHaveProperty('patientId');
    expect(payload).not.toHaveProperty('pacienteId');
  });

  it('SEC-002: trackTurnSubmitted payload does not contain patientId', () => {
    service.trackTurnSubmitted('sess-uuid-001');
    const payload = mockObs.emit.mock.calls[0][0];
    expect(payload).not.toHaveProperty('patientId');
    expect(payload).not.toHaveProperty('pacienteId');
  });

  it('SEC-002: trackNodeApproved payload does not contain patientId', () => {
    service.trackNodeApproved('no-uuid-042');
    const payload = mockObs.emit.mock.calls[0][0];
    expect(payload).not.toHaveProperty('patientId');
    expect(payload).not.toHaveProperty('pacienteId');
  });

  it('SEC-002: trackSessionError payload does not contain patientId', () => {
    service.trackSessionError('UNKNOWN');
    const payload = mockObs.emit.mock.calls[0][0];
    expect(payload).not.toHaveProperty('patientId');
    expect(payload).not.toHaveProperty('pacienteId');
  });
});
