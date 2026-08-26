import { TestBed } from '@angular/core/testing';
import { DetectionProfileStore } from './detection-profile.store';
import { DetectionProfileHttpAdapter } from './detection-profile-http.adapter';
import type { DetectionProfileDto } from './detection-profile.port';
import { CAMPOS_ESCALA_BLENDSHAPE, LIMIARES_PADRAO } from '@neuroassist/shared/contracts';

// =============================================================================
// DetectionProfileStore — unit tests (GEST-FE-028)
//
// O que estes testes protegem:
//   1. Sem perfil → degradacao().semPerfil = true; primitivos() = []
//   2. origem: INDISPONIVEL → primitivo excluído; degradacao().primitivosIndisponiveis inclui nome
//   3. engineFeatureRef desconhecido → descartado; degradacao().primitivosDesconhecidos inclui nome
//   4. 304 → perfil em memória preservado (não sobrescrito)
//   5. Limiares corretos por faixa de sensibilidade
//   6. degradado() = true em qualquer cenário de degradação
//   7. forceReload() zera ETag antes de buscar (chama com currentEtag=undefined)
// =============================================================================

const DEVICE_ID = 'b0000000-0000-0000-0000-000000000042';

const THRESHOLDS_MEDIA = {
  earClose: 0.22,
  earOpen: 0.27,
  earOtherMax: 0.30,
  blinkMinFrames: 2,
  doubleBlinkWindowMs: 2000,
  briRaiseOn: 0.30,
  briRaiseOff: 0.22,
  briOtherMax: 0.25,
  browMinFrames: 5,
};
const THRESHOLDS_ALTA = {
  earClose: 0.25,
  earOpen: 0.30,
  earOtherMax: 0.33,
  blinkMinFrames: 2,
  doubleBlinkWindowMs: 2500,
  briRaiseOn: 0.26,
  briRaiseOff: 0.18,
  briOtherMax: 0.21,
  browMinFrames: 3,
};
const THRESHOLDS_BAIXA = {
  earClose: 0.19,
  earOpen: 0.24,
  earOtherMax: 0.27,
  blinkMinFrames: 3,
  doubleBlinkWindowMs: 1500,
  briRaiseOn: 0.34,
  briRaiseOff: 0.26,
  briOtherMax: 0.29,
  browMinFrames: 7,
};

const FIXTURE_PERFIL: DetectionProfileDto = {
  id: 'c0000000-0000-0000-0000-000000000001',
  version: 7,
  active: true,
  contractVersion: '1.0.0',
  thresholds: { baixa: THRESHOLDS_BAIXA, media: THRESHOLDS_MEDIA, alta: THRESHOLDS_ALTA },
  primitives: [
    {
      tipoGestoId: 'd0000000-0000-0000-0000-000000000001',
      nome: 'Piscar duplo',
      engineFeatureRef: 'blink_double_v1',
      sensibilidade: 'media',
      origem: 'PACIENTE',
    },
    {
      tipoGestoId: 'd0000000-0000-0000-0000-000000000002',
      nome: 'Sobrancelha dupla',
      engineFeatureRef: 'brow_raise_both_v1',
      sensibilidade: 'alta',
      origem: 'PERFIL_EMPRESA',
    },
  ],
  pacienteId: 'e0000000-0000-0000-0000-000000000001',
  catalogoVersao: 3,
  catalogoDegradado: false,
  publishedAt: '2026-08-25T10:00:00Z',
  etag: '"v7-e0000001"',
};

function criarAdapterFake() {
  return {
    fetchProfile: jest.fn<Promise<DetectionProfileDto | { notModified: true }>, [string, string?]>(),
  } as unknown as DetectionProfileHttpAdapter;
}

function setupStore(adapter: DetectionProfileHttpAdapter): DetectionProfileStore {
  TestBed.configureTestingModule({
    providers: [
      DetectionProfileStore,
      { provide: DetectionProfileHttpAdapter, useValue: adapter },
    ],
  });
  return TestBed.inject(DetectionProfileStore);
}

describe('DetectionProfileStore', () => {
  let adapterFake: ReturnType<typeof criarAdapterFake>;
  let store: DetectionProfileStore;

  beforeEach(() => {
    adapterFake = criarAdapterFake();
  });

  // ---------------------------------------------------------------------------
  // Estado inicial (sem perfil)
  // ---------------------------------------------------------------------------

  describe('estado inicial', () => {
    beforeEach(() => {
      store = setupStore(adapterFake);
    });

    it('sem perfil: degradacao().semPerfil = true', () => {
      expect(store.degradacao().semPerfil).toBe(true);
    });

    it('sem perfil: primitivos() = []', () => {
      expect(store.primitivos()).toEqual([]);
    });

    it('sem perfil: degradado() = true', () => {
      expect(store.degradado()).toBe(true);
    });

    it('sem perfil: profileVersion() = null', () => {
      expect(store.profileVersion()).toBeNull();
    });
  });

  // ---------------------------------------------------------------------------
  // Carga inicial (200)
  // ---------------------------------------------------------------------------

  describe('loadProfile — 200', () => {
    beforeEach(() => {
      (adapterFake.fetchProfile as jest.Mock).mockResolvedValue(FIXTURE_PERFIL);
      store = setupStore(adapterFake);
    });

    it('deve preencher profileVersion() após carga', async () => {
      await store.loadProfile(DEVICE_ID);
      expect(store.profileVersion()).toBe(7);
    });

    it('deve expor primitivos válidos (PACIENTE e PERFIL_EMPRESA)', async () => {
      await store.loadProfile(DEVICE_ID);
      expect(store.primitivos().length).toBe(2);
    });

    it('deve usar os limiares da faixa correta para cada primitivo', async () => {
      await store.loadProfile(DEVICE_ID);
      const prims = store.primitivos();
      const piscarDuplo = prims.find((p) => p.engineFeatureRef === 'blink_double_v1');
      const sobrancelha = prims.find((p) => p.engineFeatureRef === 'brow_raise_both_v1');

      // piscarDuplo usa 'media' → THRESHOLDS_MEDIA
      expect(piscarDuplo?.thresholds.earClose).toBe(THRESHOLDS_MEDIA.earClose);
      expect(piscarDuplo?.thresholds.blinkMinFrames).toBe(THRESHOLDS_MEDIA.blinkMinFrames);

      // sobrancelha usa 'alta' → THRESHOLDS_ALTA
      expect(sobrancelha?.thresholds.earClose).toBe(THRESHOLDS_ALTA.earClose);
      expect(sobrancelha?.thresholds.browMinFrames).toBe(THRESHOLDS_ALTA.browMinFrames);
    });

    it('após carga com perfil válido: degradado() = false', async () => {
      await store.loadProfile(DEVICE_ID);
      expect(store.degradado()).toBe(false);
    });
  });

  // ---------------------------------------------------------------------------
  // origem: INDISPONIVEL
  // ---------------------------------------------------------------------------

  describe('origem: INDISPONIVEL', () => {
    const perfilComIndisponivel: DetectionProfileDto = {
      ...FIXTURE_PERFIL,
      primitives: [
        ...FIXTURE_PERFIL.primitives,
        {
          tipoGestoId: 'd0000000-0000-0000-0000-000000000099',
          nome: 'Gesto sem catálogo',
          engineFeatureRef: 'blink_left_v1',
          sensibilidade: 'media',
          origem: 'INDISPONIVEL',
        },
      ],
    };

    beforeEach(() => {
      (adapterFake.fetchProfile as jest.Mock).mockResolvedValue(perfilComIndisponivel);
      store = setupStore(adapterFake);
    });

    it('primitivo INDISPONIVEL não aparece em primitivos()', async () => {
      await store.loadProfile(DEVICE_ID);
      const refs = store.primitivos().map((p) => p.engineFeatureRef);
      expect(refs).not.toContain('blink_left_v1');
    });

    it('degradacao().primitivosIndisponiveis inclui o nome do gesto', async () => {
      await store.loadProfile(DEVICE_ID);
      expect(store.degradacao().primitivosIndisponiveis).toContain('Gesto sem catálogo');
    });

    it('degradado() = true quando há INDISPONIVEL', async () => {
      await store.loadProfile(DEVICE_ID);
      expect(store.degradado()).toBe(true);
    });
  });

  // ---------------------------------------------------------------------------
  // engineFeatureRef desconhecido
  // ---------------------------------------------------------------------------

  describe('engineFeatureRef desconhecido', () => {
    const perfilComDesconhecido: DetectionProfileDto = {
      ...FIXTURE_PERFIL,
      primitives: [
        ...FIXTURE_PERFIL.primitives,
        {
          tipoGestoId: 'd0000000-0000-0000-0000-000000000088',
          nome: 'Gesto futuro',
          engineFeatureRef: 'gesto_desconhecido_v9',
          sensibilidade: 'media',
          origem: 'PACIENTE',
        },
      ],
    };

    beforeEach(() => {
      (adapterFake.fetchProfile as jest.Mock).mockResolvedValue(perfilComDesconhecido);
      store = setupStore(adapterFake);
    });

    it('primitivo com engineFeatureRef desconhecido não aparece em primitivos()', async () => {
      await store.loadProfile(DEVICE_ID);
      const refs = store.primitivos().map((p) => p.engineFeatureRef);
      expect(refs).not.toContain('gesto_desconhecido_v9');
    });

    it('degradacao().primitivosDesconhecidos inclui o nome do gesto', async () => {
      await store.loadProfile(DEVICE_ID);
      expect(store.degradacao().primitivosDesconhecidos).toContain('Gesto futuro');
    });

    it('degradado() = true quando há engineFeatureRef desconhecido', async () => {
      await store.loadProfile(DEVICE_ID);
      expect(store.degradado()).toBe(true);
    });
  });

  // ---------------------------------------------------------------------------
  // 304 — perfil em memória preservado
  // ---------------------------------------------------------------------------

  describe('304 — perfil em memória preservado', () => {
    beforeEach(() => {
      store = setupStore(adapterFake);
    });

    it('segunda chamada com 304 mantém profileVersion() do carregamento anterior', async () => {
      // Primeira carga: 200 com perfil v7
      (adapterFake.fetchProfile as jest.Mock).mockResolvedValueOnce(FIXTURE_PERFIL);
      await store.loadProfile(DEVICE_ID);
      expect(store.profileVersion()).toBe(7);

      // Segunda carga: 304 — não deve zerar nem substituir
      (adapterFake.fetchProfile as jest.Mock).mockResolvedValueOnce({ notModified: true });
      await store.loadProfile(DEVICE_ID);

      expect(store.profileVersion()).toBe(7);
    });

    it('após 304, primitivos() não é zerado', async () => {
      (adapterFake.fetchProfile as jest.Mock).mockResolvedValueOnce(FIXTURE_PERFIL);
      await store.loadProfile(DEVICE_ID);

      (adapterFake.fetchProfile as jest.Mock).mockResolvedValueOnce({ notModified: true });
      await store.loadProfile(DEVICE_ID);

      expect(store.primitivos().length).toBe(2);
    });
  });

  // ---------------------------------------------------------------------------
  // forceReload — zera ETag antes de buscar
  // ---------------------------------------------------------------------------

  describe('forceReload', () => {
    beforeEach(() => {
      store = setupStore(adapterFake);
    });

    it('deve chamar fetchProfile com currentEtag=undefined (sem If-None-Match)', async () => {
      // Primeiro carregamento com ETag
      (adapterFake.fetchProfile as jest.Mock).mockResolvedValue(FIXTURE_PERFIL);
      await store.loadProfile(DEVICE_ID);

      // forceReload deve ignorar o ETag armazenado
      (adapterFake.fetchProfile as jest.Mock).mockResolvedValue(FIXTURE_PERFIL);
      await store.forceReload(DEVICE_ID);

      const chamadas = (adapterFake.fetchProfile as jest.Mock).mock.calls;
      const ultimaChamada = chamadas[chamadas.length - 1];
      // currentEtag deve ser undefined (null foi zerado)
      expect(ultimaChamada[1]).toBeUndefined();
    });
  });

  // ---------------------------------------------------------------------------
  // Erro de rede
  // ---------------------------------------------------------------------------

  describe('erro de rede', () => {
    beforeEach(() => {
      store = setupStore(adapterFake);
    });

    it('após erro de rede, degradado() permanece true', async () => {
      (adapterFake.fetchProfile as jest.Mock).mockRejectedValue(new Error('network error'));
      await store.loadProfile(DEVICE_ID);
      expect(store.degradado()).toBe(true);
    });

    it('após erro de rede, erro() é preenchido', async () => {
      (adapterFake.fetchProfile as jest.Mock).mockRejectedValue(new Error('network error'));
      await store.loadProfile(DEVICE_ID);
      expect(store.erro()).toBeTruthy();
    });
  });

  // ---------------------------------------------------------------------------
  // LIMIARES_PADRAO como fallback
  // ---------------------------------------------------------------------------

  describe('LIMIARES_PADRAO', () => {
    beforeEach(() => {
      store = setupStore(adapterFake);
    });

    it('limiaresFallback deve ser idêntico ao LIMIARES_PADRAO de shared/contracts', () => {
      expect(store.limiaresFallback).toEqual(LIMIARES_PADRAO);
    });

    it('LIMIARES_PADRAO.earClose deve ser 0.22 (faixa media do backend)', () => {
      expect(LIMIARES_PADRAO.earClose).toBe(0.22);
    });

    it('LIMIARES_PADRAO.blinkMinFrames deve ser 2', () => {
      expect(LIMIARES_PADRAO.blinkMinFrames).toBe(2);
    });

    it('LIMIARES_PADRAO.doubleBlinkWindowMs deve ser 2000', () => {
      expect(LIMIARES_PADRAO.doubleBlinkWindowMs).toBe(2000);
    });

    // -------------------------------------------------------------------------
    // Os campos de sobrancelha NÃO seguem o backend — e isso é o que se afirma.
    //
    // A primeira versão destes dois testes exigia 0.30 e 0.25, os valores do
    // backend. Não foi um teste que "quebrou": ele estava fixando uma mudança
    // clínica feita por coincidência de nomenclatura.
    //
    // Este motor compara `briRaiseOn`/`briOtherMax` com BLENDSHAPES do MediaPipe
    // (browInnerUp, browOuterUp*), ativações 0..1 de rede neural. O backend os
    // calibra para BRI — (eye_top_y − brow_peak_y) / inter_eye_dist, uma razão
    // geométrica. Escalas diferentes; o número de um não vale no outro.
    //
    // 0.40 e 0.18 são os do protótipo, validados em hardware real. Ficam até o
    // backend publicar uma faixa calibrada para blendshapes.
    // -------------------------------------------------------------------------

    it('briRaiseOn mantém a escala de blendshape (0.40), não a de BRI do backend', () => {
      expect(LIMIARES_PADRAO.briRaiseOn).toBeCloseTo(0.4);
      expect(LIMIARES_PADRAO.briRaiseOn).not.toBeCloseTo(0.3);
    });

    it('briOtherMax mantém a escala de blendshape (0.18), não a de BRI do backend', () => {
      expect(LIMIARES_PADRAO.briOtherMax).toBeCloseTo(0.18);
      expect(LIMIARES_PADRAO.briOtherMax).not.toBeCloseTo(0.25);
    });

    it('os campos de escala de blendshape estão declarados, para o guarda de drift', () => {
      // Se alguém acrescentar um campo comparado contra blendshape e esquecer de
      // declará-lo aqui, o guarda passará a compará-lo com o backend e o
      // "alinhamento" silencioso volta a acontecer.
      expect([...CAMPOS_ESCALA_BLENDSHAPE].sort()).toEqual(
        ['briOtherMax', 'briRaiseOff', 'briRaiseOn', 'earOtherMax'],
      );
    });
  });

  // ---------------------------------------------------------------------------
  // Mutação — confirmação de vermelho
  // ---------------------------------------------------------------------------
  // Confirmado manualmente: ao trocar toBe(7) por toBe(99) na linha de profileVersion(),
  // o teste "deve preencher profileVersion() após carga" falha. O guarda é real.
});
