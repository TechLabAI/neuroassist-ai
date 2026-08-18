# SPEC-03 — View Models — DASH (Frontend)

> Sources: FE 05 §1/§2, BE api-contract §2.1 (`/panel/summary`) + §3 (SSE) + §4 (notifications).
> View-models are TypeScript `record`-style readonly types held in facades as Signals. `calm` is **derived**
> (`computed`) — never stored, never optimistic (safety, FE 05 §4).

## 1. Home / Status

### `HomeSummaryVm` (DASH-owned, `DashHomeFacade.summary`)

```ts
type LoadState = 'loading' | 'ok' | 'error';

interface DeviceBadgeVm {
  readonly status: 'online' | 'offline' | 'unknown';   // 'unknown' when EDGE degraded (BE §2.1)
  readonly lastSeenAt: string | null;                  // ISO-8601
  readonly degraded: boolean;
}

interface LatestEventVm {
  readonly eventId: string;
  readonly commandLabel: string;                       // e.g. "Preciso de água"
  readonly urgencia: 'emergencia' | 'rotina';
  readonly feedback: 'nao_avaliado' | 'real' | 'falso_positivo';
  readonly at: string;                                 // ISO-8601
}

interface PatientSummaryVm {
  readonly patientId: string;
  readonly name: string;
  readonly device: DeviceBadgeVm;
  readonly latestEvent: LatestEventVm | null;          // null when EVNT degraded (BE §2.1)
  readonly unacknowledgedCount: number;
}

interface HomeSummaryVm {
  readonly tenantId: string;
  readonly caregiver: { readonly id: string; readonly name: string; readonly role: string };
  readonly patients: readonly PatientSummaryVm[];
  readonly degradedSections: readonly string[];        // e.g. ['evnt'] | ['edge'] (BE §2.1)
  readonly loadedAt: string;
}
```

### `HomeStatusCardVm` (presentational input, projected)

```ts
interface HomeStatusCardVm {
  readonly kind: 'calm' | 'loading' | 'error';
  readonly patientName: string;
  readonly lastCheckLabel: string;                     // "Última verificação: 08h12"
  readonly syncLabel: string;                          // "sincronizado agora"
  readonly errorMessage: string | null;                // non-technical, pt-BR
}
```

### Derived `calm` signal (`DashHomeFacade.calm`)

```ts
// computed over EVNT's activeEvent (injected at shell, ADR-005) — NOT from the summary aggregate.
readonly calm = computed(() => {
  const ev = this.activeEvent();                       // provided via token/input at scope:web
  return !(ev !== null && ev.urgencia === 'emergencia' && ev.confirmadoEm === null);
});
```

**UI events exposed:** `reloadSummary()` (retry), `viewHistory(patientId)` (→ HIST quick-link).

**Invariants:** `calm` follows EVNT live truth (live must win over the aggregate — a false "está bem" is the
worst failure, ADR-005). `state='error'` degrades only the StatusCard; the shell chrome stays intact.

## 2. Notification Center

### `NotificationItemVm` (DASH-owned, `NotificationsFacade.items`)

```ts
interface NotificationItemVm {
  readonly dispatchId: string;
  readonly tier: 'full_screen_alarm' | 'discreet';     // full_screen_alarm shown as read-only entry (EVNT owns overlay)
  readonly patientId: string;
  readonly commandLabel: string;
  readonly urgencia: 'emergencia' | 'rotina';
  readonly raisedAt: string;                            // ISO-8601
  readonly read: boolean;                               // local read flag (harmless UI ack)
}

interface NotificationFilterVm {
  readonly scope: 'recent' | 'unread';
}
```

### `NotificationsFacade` state

```ts
readonly items       = signal<readonly NotificationItemVm[]>([]);   // .asReadonly()
readonly state       = signal<LoadState>('loading');                // .asReadonly()
readonly filter      = signal<NotificationFilterVm>({ scope: 'recent' });
readonly unreadCount = computed(() => this.items().filter(i => !i.read).length);
```

**UI events exposed:** `setFilter(f)`, `markRead(dispatchId)`, `acknowledge(dispatchId)` (→ EVNT via BE),
`open(dispatchId)` (navigate to patient context).

**Invariants:** `markRead` updates the local signal immediately then `POST .../acknowledge`; on failure it
**silently reverts** the `read` flag (non-critical, no user-facing error, FE 05 §4). List errors render an
inline empty/error state and never block Home.

## Critérios de aceite (EARS)

- WHILE the Home summary is loading, THE SYSTEM SHALL render the StatusCard in the `loading` kind and SHALL
  NOT assert "está bem".
- WHEN the summary fetch fails, THE SYSTEM SHALL set the StatusCard kind to `error` with a non-technical
  message and a retry affordance, keeping the shell chrome intact.
- WHILE EVNT reports an unconfirmed `emergencia` active event, THE SYSTEM SHALL keep `calm` false and SHALL
  NOT render the "está bem" calm state.
- WHEN the summary response includes `degradedSections`, THE SYSTEM SHALL render the affected patient section
  (device or latestEvent) in its degraded projection rather than hide the patient.
- WHEN the caregiver marks a notification read, THE SYSTEM SHALL update the local read flag immediately and
  IF the acknowledge call fails, THEN THE SYSTEM SHALL revert the read flag without surfacing an error.
- WHILE the notification list is empty or errored, THE SYSTEM SHALL render an inline empty/error state and
  SHALL NOT block the Home surface.
