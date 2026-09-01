# Wireframe — NeuroAssist AI

> **Última atualização:** 2026-08-31
> **Status:** rascunho; Telas 06/07 (Emenda 3, 2026-08-30) e Telas 08/09 (Emenda 4, 2026-08-31) são draft BA — pendentes de validação do arquiteto e não implementadas no protótipo Angular existente
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

> **(Emenda 3, 2026-08-30, draft BA)** Quando o dispositivo ativo é o celular do próprio paciente (não a máquina fixa da cuidadora), a Topbar mostra um indicador de **"modo paciente"** — badge textual simples (ex.: "Sessão do paciente · ativa desde 12/08") ao lado do status de conectividade, sem esconder nem substituir os elementos existentes. Este indicador só aparece quando `SessaoPaciente.status = ativa` para o dispositivo em uso — TODO[arquiteto]: confirmar se essa distinção é resolvida no frontend (mesmo shell, indicador condicional) ou se o app do paciente é um shell separado, mais restrito (só CONV/GEST, sem sidebar de Histórico/Calibração) — ver ADR-012/013.

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
#section-conversa       (Tela 06 — árvore de falas) — (Emenda 3)
#section-ativacao-paciente (Tela 07 — ativação do celular do paciente) — (Emenda 3)
#section-treinamento    (Tela 08 — sessão de TREINAMENTO, shell cuidadora/DASH) — (Emenda 4)
#section-confirmacao-paciente (Tela 09 — confirmação do paciente, patient-shell) — (Emenda 4)
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

entity NoArvoreFala "Nó da árvore de falas — categoria ou frase (Emenda 3, 2026-08-30, draft BA)"
  id          string pk
  pacienteId  ref Paciente label=nome
  paiId       ref NoArvoreFala? label=texto
  tipo        enum categoria|frase
  texto       string
  ordem       int

entity SessaoPaciente "Sessão de app persistente do paciente, provisionada pela cuidadora (Emenda 3 — modelo de identidade final é TODO[arquiteto], ver ADR-012)"
  id              string pk
  pacienteId      ref Paciente label=nome
  provisionadaPor ref Cuidador label=nome
  dispositivoId   ref DispositivoEdge label=status
  status          enum ativa|revogada
  revogadaEm      datetime?
  revogadaPor     ref Cuidador? label=nome
```

> **Nota (Emenda 1):** `PerfilCalibracao` (1 por paciente) deixou de existir como entidade — foi substituída por `Comando` (N por paciente). `rotulo` é texto livre definido pela família (ex.: "Sapo", "Suvaco", "água" — o sistema não interpreta o significado, ver LBC §7.2). `primitivoSecundario` nulo = gesto simples (1 primitivo); preenchido = gesto composto (2 primitivos combinados, ex.: piscar 2x + sobrancelha). `Evento.comando` substitui o antigo `Evento.tipo` fixo em `HELP_REQUESTED` — o tipo do evento é o próprio comando disparado, e sua urgência (emergência/rotina) vem de `Comando.urgencia`.
>
> **Nota (Emenda 3, 2026-08-30, draft BA):** `NoArvoreFala` e `SessaoPaciente` são entidades novas, ainda **não implementadas** no protótipo Angular existente (`design/angular-prototype/`) — cobrem as Telas 06/07 abaixo. `SessaoPaciente` modela apenas o rastro de auditoria (quem provisionou, status, revogação); o **mecanismo de autenticação real** por trás dela (token de dispositivo vs. sessão delegada vs. ator próprio) é decisão em aberto — ver ADR-012 (draft BA). Não confundir `SessaoPaciente` com `Cuidador` — o paciente continua sem login próprio (usuário/senha); a sessão é provisionada, não criada por ele.
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

# TELA 06 — Árvore de Falas *(Emenda 3, 2026-08-30 — draft BA, não implementada no protótipo)*

## 5.1 Objetivo

Permitir que o paciente navegue por uma árvore de categorias → frases e comunique uma necessidade específica, usando o canal de entrada que ele conseguir operar — gesto (reaproveitando GEST) ou toque/mouse direto. Cobre os requisitos §3.1 do briefing pós-handoff. Esta tela é o destino principal do "modo paciente" (ver §3.2.B).

## 5.2 Estrutura visual

```text
┌──────────────────────────────────────────────────────────────┐
│  🔵 Sessão do paciente               🟢 Dispositivo online    │
│──────────────────────────────────────────────────────────────│
│                                                                │
│   [ 🥤 Preciso de algo ]   [ 🛏 Reposicionar ]   [ 💬 Falar ]  │
│                                                                │
│         (nó selecionado destacado — mesmo destino             │
│          alcançável por gesto reconhecido OU por toque)       │
│                                                                │
│                    [ ‹ Voltar para categorias ]                │
└──────────────────────────────────────────────────────────────┘
```

Ao selecionar um nó folha (frase), o sistema reproduz a frase em voz (TTS — escopo exato a confirmar com o arquiteto) e opcionalmente gera um `Evento` no catálogo de comandos existente, se o nó estiver mapeado a um `Comando` (ver TODO[arquiteto] no Framing §4.8/§12 pergunta 6 — mapeamento ainda não decidido).

## 5.3 Componentes

- SpeechTreeNavigator *(novo)* — renderiza o nível ativo da árvore (categorias ou frases-folha), destaca visualmente o nó atualmente "selecionado" independente do canal de entrada
- InputChannelIndicator *(novo)* — mostra discretamente qual canal está ativo (gesto ou toque), sem exigir escolha manual prévia — os dois canais podem coexistir e alimentar o mesmo modelo de nó selecionado
- TTSPlayer *(novo, escopo a confirmar)* — reproduz a frase selecionada em voz
- BackButton — volta ao nível anterior da árvore

## 5.4 Dados — bindings

- lê: `NoArvoreFala[]` (filtrado por `pacienteId` e `paiId` do nível ativo)
- escreve: (nenhuma escrita direta nesta tela — seleção de nó pode gerar `Evento`, ver TODO[arquiteto] acima)
- mock: TODO — sem mock ainda definido; esta tela não existe no protótipo Angular atual, ver nota de status do documento

## 5.5 Estado dos elementos diferenciadores

Sem painel IA. O elemento diferenciador é o `InputChannelIndicator` — reforça ao paciente e à cuidadora que o mesmo destino é alcançável por qualquer canal disponível, sem hierarquia entre eles.

**Estados obrigatórios:** vazio (árvore ainda não configurada para este paciente — CTA para a cuidadora configurar), carregamento (skeleton dos nós), erro (falha ao carregar árvore), TTS indisponível (fallback visual — texto da frase em destaque, sem áudio).

---

# TELA 07 — Ativação do celular do paciente *(Emenda 3, 2026-08-30 — draft BA, não implementada no protótipo)*

## 5.1 Objetivo

Permitir que a cuidadora ative o celular do paciente como dispositivo edge completo (webcam própria + árvore de falas), provisionando uma sessão persistente sem exigir credencial própria do paciente. Extensão do fluxo de pareamento existente (Tela 05) para o caso "dispositivo é o celular do próprio paciente", não substituição dele — TODO[arquiteto]: confirmar se reaproveita o mesmo fluxo de código de pareamento (`awaiting-claim` → heartbeat, já implementado) ou exige fluxo de provisionamento próprio (ver Framing §12, pergunta 7).

## 5.2 Estrutura visual

```text
┌──────────────────────────────────────────────────────────────┐
│  Ativar celular do paciente                                  │
│──────────────────────────────────────────────────────────────│
│  1. Paciente:              [ Márcio Amaral            ]       │
│  2. Celular do paciente:   [ Pareando... ● ]                  │
│  3. Sessão persistente:    ⚠ não expira — revogável a         │
│                               qualquer momento nesta tela      │
│                                                                │
│                    [ Ativar celular do paciente ]              │
│──────────────────────────────────────────────────────────────│
│  Sessões ativas neste paciente:                               │
│  📱 iPhone de Márcio — ativa desde 30/08         [ Revogar ]  │
└──────────────────────────────────────────────────────────────┘
```

## 5.3 Componentes

- DevicePairingStatus (reaproveitado da Tela 05)
- PersistentSessionWarning *(novo)* — aviso explícito de que a sessão não expira, antes de confirmar a ativação
- ActiveSessionsList *(novo)* — lista `SessaoPaciente[]` do paciente, com ação de revogação individual (constraint SEC-001 — mecanismo obrigatório, não opcional)
- RevokeButton *(novo)* — revoga uma `SessaoPaciente` (`status → revogada`, `revogadaEm`, `revogadaPor`)

## 5.4 Dados — bindings

- lê: `Paciente[]`, `DispositivoEdge[]`, `SessaoPaciente[]` (do paciente ativo)
- escreve: `SessaoPaciente` (criar ao ativar; atualizar `status`/`revogadaEm`/`revogadaPor` ao revogar)
- mock: TODO — sem mock ainda definido; esta tela não existe no protótipo Angular atual

## 5.5 Estado dos elementos diferenciadores

Sem painel IA. O elemento diferenciador é o `ActiveSessionsList` com revogação sempre visível e de fácil acesso — nunca escondida atrás de configuração avançada, dado que SEC-001 marca a revogação como mecanismo obrigatório.

**Estados obrigatórios:** carregamento (pareamento em andamento), erro (falha ao ativar — orientação de retry), sucesso (confirmação + sessão aparece na lista), vazio (nenhuma sessão de paciente ativa ainda).

---

---

# TELA 08 — Treinamento (sessão com LLM) *(Emenda 4, 2026-08-31 — draft BA, não implementada no protótipo)*

## 5.1 Objetivo

Permitir que a cuidadora inicie/assista uma conversa com a LLM para propor categorias/frases novas da árvore de falas (CONV), em cada idioma (PT/EN) — fase separada do uso diário do paciente (ADR-016). Vive no shell da cuidadora (§3.1/§3.2.A, DASH), não no shell restrito do paciente. O que é proposto aqui fica pendente até o paciente confirmar na Tela 09 — ver ADR-018 §2 (autoria independente por idioma) e ADR-016 (RBAC de dupla autorização).

## 5.2 Estrutura visual

```text
┌──────────────────────────────────────────────────────────────┐
│  Treinamento da árvore de falas          [ PT ▾ ]  [ EN ]     │
│──────────────────────────────────────────────────────────────│
│  💬 LLM: "Quer adicionar uma frase para pedir água com gelo?" │
│  💬 Cuidadora: "Sim, e também uma para chamar o Danilo"       │
│──────────────────────────────────────────────────────────────│
│  Propostas pendentes (idioma ativo: PT)                       │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ 💧 "Quero água com gelo"        [PT ✅ cuidadora]        │  │
│  │    categoria: Preciso de algo   [PT ⏳ paciente]  [Editar]│  │
│  ├────────────────────────────────────────────────────────┤  │
│  │ 📞 "Chamar o Danilo"            [PT ⏳ cuidadora]        │  │
│  │    categoria: Falar             [PT ⏳ paciente]  [Aprovar]│ │
│  └────────────────────────────────────────────────────────┘  │
│                    [ Encerrar sessão de treinamento ]          │
└──────────────────────────────────────────────────────────────┘
```

Cada proposta mostra o status de aprovação **por idioma e por perfil** (`confirmadoCuidadora`/`confirmadoPaciente`, ver ADR-016/ADR-018) — só quando ambos ficam `true` no mesmo idioma esse locale do nó é indexado no Elasticsearch e sintetizado no ElevenLabs (ADR-015/017). A cuidadora pode aprovar seu próprio lado aqui; a confirmação do paciente acontece de forma assíncrona na Tela 09.

## 5.3 Componentes

- ChatConversationPanel *(novo)* — histórico da conversa com a LLM (API Claude/Anthropic, ver ADR-016), input de texto/voz da cuidadora
- LocaleTabs *(novo)* — alterna o idioma ativo (PT/EN) da sessão; propostas e status de aprovação são independentes por locale (ADR-018)
- ProposedNodeCard *(novo)* — mostra a proposta (categoria/frase), com indicador dual de confirmação (`cuidadora`/`paciente`) por idioma, botão "Aprovar" (lado da cuidadora) e "Editar"
- EndSessionButton — encerra a sessão de treinamento (não descarta propostas pendentes — ficam em rascunho, ver ADR-016)

## 5.4 Dados — bindings

- lê: `NoArvoreFala[]` (filtrado por `status = rascunho`, com `conteudo.pt`/`conteudo.en`), histórico de conversa da sessão de TREINAMENTO ativa
- escreve: `NoArvoreFala` (criar proposta vinda da LLM; atualizar `conteudo.<locale>.confirmadoCuidadora` ao aprovar o lado da cuidadora)
- mock: TODO — sem mock ainda definido; esta tela não existe no protótipo Angular atual

## 5.5 Estado dos elementos diferenciadores

Painel de IA explícito (`ChatConversationPanel`) — é a única tela do produto em que uma LLM gera conteúdo; reforçar visualmente que isso é diferente do runtime do CONV (Tela 06), que nunca gera texto novo (TECH-007). Sugestão de rótulo: badge "Treinamento — não é o app do paciente" na topbar desta tela.

**Estados obrigatórios:** vazio (nenhuma sessão iniciada — CTA "Iniciar treinamento"), carregamento (LLM respondendo), erro (falha ao chamar a API Claude/Anthropic), sessão encerrada com propostas pendentes (aviso de que seguem em rascunho até confirmação do paciente).

---

# TELA 09 — Confirmação do Paciente (Treinamento) *(Emenda 4, 2026-08-31 — draft BA, não implementada no protótipo)*

## 5.1 Objetivo

Permitir que o paciente veja as propostas já aprovadas pela cuidadora (Tela 08) e confirme ou ajuste, no seu próprio ritmo — sem exigir handoff de tela síncrono com a cuidadora (ADR-016 §"handoff de UI"). Vive no shell restrito do paciente (`patient-shell.component.ts`, mesmo shell da Tela 06 — ver §6 abaixo).

## 5.2 Estrutura visual

```text
┌──────────────────────────────────────────────────────────────┐
│  🔵 Sessão do paciente               🟢 Dispositivo online    │
│──────────────────────────────────────────────────────────────│
│  Novas frases para confirmar (PT)                             │
│                                                                │
│   💧 "Quero água com gelo"                                    │
│      [ ✅ Confirmar ]   [ ✏️ Ajustar ]   [ ✕ Recusar ]         │
│                                                                │
│                    [ ‹ Voltar para conversa ]                  │
└──────────────────────────────────────────────────────────────┘
```

Só aparecem aqui as propostas com `conteudo.<locale>.confirmadoCuidadora = true`. Ajustar/Recusar não edita o texto livremente (paciente pode ter motricidade limitada) — usa o mesmo canal de entrada agnóstico da Tela 06 (gesto ou toque) para selecionar entre opções curtas apresentadas pela LLM.

## 5.3 Componentes

- PendingConfirmationList *(novo)* — lista nós com `confirmadoCuidadora=true` e `confirmadoPaciente=false` no idioma ativo do paciente
- ConfirmButton *(novo)* — grava `conteudo.<locale>.confirmadoPaciente = true`; se o outro lado (cuidadora) já estava `true`, dispara indexação (ES) + síntese (ElevenLabs) para esse locale
- AdjustButton *(novo)* — abre variações curtas sugeridas pela LLM para o paciente escolher (não edição livre de texto)
- RejectButton *(novo)* — descarta a proposta para este locale (nó permanece em rascunho nos demais locales, se houver)

## 5.4 Dados — bindings

- lê: `NoArvoreFala[]` (filtrado por `pacienteId`, `conteudo.<locale>.confirmadoCuidadora = true`, `conteudo.<locale>.confirmadoPaciente = false`)
- escreve: `NoArvoreFala.conteudo.<locale>.confirmadoPaciente`
- mock: TODO — sem mock ainda definido; esta tela não existe no protótipo Angular atual

## 5.5 Estado dos elementos diferenciadores

Sem painel de LLM ativo aqui — a conversa já aconteceu na Tela 08; esta tela é só confirmação, reforçando que o paciente nunca precisa "conversar com a IA" diretamente, só decidir sim/não/ajustar sobre o que já foi proposto.

**Estados obrigatórios:** vazio (nenhuma proposta pendente — tela não aparece/CTA discreto), carregamento, erro, confirmação registrada (feedback visual de sucesso antes de voltar à Tela 06).

---

## 6. Adendo (2026-08-30) — Decisão de shell para a sessão do paciente

> Este bloco é uma **adição**, não uma reescrita das seções acima — Telas 06/07 e a nota da
> Topbar em §3.2.B permanecem exatamente como registradas. Este adendo fecha a pergunta que
> §3.2.B deixou como `TODO[arquiteto]` ("mesmo shell com indicador condicional, ou shell
> separado?"), agora que ADR-011/012/013/014 estão todos `accepted`.

**Decisão (BA/UX, Danilo Amaral):** o celular do paciente usa um **shell separado e mais
restrito** (`patient-shell.component.ts` no protótipo) quando o dispositivo ativo carrega uma
`SessaoPaciente` ativa — não o mesmo shell da cuidadora (§3.1/§3.2.A) com um item condicional.

**Por quê:** o celular do paciente carrega duas identidades independentes e com autoridades bem
menores que a cuidadora — token de dispositivo (`edge.device`, para GEST) e a sessão de paciente
do ADR-012 (`conv.arvore.navegar`/`conv.no.selecionar`, nada de gestão). Um shell único com
sidebar completa (Status · Histórico · Calibração · Onboarding) e um indicador condicional
esconderia essas rotas por CSS/estado do cliente, não por ausência real de rota — superfície de
ataque desnecessária e confusão de UX para quem não tem motricidade fina para "não clicar ali".
O shell separado nunca renderiza essas rotas para a sessão do paciente.

**O que muda na prática (Tela 06):** a Tela 06 (Árvore de Falas) passa a viver dentro desse shell
restrito, não do shell da cuidadora (§3.1). O indicador de "modo paciente" descrito em §3.2.B
continua existindo como conceito, mas deixa de ser uma variação da Topbar da cuidadora — vive na
topbar mínima do shell restrito (paciente ativo + status de conectividade do dispositivo, sem
sidebar).

**Fora de escopo deste adendo:** RBAC real que impõe essa separação no backend (isso é
implementação, não decisão de UX — ver ADR-012 TODO[arquiteto]). Este adendo decide só a forma do
shell no frontend.

---

## 7. Adendo (2026-08-31) — TREINAMENTO, busca semântica assistida e bilinguismo

> Este bloco é uma **adição** — Telas 01-07 e os adendos anteriores permanecem exatamente como
> registrados. Fecha a colocação de shell das Telas 08/09 (novas) e documenta o impacto visual
> da Emenda 4 (Elasticsearch/retrieval puro, TREINAMENTO, ElevenLabs, bilinguismo) sobre a Tela 06
> já existente, agora que ADR-015/016/017/018 estão todos `accepted`.

**Shell da Tela 08 (Treinamento):** vive no shell da cuidadora (§3.1/§3.2.A, DASH) — mesma lógica
do adendo §6: quem conduz a autoria tem a sidebar completa (Status · Histórico · Calibração ·
Onboarding · **Treinamento**, novo item de menu). Não é uma tela do paciente.

**Shell da Tela 09 (Confirmação do paciente):** vive no shell restrito do paciente
(`patient-shell.component.ts`, o mesmo da Tela 06, ver §6) — a sessão de paciente do ADR-012 ganha
uma rota nova (`conv.treinamento.confirmar`, nome provisório) além de `conv.arvore.navegar`/
`conv.no.selecionar`. Ainda sem sidebar, ainda sem autoridade de gestão.

**O que muda na Tela 06 (Árvore de Falas) — sem reescrever a seção original:** o `SpeechTreeNavigator`
passa a poder ser alimentado por busca semântica (Elasticsearch/kNN, retrieval puro, ver ADR-015)
além da navegação manual categoria→frase já descrita em §5.2 da Tela 06 — a navegação manual
continua existindo como comportamento e como fallback (constraint OPS-003) se o Elasticsearch cair.
O `TTSPlayer` da Tela 06 deixa de ter "escopo a confirmar": toca o áudio **cacheado** (ElevenLabs,
síntese só em TREINAMENTO — ADR-017), com fallback para TTS nativo do navegador (Web Speech API)
se o áudio não estiver disponível (constraint OPS-004).

**Bilinguismo (ADR-018) em Telas 06/08/09:** as três telas ganham um seletor de idioma (PT/EN) —
na Tela 06 (paciente), o idioma determina qual `conteudo.<locale>` é buscado/reproduzido; na Tela 08
(cuidadora) e Tela 09 (paciente), determina qual proposta está sendo revisada/confirmada. Um nó
pode estar "pronto" em um idioma e "pendente" no outro — a Tela 06 só mostra/busca nós com o
locale ativo já indexado (`confirmadoCuidadora=true AND confirmadoPaciente=true` naquele idioma).
TODO[arquiteto]: desenho visual exato do seletor de idioma e do que a Tela 06 mostra quando um nó
existe só no outro idioma (ver ADR-018 TODO — decisão de UX ainda não fechada).

**Fora de escopo deste adendo:** schema real de `NoArvoreFala.conteudo` por locale, pipeline de
indexação/síntese assíncrona, integração com API Claude/Anthropic — tudo isso é implementação
(ver ADR-015/016/017/018, seção TODO[arquiteto] de cada um). Este adendo decide só forma de shell
e impacto visual nas telas existentes e novas.

---
