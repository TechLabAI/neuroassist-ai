import { TestBed } from '@angular/core/testing';
import { WakeLockService } from './wake-lock.service';

// =============================================================================
// WakeLockService — unit tests (GEST-FE-031)
//
// O que estes testes protegem:
//   1. API ausente → apiIndisponivel = true; nenhuma exceção; ativo = false
//   2. API presente → acquire() concede lock; ativo = true
//   3. release() → ativo = false
//   4. Evento 'release' do browser → ativo = false (lock foi liberado externamente)
//   5. reacquire() com document.hidden = false e sem lock → tenta adquirir
//   6. reacquire() quando já há lock ativo → não tenta adquirir novamente
//   7. reacquire() com document.hidden = true → não tenta adquirir
//
// Mutação confirmada: remover `_apiIndisponivel.set(true)` no branch !isSupported
// faz o teste 1 reprovar — o campo permanece false.
// =============================================================================

// ---------------------------------------------------------------------------
// Fábrica de sentinel mock
// ---------------------------------------------------------------------------

interface MockSentinel {
  released: boolean;
  _listeners: Map<string, (() => void)[]>;
  addEventListener(type: string, cb: () => void): void;
  release: jest.Mock<Promise<void>>;
  /** Disparar 'release' manualmente (simula o browser liberando o lock). */
  triggerRelease(): void;
}

function makeSentinel(): MockSentinel {
  const listeners = new Map<string, (() => void)[]>();
  return {
    released: false,
    _listeners: listeners,
    addEventListener(type: string, cb: () => void) {
      const list = listeners.get(type) ?? [];
      list.push(cb);
      listeners.set(type, list);
    },
    release: jest.fn().mockResolvedValue(undefined),
    triggerRelease() {
      (listeners.get('release') ?? []).forEach(cb => cb());
    },
  };
}

// ---------------------------------------------------------------------------
// Auxiliares de setup
// ---------------------------------------------------------------------------

function setWakeLockApi(sentinel: MockSentinel | null): void {
  Object.defineProperty(navigator, 'wakeLock', {
    value: sentinel ? { request: jest.fn().mockResolvedValue(sentinel) } : undefined,
    configurable: true,
    writable: true,
  });
}

function setDocumentHidden(hidden: boolean): void {
  Object.defineProperty(document, 'hidden', {
    value: hidden,
    configurable: true,
    writable: true,
  });
}

function createService(): WakeLockService {
  TestBed.configureTestingModule({ providers: [WakeLockService] });
  return TestBed.inject(WakeLockService);
}

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

describe('WakeLockService', () => {
  afterEach(() => {
    // Limpar stubs entre testes para não vazar
    Object.defineProperty(navigator, 'wakeLock', {
      value: undefined,
      configurable: true,
      writable: true,
    });
    Object.defineProperty(document, 'hidden', {
      value: false,
      configurable: true,
      writable: true,
    });
  });

  // ── Teste 1: API ausente ─────────────────────────────────────────────────

  it('declara apiIndisponivel = true e nao lanca quando a API nao existe', async () => {
    setWakeLockApi(null);
    const service = createService();

    await expect(service.acquire()).resolves.toBeUndefined();

    expect(service.apiIndisponivel()).toBe(true);
    expect(service.ativo()).toBe(false);
  });

  // ── Teste 2: API presente — acquire() ───────────────────────────────────

  it('adquire o lock e ativo = true quando a API esta disponivel', async () => {
    const sentinel = makeSentinel();
    setWakeLockApi(sentinel);
    const service = createService();

    await service.acquire();

    expect(service.ativo()).toBe(true);
    expect(service.apiIndisponivel()).toBe(false);
  });

  // ── Teste 3: release() → ativo = false ─────────────────────────────────

  it('ativo fica false apos release() explicito', async () => {
    const sentinel = makeSentinel();
    setWakeLockApi(sentinel);
    const service = createService();

    await service.acquire();
    expect(service.ativo()).toBe(true);

    service.release();
    expect(service.ativo()).toBe(false);
    expect(sentinel.release).toHaveBeenCalledTimes(1);
  });

  // ── Teste 4: evento 'release' do browser → ativo = false ───────────────

  it('ativo fica false quando o browser libera o lock (ex: aba oculta)', async () => {
    const sentinel = makeSentinel();
    setWakeLockApi(sentinel);
    const service = createService();

    await service.acquire();
    expect(service.ativo()).toBe(true);

    // Simular o browser liberando o lock (visibilitychange ou navegação)
    sentinel.triggerRelease();

    expect(service.ativo()).toBe(false);
  });

  // ── Teste 5: reacquire() quando visível e sem lock ─────────────────────

  it('reacquire() adquire lock quando documento nao esta oculto e lock nao esta ativo', async () => {
    const sentinel = makeSentinel();
    setWakeLockApi(sentinel);
    setDocumentHidden(false);
    const service = createService();

    expect(service.ativo()).toBe(false);
    await service.reacquire();
    expect(service.ativo()).toBe(true);
  });

  // ── Teste 6: reacquire() quando lock já ativo ──────────────────────────

  it('reacquire() nao tenta adquirir novamente quando ja ha lock ativo', async () => {
    const sentinel = makeSentinel();
    setWakeLockApi(sentinel);
    setDocumentHidden(false);
    const service = createService();

    await service.acquire();
    const requestFn = (navigator.wakeLock as { request: jest.Mock }).request;
    const callsBefore = requestFn.mock.calls.length;

    await service.reacquire(); // deve ser no-op
    expect(requestFn.mock.calls.length).toBe(callsBefore);
  });

  // ── Teste 7: reacquire() quando aba oculta ─────────────────────────────

  it('reacquire() nao tenta adquirir quando document.hidden = true', async () => {
    const sentinel = makeSentinel();
    setWakeLockApi(sentinel);
    setDocumentHidden(true);
    const service = createService();

    await service.reacquire();
    expect(service.ativo()).toBe(false);
  });
});
