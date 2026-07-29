# Build-Plan — Historico (historico)
wireframe-ref: §5.1–5.5 | component: HistoricoComponent | route: /historico | ai-drawer: não

> Contrato gerado por wireframe-to-angular. O implementer lê **só** este
> arquivo + as fatias referenciadas de `../../data-model.json` e
> `../../mock-data.json`. Não voltar ao wireframe. DESCARTÁVEL (regenera com --force).

## Objetivo
Permitir que a cuidadora revise eventos do dia/semana/mês em linguagem humana (Princípio 2.5) e registre feedback de falso positivo/negativo (fluxo 4.7). Cobre o fluxo 4.4.

## Regiões / layout (best-effort da §5.2)
- Status Histórico
- Histórico●
- Calibração [ Hoje ] [ Semana ] [ Mês ]
- 14h32 — "água"
- Você confirmou às 14h33 [Foi real?]
- 08h05 — "Sapo"
- Você confirmou às 08h06 [✓ Marcado real]

## Componentes
- FilterTabs (hoje/semana/mês) → local → inline no .component.ts (compor da biblioteca DS app/ui/)
- EventListItem *(Emenda 1)* — mostra o **rótulo do comando** (`Evento.comando.rotulo`), não mais um texto fixo de gesto (ex.: "água" ou "Sapo", entre aspas — é a palavra da família, não uma descrição do sistema); badge de urgência (emergência/rotina) ao lado do rótulo → local → inline no .component.ts (compor da biblioteca DS app/ui/)
- Ação de feedback ("Foi real?" → marca `Evento.feedback`) → local → inline no .component.ts (compor da biblioteca DS app/ui/)

## Ligações de dados (data-model.json)
- lê: Evento[] (filtrado por período
- lê: com `Evento.comando` resolvido para rótulo + urgência)
- escreve: Evento.feedback

## Mock (mock-data.json)
- eventos: 8 instâncias de Evento → mock-data.json#/eventos

> Importar do `mock-data.json` — **não** reinventar `const MOCK` no componente.

## Estados obrigatórios
- empty, loading, error

## IA inline
- IA não disponível nesta tela.
