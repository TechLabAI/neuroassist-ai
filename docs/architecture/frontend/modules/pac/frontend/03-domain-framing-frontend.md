# 03 — Domain Framing (Frontend) — PAC

## 1. Ubiquitous language (mirror of BE / `_brief.md`)

PAC-FE does **not** redefine domain terms; it mirrors the BE data model
(`docs/architecture/shared/modelo-dados`), `_brief.md`, and the GEST framing for
shared terms. Terms used on this surface:

| Term | Meaning (as used by PAC-FE) |
|---|---|
| **Paciente** | The monitored person (Márcio), bound to the active `Tenant`. PAC owns registration; DASH/Home read it. |
| **Comando** | A catalog entry: a gesture (simple = 1 primitivo, composite = 2 primitivos) mapped to a family-defined need. Carries `rotulo`, `urgencia`, `sensibilidade`, `tempoMinimoGestoMs`, `tempoMaximoGestoMs`. **PAC owns the catalog.** |
| **Rótulo** | Free-text family label ("água", "Sapo"). The system stores/displays it; it never interprets meaning. |
| **TipoGesto (primitivo)** | A catalogued facial-movement primitive. **Owned by GEST** — PAC reads it and may request a new one. Naming a type does not imply the engine recognizes it (GEST Framing §6.1). |
| **Urgência** | `emergencia` (full-screen alarm downstream) \| `rotina` (discreet notice). Authored here; enforced by EVNT. |
| **Sensibilidade** | `baixa \| media \| alta` — how readily the engine should fire for this command (never shown as a raw threshold). |
| **Sync status** | Client-derived: whether the command's latest saved edit is `ativo no dispositivo` or `pendente de sincronização` (ADR-005 local). Not a BE domain enum — a UI-honesty projection. |
| **Evento** | Produced by EVNT when a command is recognized in production; **never** created by PAC. |

## 2. View models (draft)

```ts
// list row — what the catalog renders per command
interface ComandoRowVM {
  id: string;
  rotulo: string;                 // family label
  gestoDescricao: string;         // "Sobrancelha" | "Sobrancelha + Piscar" (resolved tipo names)
  urgencia: 'emergencia' | 'rotina';
  sensibilidade: 'baixa' | 'media' | 'alta';
  tested: boolean;                // has a live test succeeded for this command?
  syncState: 'synced' | 'pending';// ativo no dispositivo | pendente de sincronização (ADR-005)
}

// editor form — mirrors the editable fields of Comando
interface ComandoFormVM {
  rotulo: string;
  urgencia: 'emergencia' | 'rotina';
  primitivoPrincipal: string;              // TipoGesto id (GEST-owned catalog)
  primitivoSecundario: string | null;      // composite (optional)
  sensibilidade: 'baixa' | 'media' | 'alta';
  tempoMinimoGestoMs: number;
  tempoMaximoGestoMs: number;
}

interface PacienteVM {
  id: string;
  nome: string;
  tenantId: string;
}

type EditorState = 'idle' | 'editing' | 'saving' | 'saved' | 'error';
```

## 3. UI events (user → state)

| Event | Trigger | Effect |
|---|---|---|
| `newCommand` | "Novo comando" | open editor with a blank draft → `editing` |
| `editCommand` | row "Editar" | open editor pre-filled from `Comando` → `editing` |
| `removeCommand` | row "Remover" | delete after confirm; catalog reloads |
| `addTipoGesto` | inline "+" in editor | delegate new-type request to GEST; select returned id |
| `saveCommand` | "Salvar comando" | validate → `saving` → `saved (pending sync)` \| `error` |
| `cancelEdit` | "Cancelar" / navigate away | discard draft → `idle` (no persistence) |
| `syncAck` | edge device pulled catalog (EDGE status) | row `syncState: pending → synced` |
| `savePatient` | onboarding patient step | persist `Paciente` on active tenant |

## 4. State transitions (UI level)

```
Catalog:   idle --newCommand/editCommand--> editing
Editor:    editing --saveCommand--> saving --ok--> saved(pending) --syncAck--> synced
                   \--cancelEdit--> idle (draft discarded)
           saving --error(ProblemDetail)--> error --retry--> saving
           saved --editCommand(same cmd)--> editing (syncState resets to pending on next save)
```

Invariants on transitions:
- Leaving `editing` via cancel **always** discards the draft — nothing partial is
  persisted or added to the catalog.
- A command reaches `synced` **only** on an explicit device acknowledgment
  (EDGE status), never optimistically on save (ADR-005 local).

## 5. Invariants (client-side)

- `rotulo` must be non-empty (trimmed) before a command is savable — but a **live
  test can run on an unsaved draft** (test-before-save is intentional).
- `tempoMinimoGestoMs < tempoMaximoGestoMs`, both positive; enforced synchronously
  in the editor.
- A **composite** command requires a `primitivoSecundario` distinct from
  `primitivoPrincipal`; removing the second gesture returns it to a simple command.
- `primitivoPrincipal` / `primitivoSecundario` must reference a `TipoGesto` id that
  exists in the GEST-owned catalog (or one just requested via the inline add).
- Editing one command **must not** mutate any other command's parameters
  (calibration isolation).
- No raw sensitivity threshold or confidence value is rendered — only the mapped
  `baixa|media|alta` label and human-language hints.
- The catalog **never** shows a command as `ativo no dispositivo` while its latest
  saved edit is still `pendente de sincronização` (honest-state discipline).
