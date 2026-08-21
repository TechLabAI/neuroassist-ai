# SPEC-05 — UI/UX Spec — HIST (`@neuroassist/hist`)

> The browser is a **review and audit surface**: a period-filtered, newest-first timeline of resolved events with a
> single feedback verdict per event. Copy pt-BR; code/identifiers en-US. Traces vision doc 02 §3/§5, doc 05, FE ADR-005.

## 1. Screen: History Timeline (`/historico`)

```
┌────────────────────────────────────────────────────────────────────┐
│  Histórico                                     [Verificar cadeia ✓]  │  ← badge only for hist:read:all
│  ┌───────┬────────┬──────┐                                          │
│  │ Hoje  │ Semana │ Mês  │   ← FilterTabs (active = Hoje)           │
│  └───────┴────────┴──────┘                                          │
│                                                                     │
│  14h32 — "água"     [rotina]   Você confirmou às 14h33  [ Foi real? ]│
│  08h05 — "Sapo"     [rotina]   Você confirmou às 08h06  [✓ Marcado real]│
│  07h40 — "banheiro" [atenção]  Você confirmou às 07h41  [ Foi real? ]│
│                                          … carregar mais …          │
└────────────────────────────────────────────────────────────────────┘
```

- **Row anatomy:** `occurredAtLabel` — `"label"` (rotulo) · `UrgencyBadge` · `confirmationLine` · `FeedbackAction`.
- **Label rule (R2):** always the human-readable `Comando.rotulo`, always paired with urgency badge + timestamp + confirmation line so a new caregiver has enough context. Never render the technical `factType`.
- **Ordering:** newest first (`recordedAt DESC`).

## 2. Interaction states

| State | Visual |
|---|---|
| `loading` | `ListSkeletonComponent` (shimmer rows), tabs remain interactive |
| `loaded` | list of `EventListItem` rows + optional "carregar mais" |
| `empty` | reassuring `EmptyState`: "Nenhum evento neste período — ótimo sinal" |
| `error` | inline error card + retry (never a blank screen or infinite spinner) |

## 3. Filter tabs

- Three tabs: `Hoje` / `Semana` / `Mês` (A1 — no custom date-range picker in MVP).
- Tapping a tab updates the URL `?period=`, resets to page 0, and shows the loading skeleton while the new period loads (a view transition, not a data mutation — doc 05 §4).
- The active tab is reflected from the URL so a shared link opens on the right filter.

## 4. Feedback action ("Foi real?")

- Idle: button `[ Foi real? ]` → on press, choose `real` | `falso_positivo`.
- `marking`: per-item spinner + disabled control (non-optimistic; verdict not yet shown).
- `marked`: terminal chip `[✓ Marcado real]` / `[✓ Falso positivo]`, action disabled — text + icon, not color alone.
- `mark-error`: inline "Não foi possível registrar. Tentar de novo."; item stays `nao_avaliado`.
- `409 terminal`: "Já marcado por &lt;nome&gt; às &lt;hora&gt;"; item reconciles to backend verdict, no client override.

## 5. Urgency badge

- Token-driven from `@neuroassist/shared/ui-tokens` (`rotina`/`atencao`/`critico`), consistent with EVNT's alarm palette (via shared, not a cross-module import).
- Conveys urgency by label text **and** color, never color alone (accessibility, SPEC-07).

## 6. Chain-verification badge (coordinators)

- Visible only for `hist:read:all`. States: `intact` (✓ green "Cadeia íntegra"), `broken` (⚠ "Quebrada no registro N"), `empty` (neutral "Sem registros"). Reflects `ChainVerificationResult` (api-contract §3.2).

## 7. Copy (pt-BR) reference

| Key | Copy |
|---|---|
| screen title | "Histórico" |
| tabs | "Hoje" / "Semana" / "Mês" |
| empty | "Nenhum evento neste período — ótimo sinal" |
| feedback CTA | "Foi real?" |
| feedback terminal (real) | "✓ Marcado real" |
| feedback terminal (falso) | "✓ Falso positivo" |
| list load error | "Não foi possível carregar o histórico. Tentar de novo." |
| feedback error | "Não foi possível registrar. Tentar de novo." |
| 409 reconcile | "Já marcado por {nome} às {hora}" |

## Criterios de aceite (EARS)

- THE SYSTEM SHALL render each timeline row with timestamp, human-readable label, urgency badge, confirmation line, and feedback action.
- WHEN the caregiver selects a period tab, THE SYSTEM SHALL update the URL, reset to page 0, and show the loading skeleton while the new period loads.
- WHILE the active period returns no events, THE SYSTEM SHALL show the reassuring empty state and SHALL NOT show an error or a blank screen.
- WHEN the caregiver confirms a verdict, THE SYSTEM SHALL show a per-item progress state and, on success, render the terminal verdict chip with text and icon (not color alone).
- WHILE a feedback verdict is terminal, THE SYSTEM SHALL disable the feedback action for that item.
- WHERE the caller holds `hist:read:all`, THE SYSTEM SHALL show the chain-verification badge reflecting the `intact`/`broken`/`empty` result.
- THE SYSTEM SHALL present all user-facing copy in pt-BR and SHALL localize timestamps to the tenant locale.
