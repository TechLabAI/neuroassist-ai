import { TestBed } from '@angular/core/testing';
import { CameraPreferenceService } from './camera-preference.service';

// ---------------------------------------------------------------------------
// CameraPreferenceService — ADR-007 follow-up 2
// A escolha de câmera é preferência de máquina (localStorage), nunca estado de
// domínio: o deviceId do browser é escopado por origem+permissão e rotaciona.
// ---------------------------------------------------------------------------

const STORAGE_KEY = 'neuroassist.gest.cameraDeviceId';

function mockDevices(devices: Array<Partial<MediaDeviceInfo>>): jest.Mock {
  const enumerateDevices = jest.fn().mockResolvedValue(devices);
  Object.defineProperty(global.navigator, 'mediaDevices', {
    value: { enumerateDevices },
    configurable: true,
  });
  return enumerateDevices;
}

function novoServico(): CameraPreferenceService {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ providers: [CameraPreferenceService] });
  return TestBed.inject(CameraPreferenceService);
}

describe('CameraPreferenceService', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('lista apenas videoinput', async () => {
    mockDevices([
      { kind: 'videoinput', deviceId: 'cam-1', label: 'FaceTime HD' },
      { kind: 'audioinput', deviceId: 'mic-1', label: 'Microfone' },
      { kind: 'videoinput', deviceId: 'cam-2', label: 'Logitech C920' },
    ]);
    const svc = novoServico();

    await svc.refresh();

    expect(svc.cameras().map((c) => c.deviceId)).toEqual(['cam-1', 'cam-2']);
  });

  // Antes da primeira permissão o browser devolve label vazio (anti-fingerprinting).
  it('numera as câmeras quando o browser ainda não revelou os labels', async () => {
    mockDevices([
      { kind: 'videoinput', deviceId: 'cam-1', label: '' },
      { kind: 'videoinput', deviceId: 'cam-2', label: '' },
    ]);
    const svc = novoServico();

    await svc.refresh();

    expect(svc.cameras().map((c) => c.label)).toEqual(['Câmera 1', 'Câmera 2']);
  });

  it('persiste a escolha e a recupera numa instância nova', async () => {
    mockDevices([{ kind: 'videoinput', deviceId: 'cam-2', label: 'Logitech' }]);
    novoServico().select('cam-2');

    expect(novoServico().selected()).toBe('cam-2');
  });

  it('usa deviceId exact na constraint — calibrar na câmera errada em silêncio seria pior que falhar', () => {
    mockDevices([]);
    const svc = novoServico();

    expect(svc.videoConstraints()).toBe(true);

    svc.select('cam-9');
    expect(svc.videoConstraints()).toEqual({ deviceId: { exact: 'cam-9' } });
  });

  it('descarta a preferência quando a câmera guardada some do sistema', async () => {
    localStorage.setItem(STORAGE_KEY, 'cam-desconectada');
    mockDevices([{ kind: 'videoinput', deviceId: 'cam-1', label: 'Integrada' }]);
    const svc = novoServico();
    expect(svc.selected()).toBe('cam-desconectada');

    await svc.refresh();

    expect(svc.selected()).toBeNull();
    expect(svc.videoConstraints()).toBe(true);
  });

  it('não quebra quando o armazenamento está bloqueado', () => {
    mockDevices([]);
    const svc = novoServico();
    const setItem = jest.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('SecurityError');
    });

    expect(() => svc.select('cam-1')).not.toThrow();
    expect(svc.selected()).toBe('cam-1'); // vale para a sessão, mesmo sem persistir

    setItem.mockRestore();
  });

  it('não quebra quando enumerateDevices não existe', async () => {
    Object.defineProperty(global.navigator, 'mediaDevices', {
      value: undefined,
      configurable: true,
    });
    const svc = novoServico();

    await expect(svc.refresh()).resolves.toBeUndefined();
    expect(svc.cameras()).toEqual([]);
  });
});
