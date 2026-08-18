# 03 — Domain Framing (Frontend) — DASH

## 1. Ubiquitous language

DASH does not own domain terms; it **composes** them. It mirrors the BE and the other
modules' vocabulary — do not redefine here. See `_brief.md`, the wireframe data-model
(§4.3), and the BE domain framing. Terms DASH renders/composes:

- **Paciente** — the monitored person; DASH shows the active patient in the topbar/home.
- **DispositivoEdge.status** (`online | offline`) — shown as the topbar/home device badge
  (data owned by EDGE).
- **Evento / activeEvent** — the live active event projection (owned by EVNT); DASH reads
  it to keep Home calm-or-preempted. `Evento.comando.rotulo` + `urgencia` for the last item.
- **NotificationDispatch** — a delivered routine notification record (owned by EVNT); DASH
  lists recent ones in the notification center.
- **Cuidador.papel** (`cuidadora | coordenador_clinica`) — drives role-gated navigation.
- **Empresa (tenant)** — `X-Empresa-Id`; the panel is scoped to it.

## 2. View models (draft)

```ts
// Home / Status (Tela 01) — server-driven summary + live overlay projection
interface HomeSummaryVm {
  patientName: string;              // Paciente.nome (active)
  device: DeviceBadgeVm;            // from EDGE-composed input
  lastCheckAt: string;              // "08h12" — humanized
  syncedLabel: string;              // "sincronizado agora"
  calm: boolean;                    // true = green; false only when activeEvent emergencia unconfirmed
  quickLink: { label: string; route: string }; // "Ver histórico de hoje" → HIST
  state: 'loading' | 'ok' | 'error';
}

interface DeviceBadgeVm {           // composed from EDGE facade at the shell
  status: 'online' | 'offline';
  lastSyncLabel: string;
  tone: 'ok' | 'warn';              // warn = amber (offline) — NOT alarm
}

// Notification center (bell/inbox)
interface NotificationItemVm {
  id: string;
  label: string;                    // Comando.rotulo (e.g. "água")
  urgency: 'rotina';                // center lists routine dispatches; emergencies live in EVNT/HIST
  atLabel: string;                  // "agora" / "14h32"
  read: boolean;
}

// Entrada (Tela 00) — static brand entry
interface EntradaVm {
  headline: string;                 // "Um gesto que você não vai perder."
  subtitle: string;
  cta: string;                      // "Entrar no painel"
}
```

## 3. UI events

- `entradaCtaPressed()` — from Entrada → enters the shell (session already valid).
- `quickLinkPressed()` — Home → navigate to HIST today timeline.
- `notificationOpened(id)` — mark a dispatch read; optionally navigate to HIST.
- `retryHomeSummary()` — re-fetch the summary after an error.
- `navSelected(moduleKey)` — sidebar navigation to another module's lazy route.

## 4. State transitions (UI level)

```text
Home summary:      idle ──► loading ──► ok
                                   └──► error ──►(retry)──► loading
Calm projection:   ok(calm=true)  ◄──► preempted   (driven by EVNT activeEvent; DASH observes)
Notification item: unread ──► read
Entrada:           static (no async state)
```

> `preempted` is not owned by DASH — EVNT's overlay renders above the router-outlet. DASH's
> Home merely reflects `calm = (activeEvent is not an unconfirmed emergencia)`.

## 5. Invariants (client-side)

- **Calm ⇔ no active emergency.** Home renders green iff there is no unconfirmed
  `emergencia` in `activeEvent`. Offline device does **not** break calm.
- **No fabricated status.** DASH never shows "Márcio está bem" from stale/absent data;
  absent summary ⇒ loading or error, never a green default.
- **No domain writes.** DASH performs no `Evento`/`Comando`/`Dispositivo` mutation; the only
  local write is `notification.read` (UI state / lightweight ack).
- **Role gating.** `Onboarding` nav item is present iff role is condutor técnico/admin.
- **Tenant scoping.** Every call carries `X-Empresa-Id`; never render another tenant's patient.
- **No technical codes.** The last-event/notification label is the family `rotulo`, never
  an internal `event_type`.
