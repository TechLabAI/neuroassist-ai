import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ReportIngressHttpAdapter } from './report-ingress-http.adapter';
import type { ReportRequest } from './report-ingress.port';

// =============================================================================
// ReportQueueService — fila offline de relatos de gesto (GEST-FE-032)
//
// Quando a rede está indisponível, relatos de gesto não são descartados: são
// enfileirados e reenviados assim que a conectividade é restaurada.
//
// Arquitetura:
//   - Fila em memória (Map<reportId, QueuedReport>) como fonte de verdade da sessão.
//   - Persistência em IndexedDB (best-effort): garante sobrevivência a recargas.
//   - Evento 'online' do window dispara processQueue() para reenvio imediato.
//
// Idempotência:
//   reportId é a chave primária. Enfileirar o mesmo reportId duas vezes é no-op.
//   O backend aceita o mesmo reportId com 200 (idempotente) — o reenvio é seguro.
//
// Política de erro no reenvio:
//   - 200 ou 201 → remover da fila (sucesso ou idempotente)
//   - 4xx (exceto 408/429) → remover da fila e registrar (não retentável)
//   - 5xx / 408 / 429 / rede → manter na fila e tentar novamente
//
// Teto da fila (MAX_QUEUE_SIZE):
//   Ao exceder, o relato MAIS ANTIGO é descartado e `overflowDiscardCount` é
//   incrementado. Descarte silencioso mentiria sobre a completude — o contador
//   é exposto na UI para que o operador saiba o que foi perdido.
//
// Janela de observação perdida:
//   `lastParadaSince` registra quando a estação entrou em 'parada'. Ao retornar,
//   o EstacaoComponent exibe a duração da janela sem observação.
//   Atenção: relatos enfileirados não substituem a observação perdida — eles
//   pertencem à janela anterior (rede caída enquanto a câmera estava ativa).
// =============================================================================

export interface QueuedReport {
  readonly reportId: string;
  readonly request: ReportRequest;
  readonly enqueuedAt: string; // ISO 8601
}

/** Distingue motivo de descarte para rastreabilidade. */
export type DiscardReason = 'overflow' | 'non-retryable';

const DB_NAME = 'neuroassist-report-queue-v1';
const STORE_NAME = 'reports';
const MAX_QUEUE_SIZE = 50;

/**
 * Retorna true para erros HTTP que NÃO devem ser tentados novamente.
 * 4xx (exceto 408 e 429) = erro semântico do cliente; retentar não vai funcionar.
 */
export function isNonRetryable(err: unknown): boolean {
  if (err instanceof HttpErrorResponse) {
    return (
      err.status >= 400 &&
      err.status < 500 &&
      err.status !== 408 &&
      err.status !== 429
    );
  }
  return false;
}

@Injectable({ providedIn: 'root' })
export class ReportQueueService {
  private readonly reportAdapter = inject(ReportIngressHttpAdapter);

  // ---------------------------------------------------------------------------
  // Estado em memória (fonte de verdade da sessão)
  // ---------------------------------------------------------------------------

  private readonly _queue = new Map<string, QueuedReport>();

  private readonly _pendingCount = signal(0);
  private readonly _overflowDiscardCount = signal(0);
  private readonly _nonRetryableDiscardCount = signal(0);

  /**
   * Número de relatos aguardando reenvio.
   * Exibir na tela de estação — não em banner de canto.
   */
  readonly pendingCount = this._pendingCount.asReadonly();

  /**
   * Relatos descartados por overflow de fila.
   * Cada incremento representa um pedido de ajuda que NÃO será reenviado.
   */
  readonly overflowDiscardCount = this._overflowDiscardCount.asReadonly();

  /**
   * Relatos removidos da fila por erro não-retentável (4xx sem 408/429).
   */
  readonly nonRetryableDiscardCount = this._nonRetryableDiscardCount.asReadonly();

  /**
   * Contagem total de relatos perdidos (overflow + não-retentáveis).
   * Exposto na UI como "relatos descartados".
   */
  readonly totalDiscardCount = computed(
    () => this._overflowDiscardCount() + this._nonRetryableDiscardCount(),
  );

  // ---------------------------------------------------------------------------
  // Janela de observação perdida
  // ---------------------------------------------------------------------------

  private readonly _lastParadaSince = signal<string | null>(null);

  /**
   * Timestamp (ISO) em que a estação entrou em 'parada' pela última vez.
   * null = nunca pausou nesta sessão (ou retornou de parada).
   */
  readonly lastParadaSince = this._lastParadaSince.asReadonly();

  /** Registra quando a estação entra em 'parada'. Chamado pelo EstacaoComponent. */
  recordParadaStart(): void {
    this._lastParadaSince.set(new Date().toISOString());
  }

  /** Limpa o registro ao sair de 'parada'. Chamado quando retorna a 'vigiando'. */
  clearParadaRecord(): void {
    this._lastParadaSince.set(null);
  }

  // ---------------------------------------------------------------------------
  // Estado de processamento (evita processamento concorrente)
  // ---------------------------------------------------------------------------

  private isProcessing = false;

  // ---------------------------------------------------------------------------
  // Inicialização
  // ---------------------------------------------------------------------------

  constructor() {
    if (typeof window !== 'undefined') {
      window.addEventListener('online', () => void this.processQueue());
    }
    // Hidratar da fila persistida em IDB (best-effort — não bloqueia a startup)
    void this.hydrateFromIdb();
  }

  // ---------------------------------------------------------------------------
  // API pública — enfileirar
  // ---------------------------------------------------------------------------

  /**
   * Enfileira um relato que falhou por razão retentável.
   * Chamado pelo EstacaoComponent no catch de `reportAdapter.submit()`.
   *
   * - `reportId` duplicado: no-op (idempotência local).
   * - Fila cheia: descarta o mais antigo, incrementa `overflowDiscardCount`.
   */
  enqueue(request: ReportRequest): void {
    if (this._queue.has(request.reportId)) {
      return; // já na fila — idempotente
    }

    if (this._queue.size >= MAX_QUEUE_SIZE) {
      this.discardOldest();
    }

    const item: QueuedReport = {
      reportId: request.reportId,
      request,
      enqueuedAt: new Date().toISOString(),
    };

    this._queue.set(request.reportId, item);
    this._pendingCount.set(this._queue.size);
    void this.persistToIdb(item);
  }

  // ---------------------------------------------------------------------------
  // API pública — processar fila (reenvio)
  // ---------------------------------------------------------------------------

  /**
   * Tenta reenviar todos os relatos pendentes.
   * Chamado automaticamente pelo evento 'online'.
   * Também pode ser chamado manualmente pelo EstacaoComponent.
   */
  async processQueue(): Promise<void> {
    if (this.isProcessing) return;
    this.isProcessing = true;

    const items = [...this._queue.values()];
    for (const item of items) {
      await this.retryOne(item);
    }

    this.isProcessing = false;
  }

  // ---------------------------------------------------------------------------
  // Internos — queue
  // ---------------------------------------------------------------------------

  private async retryOne(item: QueuedReport): Promise<void> {
    try {
      await this.reportAdapter.submit(item.request);
      // 200 ou 201 → sucesso (ou idempotente) — remover da fila
      this.dequeue(item.reportId);
    } catch (err) {
      if (isNonRetryable(err)) {
        // 4xx não retentável → remover e registrar
        this.dequeue(item.reportId);
        this._nonRetryableDiscardCount.update(n => n + 1);
      }
      // Retentável (rede, 5xx, 408, 429) → manter na fila
    }
  }

  private discardOldest(): void {
    const oldest = [...this._queue.values()].sort(
      (a, b) => a.enqueuedAt.localeCompare(b.enqueuedAt),
    )[0];

    if (oldest) {
      this._queue.delete(oldest.reportId);
      this._overflowDiscardCount.update(n => n + 1);
      void this.removeFromIdb(oldest.reportId);
    }
  }

  private dequeue(reportId: string): void {
    if (this._queue.delete(reportId)) {
      this._pendingCount.set(this._queue.size);
      void this.removeFromIdb(reportId);
    }
  }

  // ---------------------------------------------------------------------------
  // IndexedDB — persistência best-effort
  //
  // Todos os erros são absorvidos: a fila em memória já garante o comportamento
  // correto dentro da sessão. IDB é otimização de persistência entre sessões.
  // ---------------------------------------------------------------------------

  protected async openDb(): Promise<IDBDatabase> {
    return new Promise<IDBDatabase>((resolve, reject) => {
      const req = indexedDB.open(DB_NAME, 1);

      req.onupgradeneeded = (evt) => {
        const db = (evt.target as IDBOpenDBRequest).result;
        if (!db.objectStoreNames.contains(STORE_NAME)) {
          db.createObjectStore(STORE_NAME, { keyPath: 'reportId' });
        }
      };

      req.onsuccess = (evt) => resolve((evt.target as IDBOpenDBRequest).result);
      req.onerror = () => reject(req.error);
    });
  }

  private async persistToIdb(item: QueuedReport): Promise<void> {
    try {
      const db = await this.openDb();
      const tx = db.transaction(STORE_NAME, 'readwrite');
      tx.objectStore(STORE_NAME).put(item);
      db.close();
    } catch {
      /* IDB indisponível — fila em memória continua como fonte de verdade */
    }
  }

  private async removeFromIdb(reportId: string): Promise<void> {
    try {
      const db = await this.openDb();
      const tx = db.transaction(STORE_NAME, 'readwrite');
      tx.objectStore(STORE_NAME).delete(reportId);
      db.close();
    } catch {
      /* IDB indisponível — ignorar */
    }
  }

  /** Carrega relatos persistidos da sessão anterior na fila em memória. */
  private async hydrateFromIdb(): Promise<void> {
    try {
      const db = await this.openDb();
      const tx = db.transaction(STORE_NAME, 'readonly');
      const store = tx.objectStore(STORE_NAME);

      const items = await new Promise<QueuedReport[]>((resolve, reject) => {
        const req = store.getAll();
        req.onsuccess = () => resolve(req.result as QueuedReport[]);
        req.onerror = () => reject(req.error);
      });

      db.close();

      for (const item of items) {
        if (!this._queue.has(item.reportId)) {
          this._queue.set(item.reportId, item);
        }
      }

      this._pendingCount.set(this._queue.size);
    } catch {
      /* IDB indisponível ou vazio — nenhuma ação necessária */
    }
  }
}
