# Build-Plan — Alarme (alarme)
wireframe-ref: §5.1–5.5 | component: AlarmeComponent | route: /alarme | ai-drawer: não

> Contrato gerado por wireframe-to-angular. O implementer lê **só** este
> arquivo + as fatias referenciadas de `../../data-model.json` e
> `../../mock-data.json`. Não voltar ao wireframe. DESCARTÁVEL (regenera com --force).

## Objetivo
Ser absolutamente inconfundível no momento em que Márcio precisa de ajuda (Princípio 2.2) e exigir confirmação humana explícita antes de voltar ao estado normal (Princípio 2.3). Cobre o fluxo 4.2 (fluxo-mãe do produto). *(Emenda 1)* Esta tela é **exclusiva para comandos de urgência `emergencia`** do

## Regiões / layout (best-effort da §5.2)
- ⚠ (ícone grande, branco sobre vermelho)
- Márcio precisa de ajuda
- Gesto detectado às 14h32
- [ Confirmar que atendi Márcio ]
- O rótulo do comando (ex.: "Ajuda") substitui o texto genérico se fizer sentido para a família — mas o tratamento visual (full-bleed, `--color-alarm`) é sempre o mesmo para qualquer comando `emergencia`, não varia por rótulo.

## Componentes
- AlarmOverlay (full-bleed, cor `--color-alarm`, substitui qualquer tela ativa) → local → inline no .component.ts (compor da biblioteca DS app/ui/)
- Timestamp do evento → local → inline no .component.ts (compor da biblioteca DS app/ui/)
- Botão único de confirmação (nenhuma outra ação disponível — foco total) → local → inline no .component.ts (compor da biblioteca DS app/ui/)

## Ligações de dados (data-model.json)
- lê: Evento (o evento ativo cujo `Comando.urgencia = emergencia`)
- escreve: Evento.confirmadoEm
- escreve: Evento.confirmadoPor

## Mock (mock-data.json)
- evento_ativo: 1 instâncias de Evento → mock-data.json#/evento_ativo

> Importar do `mock-data.json` — **não** reinventar `const MOCK` no componente.

## Estados obrigatórios
- empty, loading, error

## IA inline
- IA não disponível nesta tela.
