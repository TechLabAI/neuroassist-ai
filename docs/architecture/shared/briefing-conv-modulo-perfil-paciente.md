# Briefing — Módulo de Conversação (árvore de falas) + Perfil PACIENTE

> Documento de handoff: Danilo → BA → Arquiteto. Não é uma decisão de arquitetura —
> é o levantamento do estado atual + os requisitos novos que motivam o replanejamento.
> Fonte do estado atual: leitura de `docs/architecture/backend/services/{edge,pac,acs,gest}-service/`
> e do frontend (`frontend/libs/edge`, `frontend/libs/pac`) em 2026-08-29.

---

## 1. Motivação

Hoje o sistema assume que o paciente **nunca** interage diretamente com a interface —
toda ativação e configuração é feita por um cuidador, e o paciente é só um registro
de dados. Essa premissa deixou de ser universal: **nem todo paciente está em estado
de não conseguir operar a máquina.** O paciente inicial do projeto, por exemplo, opera
um dispositivo Tobii (eye-tracking) e consegue interagir diretamente com uma tela.

Isso puxa dois requisitos novos que não existiam no desenho original:

1. Um **módulo de conversação por árvore de falas** (comunicação aumentativa/alternativa)
   — não existe hoje nenhum módulo desse tipo no catálogo (GEST/EVNT/PAC/ACS/EDGE/HIST/DASH).
2. Um **perfil PACIENTE** de fato operável na interface — hoje `Paciente` (PAC) é
   puramente um registro (`nome`, `dispositivo_edge_id`), sem qualquer noção de sessão
   ou interação direta.

---

## 2. Estado atual (resumo — ver relatório completo na conversa/threads anteriores)

- **3 identidades hoje, nunca sobrepostas:**
  - **Cuidador** (`acs-service`) — único ator com login real (usuário/senha), emite JWT.
  - **Dispositivo edge** — identidade de serviço (`client_credentials`), nunca de pessoa.
  - **Paciente** (`pac-service`) — sem nenhuma credencial; é resolvido indiretamente via
    `dispositivo_edge.paciente_id` (pareamento).
- **Pareamento** é sempre um ato do cuidador: registra device → agente enrola → cuidador
  pareia ao paciente → device manda heartbeat/gestos usando seu próprio token
  (`edge-service/adr/ADR-005-device-pairing-identity-model.md`).
- **No frontend**, o pareamento só é alcançável dentro do wizard de onboarding
  (`/onboarding`), telas em `frontend/libs/edge/src/lib/feature-pairing/`. Hoje só existe
  fluxo para "máquina" genérica — nada pensado para celular do paciente.
- Hoje **não existe** nenhum conceito de "modo paciente", "kiosk" ou "sessão de paciente"
  em nenhuma camada (backend ou frontend) — confirmado por busca full-text.
- Vários adapters do fluxo de pareamento no frontend ainda são **mock** (não HTTP real);
  reportado como TODO-LIVE em evento ES separado (trace `tr_9a07ab8b780d`), não é bloqueio
  para este briefing, mas o arquiteto deve saber que a fundação de pareamento atual ainda
  não está 100% integrada de ponta a ponta.

---

## 3. Requisitos novos (levantados com Danilo em 2026-08-29)

### 3.1 Módulo de conversação — árvore de falas
- Comunicação por árvore de falas (estrutura de categorias → frases, navegável),
  provavelmente com saída em voz (TTS) — a confirmar escopo exato com o arquiteto.
- **Interação híbrida, o paciente escolhe o modo:**
  - **Por gesto** — reaproveita a detecção de gesto já existente (GEST) para navegar/selecionar
    nós da árvore, para pacientes sem controle motor fino.
  - **Por toque/mouse direto** — para pacientes com mais autonomia (ex.: usando Tobii ou
    outro dispositivo assistivo próprio), navegação direta na tela.
  - Isso implica que a árvore de falas precisa ser agnóstica ao canal de entrada: o mesmo
    modelo de "nó selecionado" deve poder ser alimentado tanto por um evento de gesto
    reconhecido quanto por um evento de toque/clique.

### 3.2 Perfil PACIENTE operável
- Paciente precisa poder **ativar o dispositivo pelo próprio celular** — não apenas a
  máquina/dispositivo edge fixo de hoje.
- **Autenticação decidida:** a cuidadora faz login *em nome do* paciente no celular dele,
  e essa sessão **fica mantida, sem expirar** (diferente da sessão normal de cuidador).
  Ou seja: não é uma credencial própria do paciente (sem senha/PIN/QR próprio) — é uma
  sessão provisionada pela cuidadora e persistida no dispositivo do paciente.
  - Implicação a levar ao arquiteto: isso não é bem um "novo tipo de login", é mais próximo
    de uma extensão do modelo de pareamento atual (dispositivo↔paciente), mas com uma
    camada de sessão de app persistente por cima — precisa decidir se isso é modelado como
    (a) um token de dispositivo de longa duração vinculado ao paciente, ou (b) uma sessão
    de cuidador "delegada" que o app do paciente carrega internamente, ou (c) algo novo.
  - Sem expiração é uma decisão consciente de UX (paciente não pode "logar de novo"
    sozinho), mas precisa de um mecanismo de **revogação** pelo cuidador (perda/roubo do
    celular, troca de aparelho) — a levantar com o arquiteto.

### 3.3 Celular como dispositivo edge completo
- O celular do paciente assume o **mesmo papel do device edge de hoje**, incluindo
  captura de webcam própria (câmera do celular) para detecção de gesto, quando aplicável.
- Ou seja: não é um "companion app" limitado à árvore de falas — é uma extensão do
  conceito de `dispositivo_edge` para rodar em celular, com todas as capacidades atuais
  (heartbeat, pull de perfil de detecção, envio de gestos reconhecidos) **mais** a nova
  capacidade de interação por toque.

### 3.4 Escopo de segurança/rede (por ora)
- Sistema vai operar **apenas em rede interna** por enquanto.
- **LGPD explicitamente fora de escopo neste momento** — não é para o arquiteto desenhar
  nada em torno disso agora. (Registrar como decisão temporária, não permanente — vale
  revisitar quando o produto sair de rede interna.)

### 3.5 Infra/deploy: onde o Docker roda, e perfil ADMIN vs. coordenadora
- O Docker precisa rodar em algum lugar fixo — **o servidor será também o "admin"**,
  o que resolve parte dos problemas de onde/quem opera a plataforma.
- Necessidade de **tela de criação de tenant**.
- Necessidade de **separar o perfil ADMIN do perfil coordenadora** — hoje só existe
  `COORDENADOR_CLINICA` como papel de topo dentro de um tenant; não há papel acima de
  tenant.

**⚠️ Conflito direto com decisão de arquitetura já registrada.**
[`docs/architecture/backend/adr/ADR-009-tenant-onboarding-authority.md`](../backend/adr/ADR-009-tenant-onboarding-authority.md)
decidiu **conscientemente não criar** um papel humano `ADMIN_PLATAFORMA`. Motivo registrado
na própria ADR (tabela "Alternativas descartadas"): criar um papel humano que enxerga todas
as empresas exigiria tornar `cuidador.tenant_id` nulável, introduzir um claim de tenant nulo,
e revisar todo filtro de tenant dos resource servers — o oposto do que a ADR estava
resolvendo (empresa nascer inalcançável). A decisão tomada foi: criação de tenant é operação
de **plataforma via cliente de serviço `platform-ops`** (credencial de máquina, sem tela).

Este briefing está pedindo exatamente o que a ADR-009 descartou. Isso não é um erro do
levantamento — é uma reabertura consciente da decisão, e precisa ser tratada como tal pelo
arquiteto, não sobreposta silenciosamente.

**Opinião registrada (Claude, a validar com o arquiteto):** recomendo *não* reabrir o modelo
de RBAC para criar `ADMIN_PLATAFORMA` agora. A própria formulação "o admin será o servidor"
já é o argumento a favor de manter a criação de tenant como operação de máquina
(`platform-ops`) — se quem tem acesso ao host Docker já é confiável por definição, não há
necessidade de um papel de aplicação + tela + sessão para proteger a plataforma dela mesma,
com o custo de espalhar uma exceção "tenant nulo" por todos os filtros de tenant hoje
NOT NULL. Alternativa sugerida: tela ops-only, restrita por localização de rede (só acessível
a partir do host/rede interna admin), chamando o mesmo `platform-ops` por baixo — resolve
"preciso de uma tela" sem tocar em RBAC/data-scope. Isso adia (não elimina) a necessidade de
um `ADMIN_PLATAFORMA` real, que só se justificaria se houver múltiplos operadores humanos
administrando tenants a partir de máquinas diferentes no futuro.

---

## 4. Perguntas em aberto para o arquiteto

1. **Novo módulo ou extensão de PAC?** A árvore de falas é módulo de negócio novo
   (precisa de código de módulo, ex. `CONV` ou `FALA`, microserviço próprio, lib Nx
   própria) ou cabe como extensão de `pac-service`/`libs/pac`? Dado o padrão do projeto
   (1 módulo = 1 unidade de trabalho do pipeline de IA), a expectativa é módulo novo,
   mas cabe confirmar.
2. **Modelo de identidade do "paciente com sessão".** Como conviver, no mesmo sistema,
   com: (a) paciente-sem-nenhuma-credencial (caso majoritário, mantém o modelo atual),
   e (b) paciente-com-sessão-persistente-no-próprio-celular (novo caso)? Isso é um
   único conceito com dois "estados" ou dois modelos paralelos?
3. **RBAC.** Que autoridades/papéis o app do paciente precisa ter para operar a árvore de
   falas e reportar gestos, sem ganhar acesso a nada de gestão/cuidador?
4. **Revogação de sessão sem expiração.** Mecanismo para o cuidador invalidar a sessão do
   celular do paciente a qualquer momento (perda, roubo, alta do paciente, etc.).
5. **GEST no celular.** A detecção de gesto hoje roda assumindo um dispositivo edge fixo
   (`edge-service`/`gest-service`) — rodar a mesma pipeline de visão computacional a partir
   da câmera de um celular (recursos, bateria, variação de hardware) é uma consideração de
   arquitetura à parte, não só de produto.
6. **Modelo de dados da árvore de falas.** Estrutura (categorias/nós/frases), customização
   por paciente/cuidador, e se comandos reconhecidos por GEST devem mapear para nós da
   árvore via o catálogo de comandos já existente em `pac-service` ou via modelo próprio
   do novo módulo.
7. **Consistência com o pareamento atual.** O pareamento por código (`awaiting-claim` →
   heartbeat) hoje é pensado para hardware fixo, operado fisicamente por quem está perto
   da máquina. Faz sentido reaproveitar o mesmo fluxo de código de pareamento para o
   celular do paciente (que a cuidadora provavelmente tem em mãos no momento da ativação),
   ou o app do paciente precisa de um fluxo de provisionamento próprio?
8. **Reabrir ou não a ADR-009.** Manter criação de tenant como operação de máquina
   (`platform-ops`, sem tela) e resolver "preciso de uma tela" com uma página ops-only
   restrita por rede — ou aceitar o custo de introduzir `ADMIN_PLATAFORMA` de verdade
   (schema + claim + filtros) porque o produto vai precisar de múltiplos operadores humanos
   em breve? Ver §3.5 para a opinião registrada e o trade-off.
9. **Topologia de deploy.** Onde exatamente o Docker do servidor vai rodar (máquina física
   dedicada, VM, mesma rede das estações de paciente?) — impacta diretamente a pergunta 8
   (se "admin = servidor" é uma máquina única e fixa, reforça a opção de não criar papel
   humano de plataforma agora).

---

## 5. Fora de escopo (explícito)

- LGPD / conformidade de dados sensíveis — não considerar agora.
- Operação fora de rede interna (internet pública, múltiplos tenants remotos) — não
  considerar agora.
- Correção dos TODOs/stubs de integração do pareamento atual (reportados separadamente
  via evento ES, trace `tr_9a07ab8b780d`) — não é parte deste briefing, mas o arquiteto
  deve estar ciente de que a base ainda não está 100% real (adapters mock no FE).
