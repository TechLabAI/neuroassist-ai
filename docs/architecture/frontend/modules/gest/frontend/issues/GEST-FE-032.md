# GEST-FE-032 -- Fila de relatos em IndexedDB — e a distinção entre "enfileirado" e "não aconteceu"

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens
**GitHub:** [#TBD]

## Outcome
Relato que falha por rede vai para uma fila persistente e é reenviado quando a conexão volta, sem
duplicar alarme. E a tela deixa explícita a diferença entre detecção enfileirada e detecção que
nunca aconteceu.

## Context / Problem statement
- A `ADR-007` (custo nº 4) previu que o outbox do agente Python (SQLite, 50 eventos FIFO) viraria
  IndexedDB no browser.
- A parte que importa e que se perde facilmente: **aba fechada não é detecção enfileirada — é
  detecção que não aconteceu.** Nenhuma fila recupera um pedido de ajuda que ninguém observou.
  Uma UI que mostra "3 pendentes" depois de reabrir sugere que nada se perdeu, e isso é falso.
- Idempotência já é do contrato: `POST /api/v1/reports` devolve `200` (em vez de `201`) para
  `reportId` repetido. O reenvio é seguro **desde que o `reportId` de FE-030 seja estável.**

## Approach (execution path)
1. Fila em IndexedDB: `reportId` como chave (impede duplicata local por construção), FIFO com teto.
2. Ao exceder o teto, **descartar o mais antigo e contar o descarte** — descarte silencioso faria a
   fila mentir sobre completude.
3. Reenvio com backoff ao voltar `online`; `200` e `201` ambos removem da fila.
4. `4xx` que não seja 408/429 não é retentável: remove da fila e registra. Retentar 400 para sempre
   entope a fila com o que nunca vai passar.
5. Tela: "N relatos aguardando envio" **e**, se a estação foi reiniciada, a janela em que não houve
   observação nenhuma.

## Non-goals
- Service Worker para flush em background (a aba fechada não detecta; não há o que enfileirar).

## Layer(s)
frontend / data-access

## Bounded context
gest

## Service execution spec(s)
- `frontend-spec/SPEC-09-estacao-paciente.md` §2.3, §4 (I4), §5 (E6)
- `ADR-008` D6

## Architecture alignment
- Adapter isolado atrás de porta; a IndexedDB não vaza para o componente
- Tag Nx: `scope:gest`, `type:data-access`

## Implementation checklist (by layer/file)

**Data access**
- [ ] `libs/gest/src/lib/estacao/report-queue.port.ts`
- [ ] `libs/gest/src/lib/estacao/report-queue-indexeddb.adapter.ts`
- [ ] `libs/gest/src/lib/estacao/report-queue.service.ts` — backoff + política de 4xx
- [ ] specs: reenvio idempotente, descarte contado, 400 não retentável
- [ ] tela: pendentes + janela sem observação

## Acceptance
- Rede cai, gesto acontece, rede volta → relato chega **uma** vez.
- Estourar o teto incrementa um contador de descarte visível; nunca some em silêncio.
- Reabrir a estação após período fechada mostra a janela sem observação.
