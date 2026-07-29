<!-- OUTPUT-SCHEMA
- Ubiquitous Language
- Aggregates and Entities
- Domain Events
- State Transitions
- Invariants
-->

# 03 — Initial Domain Framing

## Ubiquitous Language
- **Paciente:** the monitored person; the subject a command catalog belongs to. Scoped to a `Tenant`
  (empresa), linked by reference to a `DispositivoEdge` (EDGE) and responsible `Cuidador`(es) (ACS).
- **Comando:** a catalog item mapping a **gesture** to a family-defined **need** for one patient. Carries
  the free-text `rotulo` (label), the `necessidade`, the referenced primitive(s), and its own calibration.
- **Gesture (simple / composite):** the physical movement backing a command — one `primitivoPrincipal`
  (`TipoGesto`), optionally combined with a `primitivoSecundario` for a composite gesture. Primitive ids
  are **owned by GEST**; PAC references them.
- **Calibração (per-command):** the tunable trio that governs how a command is detected — `sensibilidade`
  (`baixa|media|alta`), the `TimingWindow` (`tempoMinGestoMs`/`tempoMaxGestoMs`), and `urgencia`
  (`emergencia|rotina`). Each command owns its own; they are never shared.
- **Rótulo livre:** the family-authored, free-text label for a command ("quero água"); PAC imposes no
  controlled vocabulary on meaning.
- **Eligibility gate:** the publish-time check (against GEST) that an emergency command's primitive(s)
  are `supported` — genuinely detectable.
- **Catálogo ativo (published catalog):** the set of a patient's `published` commands GEST snapshots into
  a `DetectionProfile`.

## Aggregates and Entities
- **`Paciente`** *(aggregate root)* — `id`, `nome`, `tenantId` (empresa, ACS), `dispositivoEdgeId`
  (EDGE, by ref), responsible caregiver ref(s) (ACS, by ref), audit metadata. Tenant-scoped. PAC is the
  system of record for the patient.
- **`Comando`** *(aggregate root)* — `id`, `pacienteId`, `rotulo` (free-text), `necessidade`,
  `primitivoPrincipalId` (`TipoGesto`, by ref), `primitivoSecundarioId` (nullable, by ref),
  `sensibilidade`, `tempoMinGestoMs`, `tempoMaxGestoMs`, `urgencia`, `estado` (lifecycle),
  `calibracaoVersao` (monotonic), audit metadata. Tenant-scoped.
- **Value objects:** `Sensibilidade` (enum), `TimingWindow` (min/max ms, invariant min ≤ max),
  `Urgencia` (enum + eligibility policy), `GestureRef` (principal + optional secondary primitive ids),
  `CalibracaoVersao`.
- **Not owned here:** `TipoGesto` and `DetectionProfile` (**GEST**), `Evento` (**EVNT**), `Cuidador` and
  `Tenant` (**ACS**), `DispositivoEdge` (**EDGE**). All referenced by id only.

## Domain Events
> Messaging is disabled; these are in-process domain events / synchronous notifications, not bus
> messages. Listed for later extraction if a broker is introduced.
- `PacienteRegistrado` — a patient entered the catalog.
- `ComandoRascunhado` — a command was drafted (not yet validated/published).
- `ComandoValidado` — the emergency-eligibility gate passed (or routine advisory cleared).
- `ComandoPublicado` — a command became part of the patient's active catalog (**anchor event**).
- `ComandoRecalibrado` — a published command's sensitivity/timing/urgency changed (version bumped).
- `ComandoAposentado` — a command was retired (kept for historical references).

## State Transitions
`Comando.estado`:
```
drafted ──validate(eligibility gate)──▶ validated ──publish──▶ published
published ──recalibrate──▶ published            (calibracaoVersao++ ; re-runs gate if emergencia)
published ──retire──▶ retired
validated ──edit──▶ drafted                     (a material edit re-opens validation)
drafted   ──retire──▶ retired
```
- An `emergencia` command may only reach `validated`/`published` if the eligibility gate passed.
- `retired` is terminal for activation; the command is kept for historical `Evento`/audit references.
- Recalibration keeps the command `published` but bumps `calibracaoVersao` and signals GEST to
  republish the profile.

## Invariants
1. **Independent calibration:** editing one `Comando`'s `sensibilidade`/`TimingWindow`/`urgencia` never
   alters any other command (no shared mutable calibration).
2. **Emergency eligibility:** a `Comando` with `urgencia = emergencia` cannot be `published` unless every
   referenced primitive is `supported` per GEST (fail closed on unknown result).
3. **Timing window sanity:** `tempoMinGestoMs ≤ tempoMaxGestoMs`, both > 0.
4. **Composite integrity:** if `primitivoSecundario` is set it must differ from `primitivoPrincipal`, and
   for an emergency both must be `supported`.
5. **History preservation:** a `Comando` referenced by past events is versioned/retired, never silently
   hard-mutated in a way that changes an existing snapshot's meaning (see ADR-006).
6. **Free-text label required:** every `Comando` has a non-empty family-authored `rotulo`.
7. **Tenant isolation:** every `Paciente` and `Comando` is strictly scoped to its `empresa`
   (schema-per-empresa).
8. **Privacy:** PAC stores only catalog metadata and primitive ids — no image, frame or landmark.
