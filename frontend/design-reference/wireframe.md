# Wireframe — NeuroAssist AI

> **Última atualização:** 2026-07-27
> **Status:** rascunho
> **Persistência:** PERSISTENTE — vive com o projeto durante todo o MVP

# Etapa do Pipeline: UI Concept → Wireframe Final → Prototype HTML Único

## 1. Objetivo do Artefato

Este documento é a especificação visual estruturada do painel do NeuroAssist AI — a fonte de verdade para o frontend durante todo o MVP. Vem depois do UI Concept (0.3a, aprovado) e antes do Protótipo HTML (0.3c). Cobre as 6 telas necessárias para os 7 fluxos principais identificados no UI Concept: verificação rápida de status, resposta a alarme, ajuste de calibração, consulta de histórico, onboarding de paciente/dispositivo, verificação de status do dispositivo e registro de feedback de falso positivo.

## 2. Princípios de UX que Devem Guiar o Protótipo

Reaproveitados de `ui-concept.md` §2, sem adaptação (todos continuam válidos telas a tela):

1. Estado do paciente sempre visível em 1 relance.
2. Alarme nunca é ambíguo.
3. Toda ação crítica exige confirmação humana explícita.
4. Calibração é acessível a uma pessoa não-técnica.
5. Histórico é legível sem jargão técnico.
6. Status de conectividade do dispositivo nunca fica escondido.
7. A ação mais crítica nunca exige mais de 2 cliques.

## 3. Estrutura Global do HTML Único

### 3.1 Layout Mestre

> Adaptação ao produto: **sem painel de IA lateral**. A IA deste produto (visão computacional determinística no edge) não tem representação própria na UI — ela não conversa, não sugere, não tem painel de "insights". O que a UI mostra é o **resultado** da IA (status do paciente, eventos, alarme), não a IA em si.

```text
┌──────────┬──────────────────────────────────────────────────────┐
│ Sidebar  │ Topbar — paciente ativo · status do dispositivo      │
│ navegação├──────────────────────────────────────────────────────┤
│          │                                                      │
│          │ Conteúdo principal (tela ativa)                     │
│          │                                                      │
└──────────┴──────────────────────────────────────────────────────┘
```

### 3.2 Regiões Fixas

#### A. Sidebar esquerda
Navegação entre: Status (home) · Histórico · Calibração · (Onboarding — visível só para o papel condutor técnico, não aparece para a cuidadora no dia a dia).

#### B. Topbar superior
Mostra o nome do paciente ativo (Márcio Amaral) e o status de conectividade do dispositivo edge (online/offline + última sincronização) — sempre visível, nunca escondido em submenu (Princípio 2.6).

#### D. Conteúdo principal
Sempre presente — renderiza a tela ativa.

#### E. Notificação de rotina *(Emenda 1 — toast global, não é uma tela)*
Quando um comando de urgência `rotina` é reconhecido (ex.: "água", "Sapo"), o sistema mostra um **toast discreto** sobre a tela ativa — não interrompe a navegação, não bloqueia a tela, desaparece após alguns segundos ou com dismiss manual. Diferente da Tela 02 (Alarme), reservada exclusivamente a comandos `emergencia`. Conteúdo do toast: rótulo do comando + timestamp (ex.: "Sapo — agora"), sem exigir confirmação bloqueante (fica registrado no Histórico para revisão posterior).

> Não há região C (painel de IA lateral) neste produto.

## 4. Navegação e Comportamento do Protótipo

### 4.1 Modelo de navegação

Navegação simples por sidebar, sem hierarquia profunda — reflete a baixa densidade de telas do MVP (6 telas). A tela de Alarme (5.2) não é destino de navegação: ela **substitui** a tela ativa automaticamente quando um comando de urgência `emergencia` é reconhecido, independente de onde a cuidadora estiver. Comandos de urgência `rotina` **não** substituem a tela — geram apenas o toast da região E (§3.2).

### 4.2 Estrutura recomendada de IDs

```
#section-entrada        (Tela 00 — momento-marca)
#section-status         (Tela 01 — home)
#section-alarme         (Tela 02 — overlay full-bleed)
#section-historico      (Tela 03)
#section-calibracao     (Tela 04)
#section-onboarding     (Tela 05 — admin)
```

### 4.3 Modelo de dados

```data-model
entity Tenant "Unidade de cuidado — família ou clínica (multi-tenant, schema-per-empresa)"
  id          string pk
  nome        string
  tipo        enum familia|clinica

entity Paciente "Pessoa monitorada pelo sistema"
  id          string pk
  tenantId    ref Tenant label=nome
  nome        string
  dispositivo ref DispositivoEdge label=status

entity Cuidador "Pessoa com acesso ao painel"
  id          string pk
  tenantId    ref Tenant label=nome
  nome        string
  email       string
  papel       enum cuidadora|coordenador_clinica

entity TipoGesto "Catálogo de tipos de movimento facial (primitivos) reconhecidos pelo motor de detecção — extensível (Emenda 2, 2026-07-27)"
  id     string pk
  nome   string

entity Comando "Item do catálogo de comandos do paciente — gesto (simples ou composto) mapeado a uma necessidade (LBC Emenda 1, 2026-07-27)"
  id                    string pk
  pacienteId            ref Paciente label=rotulo
  rotulo                string
  urgencia              enum emergencia|rotina
  primitivoPrincipal    ref TipoGesto label=nome
  primitivoSecundario?  ref TipoGesto label=nome
  sensibilidade         enum baixa|media|alta
  tempoMinimoGestoMs    int
  tempoMaximoGestoMs    int

entity Evento "Ocorrência estruturada gerada pelo edge"
  id             string pk
  pacienteId     ref Paciente label=nome
  comando        ref Comando label=rotulo
  timestamp      datetime
  confirmadoEm   datetime? source=mock
  confirmadoPor  ref Cuidador label=nome
  feedback       enum nao_avaliado|real|falso_positivo

entity DispositivoEdge "Agente local que roda a detecção de gesto"
  id                   string pk
  pacienteId           ref Paciente label=nome
  status               enum online|offline
  ultimaSincronizacao  datetime
  versao               string
```

> **Nota (Emenda 1):** `PerfilCalibracao` (1 por paciente) deixou de existir como entidade — foi substituída por `Comando` (N por paciente). `rotulo` é texto livre definido pela família (ex.: "Sapo", "Suvaco", "água" — o sistema não interpreta o significado, ver LBC §7.2). `primitivoSecundario` nulo = gesto simples (1 primitivo); preenchido = gesto composto (2 primitivos combinados, ex.: piscar 2x + sobrancelha). `Evento.comando` substitui o antigo `Evento.tipo` fixo em `HELP_REQUESTED` — o tipo do evento é o próprio comando disparado, e sua urgência (emergência/rotina) vem de `Comando.urgencia`.
>
> **Nota (Emenda 2, 2026-07-27):** `primitivoPrincipal`/`primitivoSecundario` deixaram de ser um enum fechado de 6 valores — agora referenciam `TipoGesto`, um **catálogo extensível**. A cuidadora/condutor técnico pode cadastrar um novo tipo de movimento (ex.: "Leitura labial") direto na Tela 04, sem depender de release. O catálogo inicial (sobrancelha, piscar duplo, piscar rápido, piscar devagar, fechar os olhos, mover nariz) é seed, não teto. **Atenção:** cadastrar o *nome* de um tipo de gesto na UI não implica que o motor de detecção (edge) já saiba reconhecê-lo — ver `TODO[arquiteto]` no Framing §6.1/§9.3.

## 5. Wireframe Visual Final por Tela

---

# TELA 00 — Entrada

> momento-marca: true

## 5.1 Objetivo

Tela de entrada/login — momento de marca, fora do shell de navegação. Estabelece a identidade calma e confiável do produto antes de qualquer dado operacional aparecer.

## 5.2 Estrutura visual

```text
┌──────────────────────────────────────────────────────────────┐
│                                                                │
│                    [ícone da marca — rede de pessoas]         │
│                                                                │
│              "Um gesto que você não vai perder."               │
│         Webcam comum, alarme confiável, sem hardware caro.    │
│                                                                │
│                     [ Entrar no painel ]                       │
│                                                                │
└──────────────────────────────────────────────────────────────┘
```

## 5.3 Componentes
- Logo (versão fundo escuro — `logo-white.png`)
- Título + subtítulo (tom visual da marca)
- Botão CTA único "Entrar no painel"

## 5.4 Dados — bindings
- lê: (nenhum — tela estática, sem dados operacionais)
- escreve: (nenhum)
- mock: (não aplicável)

## 5.5 Estado dos elementos diferenciadores

Não há painel IA nesta tela. Único elemento diferenciador é o tratamento visual full-bleed (fundo escuro, tipografia grande, sem sidebar/topbar) — reforça a intenção "guardião silencioso" antes de entrar no operacional.

---

# TELA 01 — Status (home)

## 5.1 Objetivo

Responder, em até 2 segundos de leitura, "Márcio está bem?" — a pergunta central do produto (Princípio 2.1). Também mostra o status do dispositivo edge sem exigir navegação extra (Princípio 2.6), cobrindo o fluxo 4.6.

## 5.2 Estrutura visual

```text
┌──────────┬──────────────────────────────────────────────────────┐
│ Status ● │  Márcio Amaral            🟢 Dispositivo online       │
│ Histórico│──────────────────────────────────────────────────────│
│ Calibração│                                                      │
│          │            ✓ (grande, verde)                        │
│          │         Márcio está bem                              │
│          │   Última verificação: 08h12 · sincronizado agora     │
│          │                                                       │
│          │   [ Ver histórico de hoje ]                          │
└──────────┴──────────────────────────────────────────────────────┘
```

## 5.3 Componentes
- StatusCard (ícone grande + texto de estado + timestamp)
- DeviceStatusBadge (online/offline + última sincronização) — também presente na Topbar (3.2.B)
- Link rápido para Histórico do dia

## 5.4 Dados — bindings
- lê: Paciente[], DispositivoEdge[], Evento[] (do dia, com `Evento.comando` resolvido para rótulo + urgência)
- escreve: (nenhuma neste momento — leitura pura)
- mock: pacientes = Paciente × 1
- mock: dispositivos = DispositivoEdge × 1
- mock: comandos = Comando × 3 (catálogo do paciente)
- mock: eventos = Evento × 8 (filtrados no componente para o dia corrente, referenciando comandos do catálogo)

## 5.5 Estado dos elementos diferenciadores

Não há painel IA. O "elemento diferenciador" desta tela é o **estado calmo por padrão** (verde, ícone de check, tom sóbrio) — cromática reservada e nunca alarmante aqui, mesmo em caso de eventos passados já confirmados no dia. *(Emenda 1)* O último evento do dia mostra qual comando foi disparado (rótulo do catálogo, ex.: "água"), não apenas um estado binário bem/mal — mas o tratamento visual (verde/calmo) só muda para alarme se o comando tiver `urgencia=emergencia` e estiver sem confirmação (nesse caso, a Tela 02 assume a tela inteira antes mesmo da cuidadora navegar até aqui).

**Estados obrigatórios:** vazio (nenhum evento hoje — mostra apenas "Márcio está bem" sem seção de eventos), carregamento (skeleton do StatusCard), erro (falha ao buscar status — mensagem clara, não técnica), offline (dispositivo offline — badge âmbar, mas tela NÃO vira alarme; alarme é reservado exclusivamente para `HELP_REQUESTED`).

---

# TELA 02 — Alarme

## 5.1 Objetivo

Ser absolutamente inconfundível no momento em que Márcio precisa de ajuda (Princípio 2.2) e exigir confirmação humana explícita antes de voltar ao estado normal (Princípio 2.3). Cobre o fluxo 4.2 (fluxo-mãe do produto). *(Emenda 1)* Esta tela é **exclusiva para comandos de urgência `emergencia`** do catálogo (ex.: "solicitar ajuda") — comandos `rotina` (ex.: "água") nunca chegam aqui, geram apenas o toast da região E (§3.2).

## 5.2 Estrutura visual

```text
┌──────────────────────────────────────────────────────────────┐
│                                                                │
│                    ⚠ (ícone grande, branco sobre vermelho)     │
│                                                                │
│                Márcio precisa de ajuda                        │
│                Gesto detectado às 14h32                        │
│                                                                │
│           [ Confirmar que atendi Márcio ]                     │
│                                                                │
└──────────────────────────────────────────────────────────────┘
```

O rótulo do comando (ex.: "Ajuda") substitui o texto genérico se fizer sentido para a família — mas o tratamento visual (full-bleed, `--color-alarm`) é sempre o mesmo para qualquer comando `emergencia`, não varia por rótulo.

## 5.3 Componentes
- AlarmOverlay (full-bleed, cor `--color-alarm`, substitui qualquer tela ativa)
- Timestamp do evento
- Botão único de confirmação (nenhuma outra ação disponível — foco total)

## 5.4 Dados — bindings
- lê: Evento[] (mesma coleção usada por Home/Histórico — o evento ativo é filtrado no componente: mais recente com `Comando.urgencia = emergencia` e ainda sem `confirmadoEm`; não é uma coleção separada — evitar duplicar "evento_ativo" como tabela própria, é só uma query)
- escreve: Evento.confirmadoEm, Evento.confirmadoPor
- mock: eventos = Evento × 9 (inclui 1 sem confirmadoEm com urgencia=emergencia, para exercitar o alarme)

## 5.5 Estado dos elementos diferenciadores

Esta tela **é** o elemento diferenciador do produto — a única superfície que usa `--color-alarm`, cor deliberadamente fora da paleta de marca (ver Design System §2). Não há painel IA, não há navegação, não há outra ação possível além de confirmar. Nenhum outro estado de erro/vazio se aplica — esta tela só existe quando há um evento ativo.

---

# TELA 03 — Histórico

## 5.1 Objetivo

Permitir que a cuidadora revise eventos do dia/semana/mês em linguagem humana (Princípio 2.5) e registre feedback de falso positivo/negativo (fluxo 4.7). Cobre o fluxo 4.4.

## 5.2 Estrutura visual

```text
┌──────────┬──────────────────────────────────────────────────────┐
│ Status   │  Histórico                                           │
│ Histórico●│──────────────────────────────────────────────────────│
│ Calibração│  [ Hoje ] [ Semana ] [ Mês ]                          │
│          │                                                       │
│          │  14h32 — "água"                                      │
│          │          Você confirmou às 14h33          [Foi real?]│
│          │                                                       │
│          │  08h05 — "Sapo"                                      │
│          │          Você confirmou às 08h06     [✓ Marcado real]│
│          │                                                       │
└──────────┴──────────────────────────────────────────────────────┘
```

## 5.3 Componentes
- FilterTabs (hoje/semana/mês)
- EventListItem *(Emenda 1)* — mostra o **rótulo do comando** (`Evento.comando.rotulo`), não mais um texto fixo de gesto (ex.: "água" ou "Sapo", entre aspas — é a palavra da família, não uma descrição do sistema); badge de urgência (emergência/rotina) ao lado do rótulo
- Ação de feedback ("Foi real?" → marca `Evento.feedback`)

## 5.4 Dados — bindings
- lê: Evento[] (filtrado por período, com `Evento.comando` resolvido para rótulo + urgência)
- escreve: Evento.feedback
- mock: eventos = Evento × 8 (cobrindo hoje/semana/mês, variando entre os comandos do catálogo)

## 5.5 Estado dos elementos diferenciadores

Sem painel IA. Diferenciador: cada item do histórico mostra o rótulo do comando tal como a família o escreveu (nunca um log técnico do tipo `event_type=HELP_REQUESTED`) — para uma cuidadora nova sem contexto do código da família, o rótulo sozinho pode não ser autoexplicativo (ver risco no LBC §12, Emenda 1).

**Estados obrigatórios:** vazio ("Nenhum evento neste período — ótimo sinal"), carregamento (skeleton de lista), erro (falha ao buscar histórico).

---

# TELA 04 — Calibração

> **Emenda 1 (2026-07-27):** tela reescrita de "1 formulário único" para gestão de **catálogo de comandos** (lista + editor por comando).

## 5.1 Objetivo

Permitir que uma pessoa não-técnica gerencie o **catálogo de comandos** de Márcio (adicionar, editar, remover) e calibre cada um independentemente — gesto, sensibilidade e nível de urgência (Princípio 2.4). Cobre o fluxo 4.3, agora estendido de "1 gesto" para "N comandos, cada um com sua própria calibração".

## 5.2 Estrutura visual

```text
┌──────────┬──────────────────────────────────────────────────────┐
│ Status   │  Calibração — Márcio Amaral            [+ Novo comando]│
│ Histórico│──────────────────────────────────────────────────────│
│Calibração●│  🔴 Ajuda           sobrancelha 2x               [›] │
│          │     emergência · sensibilidade média                 │
│          │                                                       │
│          │  🔵 água            sobrancelha + piscar 2x      [›] │
│          │     rotina · sensibilidade alta                      │
│          │                                                       │
│          │  🔵 Sapo            piscar rápido + devagar      [›] │
│          │     rotina · sensibilidade média                     │
│          │                                                       │
└──────────┴──────────────────────────────────────────────────────┘

── ao abrir um comando (editor) ──────────────────────────────────
┌──────────────────────────────────────────────────────────────┐
│  Comando: [ água                                    ]         │
│  Urgência: ( ) Emergência  (●) Rotina                          │
│                                                                │
│  Gesto principal:   [ Levantar sobrancelha ▾ ]                 │
│  + Adicionar segundo gesto (combinação):                       │
│                     [ Piscar 2x ▾ ]              [remover]     │
│                                                                │
│  Sensibilidade:  Baixa ─────●───── Alta                        │
│                                                                │
│  [ Testar agora com a webcam ]                                 │
│                                                                │
│  [ Cancelar ]                          [ Salvar comando ]      │
└──────────────────────────────────────────────────────────────┘
```

## 5.3 Componentes
- CommandList *(novo)* — lista o catálogo, cada item mostra rótulo, badge de urgência (cor `--color-alarm` para emergência, cor neutra para rotina), gesto (1 ou 2 primitivos) e sensibilidade; ação de abrir editor ou remover
- CommandEditor *(novo)* — formulário do comando individual, reutiliza os campos abaixo; abre para "Novo comando" ou edição de um existente
- LabelInput — campo de texto livre para o rótulo (a família escreve a própria palavra-código, ex.: "água", "Sapo" — sem sugestão nem validação de significado)
- UrgencySelector *(novo)* — escolha binária emergência/rotina, com explicação inline de que emergência dispara a Tela 02 (full-bleed) e rotina dispara só o toast (§3.2.E)
- GestureSelector *(Emenda 2)* — dropdown sobre o catálogo `TipoGesto` (exemplos visuais, não nomes técnicos), usado 1x para o primitivo principal e opcionalmente 1x mais para o secundário (gesto composto); inclui ação "+ Cadastrar novo tipo de gesto" (ex.: "Leitura labial") que adiciona ao catálogo e seleciona na hora — catálogo não é mais fechado em 6 valores
- SensitivitySlider (visual, 3-5 níveis nomeados, não valores numéricos crus)
- TestLiveButton (testa a combinação de gesto ao vivo antes de salvar — crítico quando há 2 primitivos, já que o risco de falso negativo é maior, ver LBC §12 Emenda 1)
- SaveButton / RemoveButton (por comando)

## 5.4 Dados — bindings
- lê: Comando[] (todos do paciente ativo)
- escreve: Comando (criar/editar/remover), campos: rotulo, urgencia, primitivoPrincipal, primitivoSecundario, sensibilidade
- mock: comandos = Comando × 3 — inspirados no catálogo real do Márcio: "Ajuda" (emergência, sobrancelha), "água" (rotina, sobrancelha + piscar 2x), "Sapo" (rotina, piscar rápido + devagar)

## 5.5 Estado dos elementos diferenciadores

Sem painel IA. O "teste ao vivo" continua sendo o elemento diferenciador — mais importante ainda agora, porque testar uma combinação de 2 primitivos dá menos margem de erro do que testar 1 primitivo isolado. A lista (CommandList) é o novo elemento estrutural desta tela: substitui o formulário único da v1.

**Estados obrigatórios:** vazio (catálogo sem nenhum comando — CTA "Adicionar primeiro comando"), carregamento (skeleton da lista), erro (falha ao salvar — mensagem clara), sincronizando (aguardando propagação do catálogo completo para o dispositivo edge no próximo heartbeat).

---

# TELA 05 — Onboarding (admin/setup)

## 5.1 Objetivo

Cadastro inicial de tenant, paciente e pareamento do dispositivo edge — conduzido uma única vez por Danilo Amaral (condutor técnico), não pela cuidadora no dia a dia. Cobre o fluxo 4.5.

## 5.2 Estrutura visual

```text
┌──────────────────────────────────────────────────────────────┐
│  Configuração inicial                                        │
│──────────────────────────────────────────────────────────────│
│  1. Unidade de cuidado:   [ Família Amaral            ]       │
│  2. Paciente:             [ Márcio Amaral             ]       │
│  3. Dispositivo edge:     [ Pareando... ● ]                   │
│  4. Primeiro comando:     → vai para Tela 04 (catálogo)      │
│  5. Convidar cuidadora:   [ Jamilly Amaral · email    ]       │
│                                                                │
│                    [ Concluir configuração ]                  │
└──────────────────────────────────────────────────────────────┘
```

## 5.3 Componentes
- Wizard de passos (tenant → paciente → dispositivo → primeiro comando → convite)
- DevicePairingStatus (indicador de pareamento em andamento)
- InviteCaregiverForm

## 5.4 Dados — bindings
- lê: Tenant[], Paciente[], DispositivoEdge[]
- escreve: Tenant, Paciente, DispositivoEdge, Cuidador (cria os registros); Comando (opcionalmente já cria o primeiro comando do catálogo, se o condutor preencher na Tela 04 antes de voltar)
- mock: (tela de setup — sem mock, dados reais inseridos no fluxo)

## 5.5 Estado dos elementos diferenciadores

Sem painel IA. Tela de uso único/raro — não faz parte da rotina diária da cuidadora, por isso não aparece na sidebar dela (visível só para o papel condutor técnico).

**Estados obrigatórios:** carregamento (pareamento do dispositivo em andamento), erro (falha ao parear dispositivo — orientação de retry), sucesso (confirmação antes de redirecionar para Tela 01).

---
