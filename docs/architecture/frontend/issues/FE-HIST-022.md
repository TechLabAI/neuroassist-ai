## [FE-HIST-022] hist a11y: hit targets ≥ 44×44px, focus ring e contraste WCAG AA — FE-GAP-19

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Todos os controles interativos têm hit targets mínimos de 44×44 CSS px, focus ring visível (≥ 3:1 contraste), e texto/badges com contraste AA (≥ 4.5:1 normal / 3:1 large). Fecha FE-GAP-19.

**Context / Problem statement:**
- SPEC-07 §5: feedback button e botões de veredicto: `min-width/height: 44px`.
- SPEC-07 §5: focus ring visível em **todos** os interativos, token-driven, ≥ 3:1.
- SPEC-07 §6: contraste ≥ 4.5:1 (normal) / 3:1 (large) com tokens `@neuroassist/shared/ui-tokens`.
- FE-GAP-19: meta de hit target e contraste ausente nas specs — esta issue documenta e verifica.

**Approach (execution path):**
1. CSS global de `libs/hist/ui`: `min-width: 44px; min-height: 44px` em todos os `button` interativos.
2. Focus ring: `outline: 2px solid var(--color-focus)` em `:focus-visible` para todos os interativos; `--color-focus` do ui-tokens.
3. Verificar paleta de urgência (`rotina`/`atencao`/`critico`) contra AA:
   - Usar `@neuroassist/shared/ui-tokens` palette; se tokens não validados, criar variante validada.
4. Documentar resultados de contraste no SPEC-07 (ou criar artefato de audit).
5. Playwright + axe-playwright: `checkA11y` com regras de contraste habilitadas.

**Non-goals:**
- Não redesenhar tokens globais de outros módulos.

**Layer(s):**
frontend / ui

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-07-accessibility.md` (§5, §6)

**Architecture alignment:**
- Tag Nx: `scope:hist`, `type:ui`

**Implementation checklist (by file):**
- 🔲 `libs/hist/ui/src/lib/hist-ui-a11y.scss` — hit targets + focus ring tokens
- 🔲 Atualizar `feedback-action.component.scss` — min 44×44
- 🔲 Atualizar `filter-tabs.component.scss` — min 44×44
- 🔲 Audit de contraste: documentar ratio de cada token de urgência
- 🔲 `nx test hist-ui` PASS
- 🔲 `nx lint hist-ui` PASS

**Milestone:** Wave P6 / Accessibility (HIGHEST PRIORITY)

**Labels:**
type:accessibility · layer:frontend · scope:hist · priority:P6

**Acceptance criteria:**
- Todos os botões interativos: `min-width ≥ 44px; min-height ≥ 44px` (verificável via getComputedStyle).
- Focus ring visível em todos os interativos.
- Tokens de urgência e texto primário: contraste ≥ 4.5:1 (auditado e documentado).
- Axe-playwright: 0 violations de contraste AA.

**Dependencies:**
- FE-HIST-013, FE-HIST-010, FE-HIST-012
