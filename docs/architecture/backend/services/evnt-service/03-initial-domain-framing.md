<!-- OUTPUT-SCHEMA
- Ubiquitous Language
- Aggregates and Entities
- Domain Events
- State Transitions
- Invariants
-->

# 03 — Initial Domain Framing

## Ubiquitous Language
- **Evento:** a structured, persisted business fact — "the patient made a recognized help request". The
  system of record owned by EVNT. Built from a `RecognizedCommandReport`.
- **RecognizedCommandReport:** the edge's frozen input payload (GEST detection contract §3.1) — classified
  primitive(s), confidence, timing window, `commandRef`, `reportId`. EVNT's ingress, not owned by EVNT.
- **Urgência:** the deterministic tier of a command — `emergencia` | `rotina` — sourced from the `Comando`
  (PAC) and **snapshotted** onto the Evento at creation.
- **Notificação (NotificationDispatch):** the act of alerting the responsible caregiver, in the tier matching
  the urgency (`full_screen_alarm` for emergency, `discreet` for routine), with a tracked delivery status.
- **Feedback verdict:** the caregiver's judgement on an Evento — `nao_avaliado` → `real` | `falso_positivo`.
- **Responsible caregiver (routing target):** the `Cuidador` (resolved via ACS) who should receive a given
  patient's alerts. EVNT references by id; ACS owns identity and routing rules.
- **Idempotency ledger:** the record of processed `reportId`s that guarantees one report ⇒ one Evento.

## Aggregates and Entities
- **`Evento`** *(aggregate root)* — `id`, `pacienteId` (ref, PAC), `comandoRef` (opaque handle, PAC),
  `commandLabelSnapshot`, `urgenciaSnapshot` (`emergencia|rotina`), `timingWindow`, `reportId`
  (idempotency), `recognizedAt`, `feedback` (`nao_avaliado|real|falso_positivo`), `confirmadoPor` (ref,
  Cuidador — nullable until confirmed), `confirmadoAt` (nullable). Tenant-scoped.
- **`NotificationDispatch`** *(entity within the Evento aggregate)* — `id`, `eventoId`, `tier`
  (`full_screen_alarm|discreet`), `targetCaregiverId` (ref, ACS), `status`
  (`queued|delivered|acknowledged|failed`), `attempts`, `lastAttemptAt`. One or more per Evento (retries).
- **Value objects:** `Urgencia` (enum), `FeedbackVerdict` (enum + allowed transitions), `TimingWindow`
  (`startMs`, `endMs`, `durationMs`), `NotificationTier` (derived from `Urgencia`).
- **Not owned here:** `Comando`, `Paciente` (PAC), `Cuidador` (ACS), `TipoGesto`/`DetectionProfile` (GEST),
  `DispositivoEdge` (EDGE), the immutable audit ledger (HIST). All referenced by id, snapshotted where needed.

## Domain Events
> Messaging is disabled; these are in-process domain events / synchronous notifications, not bus messages.
> Named for later extraction (Framing §11.2 reevaluation trigger) with no rename.
- `EventoCriado` — a new Evento was persisted from a validated report (`feedback = nao_avaliado`).
- `NotificacaoDespachada` — a notification dispatch entered `delivered`/`acknowledged` for an Evento.
- `NotificacaoFalhou` — a dispatch attempt failed and was re-queued (retry pending).
- `EventoConfirmado` — a caregiver classified the Evento as `real`.
- `FalsoPositivoMarcado` — a caregiver classified the Evento as `falso_positivo`.

## State Transitions
`Evento.feedback`:
```
nao_avaliado ──confirm-real──────▶ real            (terminal)
nao_avaliado ──mark-false-positive▶ falso_positivo  (terminal)
```
`NotificationDispatch.status`:
```
queued ──deliver──▶ delivered ──ack──▶ acknowledged   (terminal-success)
queued ──fail─────▶ failed ──retry──▶ queued          (bounded retries)
```
- An Evento's feedback verdict is set **once** and is terminal — a correction is a new audited action,
  never a silent overwrite (safety/audit requirement).
- A failed dispatch re-queues up to a bounded retry budget; exhausting it leaves the dispatch `failed`
  and surfaces it (never silently abandoned for an emergency).

## Invariants
1. **One report ⇒ one Evento.** A `reportId` already in the idempotency ledger never creates a second
   Evento; the original Evento/verdict is returned.
2. **No Evento without validation.** An Evento is created only after GEST validates the report; a rejected
   report (unknown profile version / primitive not in profile) yields no Evento.
3. **Urgency is snapshotted, not live.** `urgenciaSnapshot` is captured from the `Comando` at creation and
   never re-read; a later change to the `Comando` does not mutate an existing Evento.
4. **Emergency always dispatches.** An `emergencia` Evento always produces at least one `full_screen_alarm`
   dispatch; if delivery fails, the dispatch is retried, never dropped.
5. **Feedback is terminal and attributed.** Setting `feedback` to `real`/`falso_positivo` requires a
   `confirmadoPor` (a resolvable Cuidador) and is a single, audited, non-reversible transition.
6. **Privacy invariant.** No image, frame or landmark is ever accepted or persisted — only primitive ids,
   confidence, timing and business references.
7. **Tenant isolation.** Every Evento, dispatch and lookup is strictly scoped to its `empresa`
   (schema-per-tenant); no cross-tenant access is representable.
