# Build-Plan — Entrada (entrada)
wireframe-ref: §5.1–5.5 | component: EntradaComponent | route: /entrada | ai-drawer: não

> Contrato gerado por wireframe-to-angular. O implementer lê **só** este
> arquivo + as fatias referenciadas de `../../data-model.json` e
> `../../mock-data.json`. Não voltar ao wireframe. DESCARTÁVEL (regenera com --force).

## Objetivo
Tela de entrada/login — momento de marca, fora do shell de navegação. Estabelece a identidade calma e confiável do produto antes de qualquer dado operacional aparecer.

## Regiões / layout (best-effort da §5.2)
- [ícone da marca — rede de pessoas]
- "Um gesto que você não vai perder."
- Webcam comum, alarme confiável, sem hardware caro.
- [ Entrar no painel ]

## Componentes
- Logo (versão fundo escuro — `logo-white.png`) → local → inline no .component.ts (compor da biblioteca DS app/ui/)
- Título + subtítulo (tom visual da marca) → local → inline no .component.ts (compor da biblioteca DS app/ui/)
- Botão CTA único "Entrar no painel" → local → inline no .component.ts (compor da biblioteca DS app/ui/)

## Ligações de dados (data-model.json)
- lê: (nenhum — tela estática
- lê: sem dados operacionais)
- escreve: (nenhum)

## Mock (mock-data.json)
- (sem coleção de mock declarada)

## Estados obrigatórios
- empty, loading, error

## IA inline
- IA não disponível nesta tela.
