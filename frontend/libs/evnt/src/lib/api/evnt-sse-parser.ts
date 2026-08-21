import { SseFrame, SseEventKind } from './evnt-client.port';

// =============================================================================
// evnt-sse-parser — INT-FE-EVNT-002 / P6-B
// Pure SSE line parser — no DI, testable in isolation.
//
// SSE wire format (RFC 8895):
//   event: evento.created\n
//   id: 42\n
//   data: {"eventoId":"...","urgencia":"emergencia",...}\n
//   \n         ← empty line signals end of event block
//
// Accumulation model:
//   - Caller maintains a SseBuffer across calls.
//   - parseSseLine() mutates the buffer for `data:` and `id:` fields.
//   - On empty line: serialises the accumulated data lines into an SseFrame,
//     resets the buffer's data lines, and returns the frame.
//   - All other calls return null.
// =============================================================================

/** Mutable buffer shared across parseSseLine() calls for a single SSE connection. */
export interface SseBuffer {
  dataLines: string[];
  lastId: string | null;
  eventType: string | null;
}

/** Create a fresh SseBuffer for a new SSE connection. */
export function createSseBuffer(): SseBuffer {
  return { dataLines: [], lastId: null, eventType: null };
}

/**
 * Process one SSE text line and update the buffer.
 *
 * Returns an SseFrame when an empty line signals the end of an event block
 * and there is at least one accumulated data line. Returns null otherwise.
 *
 * @param line - One raw SSE text line (newline already stripped by caller).
 * @param buffer - Mutable accumulation buffer (mutated in place).
 */
export function parseSseLine(line: string, buffer: SseBuffer): SseFrame | null {
  // Comment line — ignore
  if (line.startsWith(':')) {
    return null;
  }

  // Empty line → dispatch event
  if (line === '') {
    if (buffer.dataLines.length === 0) {
      return null;
    }

    const rawData = buffer.dataLines.join('\n');
    // Reset data lines (keep lastId across events — RFC 8895 §9.2.4)
    buffer.dataLines = [];

    const kind = (buffer.eventType ?? 'evento.created') as SseEventKind;
    buffer.eventType = null;

    // Parse JSON payload if present
    let payload: SseFrame['payload'] = null;
    if (rawData.trim()) {
      try {
        payload = JSON.parse(rawData);
      } catch {
        // Unparseable data (e.g. keepalive plain text) → treat as keepalive
      }
    }

    const seq = buffer.lastId !== null ? parseInt(buffer.lastId, 10) || 0 : 0;

    // Keepalive frames have no payload
    const isKeepalive = kind === 'keepalive' || payload === null;

    return {
      kind: isKeepalive ? 'keepalive' : kind,
      seq,
      payload: isKeepalive ? null : payload,
    };
  }

  // Field lines
  const colonIdx = line.indexOf(':');
  if (colonIdx === -1) {
    // Field name only (no colon) — treat as empty value
    return null;
  }

  const field = line.slice(0, colonIdx).trim();
  const value = line.slice(colonIdx + 1).replace(/^ /, ''); // strip single leading space per RFC

  switch (field) {
    case 'data':
      buffer.dataLines.push(value);
      break;
    case 'id':
      buffer.lastId = value;
      break;
    case 'event':
      buffer.eventType = value;
      break;
    case 'retry':
      // Reconnect timer — ignored at parser level; handled by adapter
      break;
    default:
      // Unknown field — ignore per RFC 8895 §9.2.5
      break;
  }

  return null;
}
