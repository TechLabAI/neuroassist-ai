# SPEC-04 — Data Model & Migrations

## Objetivo
Fixar a postura de persistência do DASH: **zero tabelas no MVP** (ADR-004). Fonte canônica:
`contracts/data-model.md`.

## Escopo
- **In:** ausência de store durável; registro de sessão in-memory; tabela opcional diferida.
- **Out:** dados de domínio (vivem nos serviços donos: HIST, PAC, EVNT, EDGE, ACS).

## Decisões técnicas
- DASH **não contribui com scripts Flyway** no MVP (MIG é centralizado, ADR-003 shared).
- Estado de runtime = `PanelSession` in-memory (efêmero), nunca linha de banco.
- Tabela `caregiver_ui_preference` é **diferida** e documentada como bloco não-`sql` para o gerador de
  migração **não** materializá-la.

## Casos de borda
- Restart do DASH → nenhuma perda de dado de domínio.
- Se a preferência opcional for aprovada → um `V<n>__dash_caregiver_ui_preference.sql` por schema `t_<empresa>`,
  aplicado pela MIG; DASH ainda não roda DDL de boot.

## Critérios de aceite (EARS)
- THE SYSTEM SHALL persist no operational aggregate in the MVP.
- THE SYSTEM SHALL hold the SSE session registry in memory only.
- IF the optional preference table is ever built, THEN THE SYSTEM SHALL store only non-sensitive
  presentation data within the tenant schema.
- THE SYSTEM SHALL run no boot-time DDL (migrations centralized in MIG).
