// =============================================================================
// acs-cuidador-mock.adapter.spec.ts — FE-ACS-020
//
// Tests: AcsCuidadorMockAdapter list(), create(), deactivate() behavior.
// Uses jest.useFakeTimers() to advance RxJS timer() delays synchronously so
// lastValueFrom() resolves without real async delays.
// =============================================================================

import { lastValueFrom } from 'rxjs';
import { AcsCuidadorMockAdapter } from './acs-cuidador-mock.adapter';

describe('AcsCuidadorMockAdapter — FE-ACS-020', () => {
  let adapter: AcsCuidadorMockAdapter;

  beforeEach(() => {
    adapter = new AcsCuidadorMockAdapter();
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  // ---------------------------------------------------------------------------
  // list()
  // ---------------------------------------------------------------------------
  describe('list()', () => {
    it('returns at least 1 item (seed: Jamilly Amaral)', async () => {
      const p = lastValueFrom(adapter.list());
      jest.advanceTimersByTime(401);
      const items = await p;
      expect(items.length).toBeGreaterThanOrEqual(1);
    });

    it('seed contains Jamilly Amaral with papel=cuidadora', async () => {
      const p = lastValueFrom(adapter.list());
      jest.advanceTimersByTime(401);
      const items = await p;
      const jamilly = items.find((c) => c.nome === 'Jamilly Amaral');
      expect(jamilly).toBeDefined();
      expect(jamilly!.papel).toBe('cuidadora');
      expect(jamilly!.email).toBe('jamilly.amaral@example.com');
    });

    it('Jamilly seed has status=active and tenantId=tenant-familia-amaral', async () => {
      const p = lastValueFrom(adapter.list());
      jest.advanceTimersByTime(401);
      const items = await p;
      const jamilly = items.find((c) => c.id === 'cuid-jamilly');
      expect(jamilly).toBeDefined();
      expect(jamilly!.status).toBe('active');
      expect(jamilly!.tenantId).toBe('tenant-familia-amaral');
    });
  });

  // ---------------------------------------------------------------------------
  // create()
  // ---------------------------------------------------------------------------
  describe('create(dto)', () => {
    it('returns new CuidadorDto with non-null id', async () => {
      const p = lastValueFrom(
        adapter.create({ nome: 'Novo Cuidador', email: 'novo@test.com', papel: 'cuidadora' }),
      );
      jest.advanceTimersByTime(801);
      const result = await p;
      expect(result.id).toBeTruthy();
    });

    it('returned dto has nome and email matching the input dto', async () => {
      const p = lastValueFrom(
        adapter.create({ nome: 'Maria Silva', email: 'maria@test.com', papel: 'coordenador_clinica' }),
      );
      jest.advanceTimersByTime(801);
      const result = await p;
      expect(result.nome).toBe('Maria Silva');
      expect(result.email).toBe('maria@test.com');
      expect(result.papel).toBe('coordenador_clinica');
    });

    it('newly created cuidador has status=active', async () => {
      const p = lastValueFrom(
        adapter.create({ nome: 'Test', email: 'test@test.com', papel: 'cuidadora' }),
      );
      jest.advanceTimersByTime(801);
      const result = await p;
      expect(result.status).toBe('active');
    });

    it('id is unique across consecutive creates', async () => {
      const p1 = lastValueFrom(
        adapter.create({ nome: 'A', email: 'a@test.com', papel: 'cuidadora' }),
      );
      jest.advanceTimersByTime(801);
      const r1 = await p1;

      jest.useRealTimers();
      jest.useFakeTimers();

      const p2 = lastValueFrom(
        adapter.create({ nome: 'B', email: 'b@test.com', papel: 'cuidadora' }),
      );
      jest.advanceTimersByTime(801);
      const r2 = await p2;

      expect(r1.id).not.toBe(r2.id);
    });
  });

  // ---------------------------------------------------------------------------
  // deactivate()
  // ---------------------------------------------------------------------------
  describe('deactivate(id)', () => {
    it('returns cuidador with status=inactive', async () => {
      // Use seed id 'cuid-jamilly' — this is the last test in the suite so mutation is safe
      const p = lastValueFrom(adapter.deactivate('cuid-jamilly'));
      jest.advanceTimersByTime(601);
      const result = await p;
      expect(result.status).toBe('inactive');
      expect(result.id).toBe('cuid-jamilly');
    });

    it('throws an error for unknown id', async () => {
      const p = lastValueFrom(adapter.deactivate('non-existent-id-xyz'));
      jest.advanceTimersByTime(601);
      await expect(p).rejects.toThrow('mock: cuidador not found: non-existent-id-xyz');
    });
  });
});
