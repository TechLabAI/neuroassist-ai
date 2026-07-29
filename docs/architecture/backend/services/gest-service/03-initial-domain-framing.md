<!-- OUTPUT-SCHEMA
- Ubiquitous Language
- Aggregates and Entities
- Domain Events
- State Transitions
- Invariants
-->

# 03 — Initial Domain Framing

## Ubiquitous Language
- **TipoGesto (Primitive):** an atomic facial-movement type the detection engine may recognize
  (e.g. sobrancelha, piscar duplo). The unit of the extensible vocabulary.
- **Detectability state:** where a primitive sits between "named" and "actually recognizable":
  `registered`, `pending-training`, `supported`, `deprecated`.
- **Detection profile:** an immutable, versioned bundle of `supported` primitives plus their
  classification parameters, pulled by an edge device to configure its engine.
- **Detection contract:** the published schema an edge device uses to report a recognized command
  (classified primitive id(s), confidence, timing window).
- **Recognized command (report):** the edge's structured output when the active command's primitive
  is classified above threshold. GEST owns its shape; EVNT builds the business `Evento` from it.
- **Composite gesture:** a command backed by two primitives combined (Framing §7.2). Both must be
  `supported` for the composite to be eligible.
- **Edge engine (`GestureDetectionEngine`):** the on-device CV process; a consumer of GEST, outside
  the service boundary.

## Aggregates and Entities
- **`TipoGesto`** *(aggregate root)* — `id`, `nome`, `detectabilityState`, `engineFeatureRef`
  (opaque handle the edge engine understands, nullable until `supported`), audit metadata. Tenant-scoped.
- **`DetectionProfile`** *(aggregate root)* — `id`, `version` (monotonic), `supportedPrimitiveIds[]`,
  `classificationParams` (thresholds, timing windows), `publishedAt`, `active` flag. Immutable once
  published; a new state produces a new version.
- **Value objects:** `DetectabilityState` (enum + allowed transitions), `ClassificationParams`
  (confidence threshold, min/max gesture window in ms — sourced from PAC `Comando` but snapshotted
  into the profile), `ProfileVersion`.
- **Not owned here:** `Comando` (PAC), `Evento` (EVNT), `DispositivoEdge` (EDGE), `Paciente` (PAC).
  GEST references `DispositivoEdge` only by id when resolving a profile.

## Domain Events
> Messaging is disabled; these are in-process domain events / synchronous notifications, not bus
> messages. Listed for later extraction (Framing §11.2 reevaluation trigger).
- `PrimitiveRegistered` — a new TipoGesto entered the catalog as `registered`.
- `PrimitivePromoted` — transition toward `supported` (`registered→pending-training→supported`).
- `PrimitiveDeprecated` — a primitive left active use.
- `DetectionProfilePublished` — a new immutable profile version became active for a tenant/device.

## State Transitions
`TipoGesto.detectabilityState`:
```
registered ──promote──▶ pending-training ──confirm-support──▶ supported
registered ──confirm-support(direct)────────────────────────▶ supported   (seed primitives)
supported  ──deprecate──▶ deprecated
pending-training ──deprecate──▶ deprecated
```
- A primitive may skip `pending-training` only for seed primitives already validated on the engine.
- `deprecated` is terminal for eligibility (kept for historical `Comando`/`Evento` references).

## Invariants
1. A `Comando` with `urgencia = emergencia` may reference **only** `supported` primitives (both, for
   composite gestures). GEST is the enforcement point for this eligibility check.
2. A `DetectionProfile` contains **only** `supported` primitives; publishing recomputes membership.
3. `DetectionProfile.version` is strictly monotonic and never reused; profiles are immutable.
4. A primitive cannot regress from `supported` to a lower state silently — any regression is an
   explicit, audited transition (safety/audit requirement).
5. `engineFeatureRef` must be non-null before a primitive can reach `supported` (there must be a real
   engine feature behind the name — closes the Framing §6.1/§9.3 gap).
6. TipoGesto vocabulary and profiles are strictly tenant-scoped (schema-per-empresa).
