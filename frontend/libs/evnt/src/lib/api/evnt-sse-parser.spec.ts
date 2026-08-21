import { parseSseLine, createSseBuffer, SseBuffer } from './evnt-sse-parser';

// =============================================================================
// evnt-sse-parser spec — INT-FE-EVNT-002 / P6-B
// =============================================================================

describe('parseSseLine', () => {
  let buffer: SseBuffer;

  beforeEach(() => {
    buffer = createSseBuffer();
  });

  it('accumulates data: lines and emits SseFrame on empty line', () => {
    // Simulate a full event block
    expect(parseSseLine('event: evento.created', buffer)).toBeNull();
    expect(parseSseLine('id: 7', buffer)).toBeNull();
    expect(
      parseSseLine(
        'data: {"eventoId":"evt-001","urgencia":"emergencia","tier":"full_screen_alarm","commandLabel":"Água","feedback":"nao_avaliado","recognizedAt":"2026-08-16T10:00:00Z"}',
        buffer,
      ),
    ).toBeNull();

    const frame = parseSseLine('', buffer);

    expect(frame).not.toBeNull();
    expect(frame?.kind).toBe('evento.created');
    expect(frame?.seq).toBe(7);
    expect(frame?.payload).toBeDefined();
    expect((frame?.payload as { eventoId: string })?.eventoId).toBe('evt-001');
  });

  it('updates lastId in buffer when id: field is parsed', () => {
    parseSseLine('id: 99', buffer);
    expect(buffer.lastId).toBe('99');
  });

  it('returns null for an empty line when no data lines are accumulated', () => {
    const result = parseSseLine('', buffer);
    expect(result).toBeNull();
  });

  it('returns null for comment lines', () => {
    const result = parseSseLine(': keep-alive ping', buffer);
    expect(result).toBeNull();
  });

  it('emits keepalive frame when event type is keepalive', () => {
    parseSseLine('event: keepalive', buffer);
    parseSseLine('data: ping', buffer);
    const frame = parseSseLine('', buffer);

    expect(frame?.kind).toBe('keepalive');
    expect(frame?.payload).toBeNull();
  });

  it('preserves lastId across multiple event dispatches', () => {
    // First event
    parseSseLine('id: 3', buffer);
    parseSseLine('data: {"eventoId":"e1","urgencia":"rotina","tier":"discreet","commandLabel":"X","feedback":"nao_avaliado","recognizedAt":"2026-08-16T10:00:00Z"}', buffer);
    parseSseLine('', buffer);

    // Second event — no id field (lastId should persist)
    parseSseLine('data: {"eventoId":"e2","urgencia":"rotina","tier":"discreet","commandLabel":"Y","feedback":"nao_avaliado","recognizedAt":"2026-08-16T10:01:00Z"}', buffer);
    const frame = parseSseLine('', buffer);

    // lastId remains '3' because no new id: field was received
    expect(buffer.lastId).toBe('3');
    expect(frame?.seq).toBe(3);
  });
});
