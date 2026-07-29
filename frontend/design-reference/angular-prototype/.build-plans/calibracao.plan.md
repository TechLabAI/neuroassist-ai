# Build-Plan — Calibracao (calibracao)
wireframe-ref: §5.1–5.5 | component: CalibracaoComponent | route: /calibracao | ai-drawer: não

> Contrato gerado por wireframe-to-angular. O implementer lê **só** este
> arquivo + as fatias referenciadas de `../../data-model.json` e
> `../../mock-data.json`. Não voltar ao wireframe. DESCARTÁVEL (regenera com --force).

## Objetivo
Permitir que uma pessoa não-técnica gerencie o **catálogo de comandos** de Márcio (adicionar, editar, remover) e calibre cada um independentemente — gesto, sensibilidade e nível de urgência (Princípio 2.4). Cobre o fluxo 4.3, agora estendido de "1 gesto" para "N comandos, cada um com sua própria cal

## Regiões / layout (best-effort da §5.2)
- Status Calibração — Márcio Amaral [ Novo comando]
- Histórico
- Calibração● 🔴 Ajuda sobrancelha 2x [›]
- emergência sensibilidade média
- 🔵 água sobrancelha piscar 2x [›]
- rotina sensibilidade alta
- 🔵 Sapo piscar rápido devagar [›]
- rotina sensibilidade média
- ao abrir um comando (editor)
- Comando: [ água ]
- Urgência: ( ) Emergência (●) Rotina
- Gesto principal: [ Levantar sobrancelha ▾ ]

## Componentes
- CommandList *(novo)* — lista o catálogo, cada item mostra rótulo, badge de urgência (cor `--color-alarm` para emergência, cor neutra para rotina), gesto (1 ou 2 primitivos) e sensibilidade; ação de abrir editor ou remover → local → inline no .component.ts (compor da biblioteca DS app/ui/)
- CommandEditor *(novo)* — formulário do comando individual, reutiliza os campos abaixo; abre para "Novo comando" ou edição de um existente → local → inline no .component.ts (compor da biblioteca DS app/ui/)
- LabelInput — campo de texto livre para o rótulo (a família escreve a própria palavra-código, ex.: "água", "Sapo" — sem sugestão nem validação de significado) → local → inline no .component.ts (compor da biblioteca DS app/ui/)
- UrgencySelector *(novo)* — escolha binária emergência/rotina, com explicação inline de que emergência dispara a Tela 02 (full-bleed) e rotina dispara só o toast (§3.2.E) → local → inline no .component.ts (compor da biblioteca DS app/ui/)
- GestureSelector (dropdown com exemplos visuais, não nomes técnicos) — usado 1x para o primitivo principal, e opcionalmente 1x mais para o primitivo secundário (gesto composto) → local → inline no .component.ts (compor da biblioteca DS app/ui/)
- SensitivitySlider (visual, 3-5 níveis nomeados, não valores numéricos crus) → local → inline no .component.ts (compor da biblioteca DS app/ui/)
- TestLiveButton (testa a combinação de gesto ao vivo antes de salvar — crítico quando há 2 primitivos, já que o risco de falso negativo é maior, ver LBC §12 Emenda 1) → local → inline no .component.ts (compor da biblioteca DS app/ui/)
- SaveButton / RemoveButton (por comando) → local → inline no .component.ts (compor da biblioteca DS app/ui/)

## Ligações de dados (data-model.json)
- lê: Comando[] (todos do paciente ativo)
- escreve: Comando (criar/editar/remover)
- escreve: campos: rotulo
- escreve: urgencia
- escreve: primitivoPrincipal
- escreve: primitivoSecundario
- escreve: sensibilidade

## Mock (mock-data.json)
- comandos: 3 instâncias de Comando → mock-data.json#/comandos

> Importar do `mock-data.json` — **não** reinventar `const MOCK` no componente.

## Estados obrigatórios
- empty, loading, error

## IA inline
- IA não disponível nesta tela.
