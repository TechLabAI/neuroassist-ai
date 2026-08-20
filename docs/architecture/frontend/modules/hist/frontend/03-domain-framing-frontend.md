# 03 — Domain Framing (Frontend) — HIST

> This document **mirrors** the backend ubiquitous language; it does not redefine it.
> Source of truth: `_brief.md` (this module) and the EVNT/HIST backend domain framing.
> HIST is a **read + audit** view over the `Evento` system of record owned by EVNT.

## 1. Ubiquitous language (mirror of BE)

| Term | Meaning (as used by HIST-FE) | Owner |
|---|---|---|
| **Evento** | A persisted business fact (a recognized help request). HIST reads it historically; it never creates it. | EVNT |
| **Comando** | The family-defined command; supplies `rotulo` (label) and `urgencia` rendered in each item. | PAC |
| **rotulo** | The command label as the family wrote it ("água", "Sapo"). Always shown; never a technical code. | PAC / Evento snapshot |
| **urgencia** | `emergencia` \| `rotina` — rendered as an urgency badge. Snapshotted on the Evento. | PAC (snapshot on Evento) |
| **feedback** | Verdict: `nao_avaliado` \| `real` \| `falso_positivo`. HIST provides the UI to set it; the transition is terminal and attributed. | EVNT (system of record) |
| **confirmadoPor / confirmadoEm** | Who confirmed the event and when — rendered as the "Você confirmou às 14h33" line. | EVNT / ACS |
| **audit entry** | An immutable, attributed record of an event/alarm/confirmation/edit action, persisted by `hist-service`. | HIST (`hist-service`) |
| **period** | UI-level filter over the ledger: `hoje` \| `semana` \| `mes`. | HIST-FE |

HIST introduces **no new domain nouns**; `audit entry` and `period` are the only
HIST-owned concepts, and both are read-model / view concerns.

## 2. View models (draft)

```ts
// Rendered per row in the timeline (read model → VM)
interface HistoryItemVm {
  eventId: string;
  label: string;              // Evento.comando.rotulo — family's own word, quoted in UI
  urgency: 'emergencia' | 'rotina';
  recognizedAt: string;       // ISO; rendered "14h32"
  confirmedBy?: string;       // Cuidador name → "Você confirmou..." / "<nome> confirmou..."
  confirmedAt?: string;       // ISO; rendered "às 14h33"
  feedback: 'nao_avaliado' | 'real' | 'falso_positivo';
  marking: 'idle' | 'marking' | 'mark-error';  // per-item feedback sub-state
}

// The screen VM
interface HistoryScreenVm {
  period: 'hoje' | 'semana' | 'mes';
  items: HistoryItemVm[];
  status: 'idle' | 'loading' | 'loaded' | 'empty' | 'error';
  page: number;
  hasMore: boolean;           // pagination
  error?: AppError;
}
```

VMs are **projections of the read model**, assembled in the facade; components never
fetch or shape raw DTOs.

## 3. UI events (user-driven, mutate state)

| UI event | Trigger | Effect |
|---|---|---|
| `openHistory(period)` | Home deep-link / route entry | `load(period)` → loading → loaded/empty/error |
| `setPeriod(period)` | tap `Hoje`/`Semana`/`Mês` | reset to page 0, reload |
| `loadMore()` | scroll end / "carregar mais" | append next page (if `hasMore`) |
| `markFeedback(eventId, verdict)` | press "Foi real?" → choose real/falso-positivo | per-item marking → POST → terminal / mark-error |
| `retry()` | error state action | re-run the last query |
| `retryMark(eventId)` | item mark-error action | re-run the feedback write |

## 4. State transitions (UI level)

**Screen:**
```
idle --openHistory--> loading --success(items>0)--> loaded
                              --success(items=0)--> empty
                              --failure----------> error --retry--> loading
loaded --setPeriod--> loading      (list resets)
loaded --loadMore(ok)--> loaded    (append)   loaded --loadMore(fail)--> loaded + inline-error
```

**Per item (feedback):**
```
idle(nao_avaliado) --markFeedback--> marking --success--> marked(real|falso_positivo)  [action disabled]
                                            --failure--> mark-error --retryMark--> marking
marking --409 already-terminal--> reconcile to backend verdict (no client override)
```

## 5. Invariants (client-side)

- **Immutability** — HIST-FE never edits or deletes a historical event; the only
  write is the feedback verdict.
- **Terminal feedback** — once `feedback ∈ {real, falso_positivo}`, the "Foi real?"
  action is disabled; the UI never re-opens a terminal verdict (backend enforces).
- **Non-optimistic write** — an item shows a verdict only after the backend confirms
  it; a failed write leaves the item `nao_avaliado`.
- **Label fidelity** — the item always renders `Evento.comando.rotulo`; if the label
  is missing/unresolved, show a safe fallback ("comando sem rótulo") — never a code.
- **Attribution fidelity** — "quem/quando" comes only from the ledger; the UI never
  fabricates confirmation metadata.
- **Tenant scope** — every query is bound to the caregiver↔patient vínculo via
  `X-Empresa-Id`; no cross-tenant history is ever requested or shown.
- **Period is a view filter, not a mutation** — changing period never alters data,
  only the query window.
