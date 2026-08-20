# SPEC-07 — Accessibility — HIST (`@neuroassist/hist`)

> Target WCAG 2.1 AA. The timeline is an **audit surface** used by caregivers and coordinators; correctness of
> what is conveyed (urgency, verdict, integrity) must not depend on color alone. Traces vision doc 02 §5.

## 1. Semantics & structure

- The timeline is a semantic list: `<ul role="list">` with each row an `<li>`; not a table (rows are cards, not a data grid).
- Each row exposes an accessible name composed of timestamp + label + urgency + confirmation line, e.g. `"14h32, água, rotina, você confirmou às 14h33"`.
- Filter tabs are a `role="tablist"` with `role="tab"` children, `aria-selected` on the active tab, and `aria-controls` pointing at the list region (`role="tabpanel"`).
- Breadcrumb is a `<nav aria-label="Trilha de navegação">` with an ordered list.

## 2. Color independence

- Urgency is conveyed by **text label + icon + color**, never color alone (`rotina`/`atenção`/`crítico` badges carry the word).
- Feedback terminal state uses **icon + text** (`✓ Marcado real`), not a green/red swatch alone.
- Chain-verification badge states carry text (`Cadeia íntegra` / `Quebrada no registro N` / `Sem registros`).

## 3. Keyboard operation

- All interactive elements (tabs, feedback button + verdict choice, "carregar mais", retry) are reachable and operable by keyboard in a logical tab order.
- The feedback action opens verdict choices as a keyboard-navigable menu/group; `Esc` cancels; focus returns to the triggering button.
- After "carregar mais" appends rows, focus moves to the first newly loaded row (or an `aria-live` announcement of "N novos registros").

## 4. Screen-reader announcements (`aria-live`)

| Transition | Announcement (polite) |
|---|---|
| load → loaded | "{N} registros carregados" |
| load → empty | "Nenhum evento neste período" |
| load → error | "Não foi possível carregar o histórico" |
| feedback → marking | "Registrando…" |
| feedback → marked | "Marcado como {verdict}" |
| feedback → mark-error | "Não foi possível registrar" |
| 409 reconcile | "Já marcado por {nome}" |

- Status region is `aria-live="polite"`; error region is `role="alert"` (assertive) for load failures.

## 5. Focus & targets

- Feedback button and verdict choices have large hit targets (≥ 44×44 CSS px) — vision doc 02 §5 ("large target").
- Visible focus ring on every interactive element (token-driven, ≥ 3:1 contrast against background).
- Skeleton placeholders are `aria-hidden="true"` and marked `aria-busy` on the list region while loading.

## 6. Contrast & typography

- Text and badge contrast ≥ 4.5:1 (normal) / 3:1 (large) using `@neuroassist/shared/ui-tokens`; urgency palette validated for AA.
- Timestamps localized and spelled unambiguously ("14h32"); no reliance on locale-ambiguous numeric-only formats.

## Criterios de aceite (EARS)

- THE SYSTEM SHALL convey urgency, feedback verdict, and chain-integrity status using text and icon in addition to color, and SHALL NOT rely on color alone.
- THE SYSTEM SHALL expose the timeline as a semantic list where each row has an accessible name combining timestamp, label, urgency, and confirmation line.
- THE SYSTEM SHALL make every interactive control operable by keyboard in a logical order with a visible focus indicator.
- WHEN the screen status changes (loaded/empty/error) or a feedback verdict resolves, THE SYSTEM SHALL announce the change via an `aria-live` region.
- WHEN a list load fails, THE SYSTEM SHALL expose the error through a `role="alert"` region.
- THE SYSTEM SHALL provide feedback controls with hit targets of at least 44×44 CSS pixels.
- THE SYSTEM SHALL meet WCAG 2.1 AA contrast for text and urgency badges using the shared design tokens.
