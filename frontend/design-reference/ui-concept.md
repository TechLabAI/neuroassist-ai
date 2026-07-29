# UI Concept — NeuroAssist AI

> **Última atualização:** 2026-07-27
> **Status:** rascunho

Este documento captura a **intenção de UX** que precede o wireframe estruturado.
Define princípios, persona em jornada e fluxos principais que guiam todas
as decisões de design do painel da cuidadora.

---

## 1. Intenção

O NeuroAssist AI deve transmitir a sensação de um **guardião silencioso e confiável** — algo que existe em segundo plano na rotina da cuidadora, sem exigir atenção constante, mas que se torna **absolutamente inconfundível** no momento em que o paciente precisa de ajuda.

A experiência é desenhada para uma pessoa que:
- não é usuária de tecnologia por profissão — é uma cuidadora familiar, sem tempo nem paciência para interfaces complexas;
- olha o painel de forma **periférica e esporádica** ao longo do dia, não como ferramenta de trabalho contínuo;
- precisa, no momento de um alarme real, reconhecer a emergência **instantaneamente**, sem ambiguidade e sem precisar interpretar dados.

Isso difere de um dashboard operacional denso (como um cockpit de gestão): aqui a maior parte do tempo a tela deve ser **calma e discreta** — o oposto de ansiedade constante — reservando intensidade visual (cor, som, contraste) exclusivamente para o momento em que o alarme dispara de verdade.

---

## 2. Princípios de UX

Cada princípio é **acionável**: alguém deve poder olhar uma tela e dizer
"isso viola o princípio N".

### 2.1 Estado do paciente sempre visível em 1 relance

Ao abrir o painel, a cuidadora vê imediatamente se está tudo normal, sem precisar navegar. Não há tela inicial "neutra" que exija interpretação.

**Como verificar:** se a tela inicial não responde "Márcio está bem?" em menos de 2 segundos de leitura, viola o princípio.

### 2.2 Alarme nunca é ambíguo

Quando um evento `HELP_REQUESTED` dispara, a interface muda de forma inconfundível: cor de alerta, texto grande, ocupando a tela inteira — não um badge discreto no canto.

**Como verificar:** mostre a tela de alarme a alguém que nunca usou o produto. Em menos de 1 segundo, a pessoa reconhece que é uma emergência? Se não, refinar.

### 2.3 Toda ação crítica exige confirmação humana explícita

Nenhum alarme "desaparece" sozinho. A cuidadora precisa confirmar explicitamente que atendeu, antes de o sistema voltar ao estado normal.

**Como verificar:** todo alarme tem um botão explícito de confirmação (ex: "Confirmar que atendi Márcio") — sem isso, a tela de alarme não pode sumir.

### 2.4 Calibração é acessível a uma pessoa não-técnica

Ajustar sensibilidade do gesto usa controles visuais simples (sliders, exemplos, testes ao vivo) — nunca campos técnicos crus (ex: "threshold: 0.73").

**Como verificar:** peça para alguém sem formação técnica ajustar a sensibilidade sem ajuda. Se travar, viola o princípio.

### 2.5 Histórico é legível sem jargão técnico

Eventos aparecem em linguagem humana ("Márcio levantou a sobrancelha às 14:32 — você confirmou às 14:33"), nunca como log técnico (`event_type=HELP_REQUESTED`).

**Como verificar:** qualquer entrada do histórico deve fazer sentido para alguém lendo pela primeira vez, sem explicação.

### 2.6 Status de conectividade do dispositivo nunca fica escondido

Mesmo que o alarme local funcione offline (constraint OPS-001 do Framing), a cuidadora precisa sempre ver se o painel está sincronizado com o dispositivo — isso não pode estar enterrado em uma tela de configurações.

**Como verificar:** o status do dispositivo (online/offline, última sincronização) aparece na tela principal, não só em "Configurações".

### 2.7 A ação mais crítica nunca exige mais de 2 cliques

Ver o status atual do paciente e confirmar um alarme devem ser as ações mais rápidas do sistema — nunca escondidas atrás de menus.

**Como verificar:** cronometre o caminho da tela inicial até a confirmação de um alarme. Mais de 2 toques/cliques viola o princípio.

---

## 3. Persona em jornada

> **Nota de escopo:** o MVP tem uma única persona real de uso do produto — Jamilly Amaral (cuidadora). Márcio Amaral é o paciente monitorado, não-verbal, sem interação direta com a interface. Não há segunda persona validada nesta fase (ver LBC §4).

### 3.1 Jornada — Jamilly Amaral (Cuidadora Familiar)

```
Manhã (chega para o turno de cuidado)
  → Abre o painel no notebook/tablet
  → Tela inicial: "Márcio — monitorando normalmente. Última verificação: 08h12."
  → Confere e segue a rotina do dia, painel em segundo plano

Durante o dia (uso periférico, não contínuo)
  → Painel aberto em segundo plano, sem exigir atenção
  → Evento HELP_REQUESTED é detectado às 14h32
  → Alarme sonoro local dispara imediatamente (funciona mesmo se a internet cair)
  → Tela muda para tela de alarme em tela cheia — inconfundível
  → Jamilly vai até Márcio, atende a necessidade
  → Volta ao painel e confirma "Confirmar que atendi Márcio"
  → Tela volta ao estado normal

Fim do dia
  → Revisa o histórico do dia: quantos eventos, quantos foram confirmados
  → Percebe um falso positivo (ex: gesto involuntário) e ajusta levemente a sensibilidade
```

**Frequência de uso:** contínua em segundo plano, com poucas interações ativas por dia (verificação matinal + eventual alarme + revisão noturna).
**Dispositivo principal:** notebook ou tablet fixo próximo ao ambiente de cuidado.
**Objetivo dominante:** confiar que, se Márcio precisar de ajuda, ela vai saber — sem precisar vigiar constantemente.

---

## 4. Fluxos principais

### 4.1 Fluxo — Verificação rápida de status (fluxo-mãe)

```
1. Jamilly abre o painel
2. Sistema mostra imediatamente: paciente + estado atual (normal/alarme/offline)
3. Jamilly confirma visualmente que está tudo bem
4. Fim — sem necessidade de mais nenhuma ação
```

### 4.2 Fluxo — Resposta a alarme

```
1. GestureDetectionEngine (edge) reconhece o gesto configurado
2. LocalAlarmPlayer dispara o alarme sonoro local imediatamente (funciona offline)
3. Evento sincroniza com o backend quando há conectividade
4. Painel muda para tela de alarme em tela cheia, com o horário do evento
5. Jamilly atende Márcio
6. Jamilly confirma "Confirmar que atendi" no painel
7. Sistema registra o horário de confirmação e volta ao estado normal
```

### 4.3 Fluxo — Ajuste de calibração de sensibilidade

```
1. Jamilly percebe um falso positivo ou falso negativo no histórico
2. Acessa a tela de calibração do paciente
3. Ajusta sensibilidade/tempo do gesto via controles simples (slider, teste ao vivo)
4. Sistema salva e sincroniza a nova calibração de volta para o dispositivo edge
```

### 4.4 Fluxo — Consulta de histórico

```
1. Jamilly acessa a tela de histórico
2. Vê lista de eventos em linguagem humana (data, hora, tipo de gesto, se foi confirmado)
3. Pode filtrar por período (hoje, semana, mês)
```

### 4.5 Fluxo — Onboarding inicial (cadastro de paciente + dispositivo)

```
1. Danilo (condutor técnico) cadastra o tenant (família Amaral)
2. Cadastra o paciente (Márcio Amaral)
3. Pareia o dispositivo edge com o paciente
4. Faz a calibração inicial do gesto (ex: levantar sobrancelha)
5. Jamilly é convidada como cuidadora com acesso ao painel
```

### 4.6 Fluxo — Verificação de status do dispositivo (edge online/offline)

```
1. Jamilly vê no painel o status do dispositivo edge (online/offline, última sincronização)
2. Se offline há muito tempo, painel destaca visualmente o alerta (sem ser tão intenso quanto o alarme de paciente)
3. Jamilly verifica fisicamente o dispositivo/conexão
```

### 4.7 Fluxo — Registro de feedback de falso positivo/negativo

```
1. Jamilly identifica, no histórico, um evento que não era real (falso positivo)
2. Marca o evento como "não era um pedido de ajuda real"
3. Sistema usa esse feedback como insumo para sugestão futura de recalibração (expansão — não obrigatório no MVP)
```

---

## 5. Tom visual de alto nível

### Densidade
- **Padrão:** **espaçosa/confortável** — poucas informações por tela, foco total no essencial (estado do paciente). Não é ferramenta de power-user.

### Cromática
- **Tom principal:** **sóbrio e calmo** na maior parte do tempo — tons neutros, baixo contraste emocional, para não gerar ansiedade constante.
- **Exceção deliberada:** o estado de alarme usa cor de alerta intensa (vermelho, alto contraste) exclusivamente quando o evento é real — contraste absoluto com o resto da experiência, para ser inconfundível.
- **Razão:** "calm technology" — a interface deve ser invisível quando tudo está bem, e impossível de ignorar quando não está.

### Tipografia
- Sans-serif legível, tamanhos grandes para textos de estado (ex: "Márcio está bem") — prioriza leitura rápida por alguém cansado ou estressado, não densidade informacional.

### Iconografia
- Simples, poucos ícones, com significado óbvio sem necessidade de legenda (ex: ícone de "conectado" vs "desconectado").

### Referências visuais externas
- Apple Health / apps de monitoramento doméstico de saúde — calma, confiável, sem jargão técnico.
- Apps de alarme residencial (ex: Ring) — estado normal discreto, estado de alerta inconfundível.

---

## 6. O que NÃO está neste UI Concept

- **Layout específico das telas** → ver `wireframe.md` (0.3b)
- **Componentes detalhados por tela** → ver `wireframe.md` §5
- **Paleta de cores final / tokens** → ver `design-system/theme.css` (0.3a+)
- **Estrutura HTML/JSX exata** → ver `prototype-bundle.html` (0.3c)

---

## 7. Validação dos princípios — checklist contínuo

- [ ] A tela inicial responde "Márcio está bem?" em 2 segundos? (Princípio 2.1)
- [ ] O alarme é reconhecível como emergência em menos de 1 segundo, sem contexto prévio? (Princípio 2.2)
- [ ] Toda ação crítica exige confirmação explícita? (Princípio 2.3)
- [ ] Uma pessoa não-técnica consegue calibrar sozinha? (Princípio 2.4)
- [ ] O histórico é legível sem jargão técnico? (Princípio 2.5)
- [ ] O status de conectividade do dispositivo está sempre visível? (Princípio 2.6)
- [ ] A ação mais crítica (ver status / confirmar alarme) leva no máximo 2 cliques? (Princípio 2.7)
