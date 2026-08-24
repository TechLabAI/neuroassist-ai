# Fluxo operacional e perfis — NeuroAssist AI

**Status:** decisões de §7 tomadas em 2026-08-24 e aplicadas
**Data:** 2026-08-24
**Público:** time de produto e engenharia (o documento para quem opera é o
[tutorial-operador.md](tutorial-operador.md))

Este documento responde três perguntas que hoje não têm resposta escrita: **quem cadastra o quê e
em que ordem**, **o que cada perfil pode fazer**, e **o que cada perfil deve ver**. Tudo aqui está
ancorado no que o backend faz hoje — quando algo é proposta e não realidade, está marcado.

---

## 1. Os atores

São **quatro**, não três. O quarto não é um papel do sistema e é justamente aí que está o primeiro
buraco.

| Ator | O que é | Como existe hoje |
|---|---|---|
| **Administrador da plataforma** | Quem cria a empresa (clínica) e a primeira coordenadora dela | **Não é uma pessoa com tela.** É um cliente de serviço, `platform-ops`, que se autentica com `clientId`/`clientSecret` e recebe um token sem empresa |
| **Coordenadora de clínica** | Responde pela operação de uma clínica | `COORDENADOR_CLINICA` |
| **Cuidadora** | Cuida de pacientes específicos, recebe os alarmes | `CUIDADORA` |
| **Apoio ao paciente** | Acompanha, sem poder alterar nada | `PACIENTE_APOIO` |

> **Por que o administrador não tem tela.** Criar uma empresa acontece **antes** de a empresa
> existir, então não há a que escopar um login normal. O sistema resolve isso com um token
> "sem empresa", concedido apenas a clientes explicitamente autorizados (ADR/E2E-029).
> Consequência prática: **hoje só se cria uma clínica por chamada de API.** Não há tela para isso,
> e criar uma exige decidir quem, dentro da sua organização, tem essa credencial.

---

## 2. A ordem das coisas

```
   ADMINISTRADOR DA PLATAFORMA
   └─ cria a EMPRESA + a primeira COORDENADORA   (num passo só, atômico)
            │
            ▼
   COORDENADORA
   ├─ cria CUIDADORAS da clínica
   ├─ cria PACIENTES
   ├─ VINCULA cuidadora ↔ paciente        ← todo paciente precisa de ao menos uma
   ├─ pareia o DISPOSITIVO ao paciente
   └─ CALIBRA os comandos do paciente     (gesto → necessidade → urgência)
            │
            ▼
   OPERAÇÃO CONTÍNUA
   paciente faz o gesto → dispositivo detecta → alarme → painel da cuidadora
```

**Por que empresa e coordenadora nascem juntas.** Se fossem dois passos, existiria o estado
"empresa sem ninguém dentro" — e ninguém teria como entrar nela para criar a primeira pessoa. O
sistema fecha isso criando os dois atomicamente: ou nascem os dois, ou não nasce nenhum.

**Por que o vínculo vem antes da calibração.** Um paciente sem cuidadora é um paciente cujo alarme
não tem para onde ir. O sistema proíbe deixar um paciente sem a última cuidadora (ADR-010).

---

## 3. Matriz de perfis — o que cada um PODE fazer

Extraída do que o backend concede hoje, não do que se imagina.

| Capacidade | Admin plataforma | Coordenadora | Cuidadora | Apoio |
|---|:---:|:---:|:---:|:---:|
| Criar empresa | ✅ | — | — | — |
| Criar a 1ª coordenadora | ✅ | — | — | — |
| Criar cuidadora | — | ✅ | — | — |
| Desativar cuidadora | — | ✅ | — | — |
| **Vincular** cuidadora ↔ paciente | — | ✅ | — | — |
| Editar paciente **sem** mexer em vínculo | — | ✅ | ✅ | — |
| Criar paciente | — | ✅ | — | — |
| **Apagar dados do paciente (LGPD)** | — | ✅ | — | — |
| Criar/editar comando | — | ✅ | ✅ | — |
| Publicar / aposentar / recalibrar comando | — | ✅ | ✅ | — |
| Criar/editar tipo de gesto | — | ✅ | — | — |
| **Publicar o perfil de detecção da clínica** | — | ✅ | — | — |
| Gerenciar dispositivos | — | ✅ | — | — |
| Ver dispositivos | — | ✅ | ✅ | — |
| Ver e confirmar alarmes | — | ✅ | ✅ | ✅ |
| Ver o painel | — | ✅ | ✅ | ✅ |
| Ver histórico do próprio escopo | — | ✅ | ✅ | ✅ |
| Ver histórico de **toda** a clínica | — | ✅ | — | — |

Notas sobre a matriz:

1. **A cuidadora deixou de poder criar paciente** (decisão de 2026-08-24). Antes podia — e criava um
   paciente que nascia descoberto e que ela própria não conseguia assumir, porque nunca teve
   permissão de vincular. Cadastrar paciente é da coordenadora.
2. **Editar paciente e vincular cuidadoras foram separados.** Os dois chegavam pelo mesmo `PUT`, o
   que fazia "editar" conceder "vincular" de carona: bastava mandar outra lista no corpo. Agora
   mudar a lista exige `pac.paciente.vincular`, que só a coordenadora tem. A cuidadora segue
   editando o paciente — e um `PUT` que reenvia a mesma lista não conta como vínculo, que é o caso
   comum de quem edita o nome e devolve o objeto inteiro.
3. **Apagar dados de paciente (LGPD) é só da coordenadora**, deliberadamente — é irreversível.
4. **Apoio ao paciente** vê principalmente o **histórico**, e é o **último degrau do escalonamento
   de alarme**: quando nenhuma cuidadora e nem a coordenadora atendem, o alarme chega a ele
   (ADR-011). **Pode confirmar alarme** — sem isso o último degrau seria um beco sem saída, já que
   confirmar é o único caminho de saída de um alarme de ajuda, que nunca expira. Continua sem
   qualquer autoridade de escrita: poder dizer "eu vi e vou atender" não é poder configurar nada.

---

## 4. O que cada perfil deve VER — proposta

Hoje o menu é o mesmo para todo mundo e espelha os módulos técnicos (`gest`, `eventos`, `pac`,
`edge`, `acs`, `historico`). Isso expõe a arquitetura ao usuário: ninguém que cuida de um paciente
pensa "vou ao módulo GEST".

Proposta: o menu passa a espelhar **o trabalho**, e é filtrado por perfil.

### Cuidadora — a tela do dia a dia

| Item | O que é | Vem de |
|---|---|---|
| **Meus pacientes** (home) | Lista com estado do dispositivo e se há alarme ativo | painel |
| **Alarmes** | O que chegou, o que falta confirmar | alarmes |
| **Comandos do paciente** | Ver e recalibrar gesto → necessidade | catálogo |
| **Histórico** | O que aconteceu com os pacientes dela | histórico |

Não vê: cuidadoras, dispositivos da clínica, tipos de gesto, perfil de detecção.

### Coordenadora — tudo acima, mais a clínica

| Item adicional | O que é |
|---|---|
| **Equipe** | Cuidadoras da clínica; criar, desativar, **vincular a pacientes** |
| **Pacientes da clínica** | Todos, inclusive **os que estão sem cuidadora** |
| **Dispositivos** | Parear, ver saúde, drift de perfil |
| **Configuração de detecção** | Tipos de gesto e publicação do perfil da clínica |
| **Histórico completo** | Toda a clínica |

### Apoio ao paciente — acompanhar, e ser o último recurso

Histórico do paciente que acompanha, e o painel. Sem ação de configuração alguma.

É também o **último degrau da escada de escalonamento** (ADR-011): cuidadora original → demais
cuidadoras do paciente → coordenadora → apoio. Ou seja, este perfil não é só espectador — ele é
quem recebe o pedido quando mais ninguém recebeu.

> **Resolvido em 2026-08-24:** o apoio recebeu `evnt.event-confirmation.confirmar`. Receber o
> alarme como último recurso sem poder confirmá-lo deixaria o pedido preso na fila para sempre.

> **Alerta que precisa existir:** *pacientes sem cuidadora*. Um paciente descoberto é invisível ao
> painel — o painel lista pacientes **pela cuidadora**, e quem não tem vínculo não está na lista de
> ninguém. Sem uma tela que os mostre, eles somem. Hoje há um endpoint que os lista; falta a tela.

---

## 5. Distância entre isto e o que existe

| Gap | Situação |
|---|---|
| **Não há tela de login** | Confirmado: zero ocorrências de login em `apps/neuroassist-ai-web` e em `libs/shared/auth-session`. O backend inteiro é autenticado; o frontend não autentica ninguém |
| **Menu espelha módulos técnicos** | `gest`, `eventos`, `pac`, `edge`, `acs`, `historico` — nomes de arquitetura, não de trabalho |
| **Menu não é filtrado por perfil** | Todos veem tudo |
| **Wizard de onboarding começa por "criar empresa"** | Passo 1 do wizard cria a empresa, mas isso deixou de ser operação de coordenadora (E2E-029). O wizard precisa começar depois disso |
| **Sem tela para paciente descoberto** | O dado existe; a tela não |
| **Admin de plataforma sem interface** | Criar clínica é chamada de API com segredo de cliente. **Decidido: precisa de tela** (2026-08-24) |

---

## 6. As duas portas do vínculo — fechado em 2026-08-24

**Havia duas portas para vincular cuidadora a paciente, e só uma mantinha os dois lados em
sincronia.**

- Pela edição do paciente: o PAC grava o vínculo e propaga ao ACS.
- Direto no ACS: a coordenadora tinha `acs.vinculo.criar` e criava o vínculo lá, **sem o PAC
  saber**.

No dev isso produziu **17 pacientes** com vínculo ativo no ACS e nenhuma linha correspondente no
PAC — e **zero** no sentido inverso, o que fixou a direção do vazamento. O efeito prático: a lista
de "pacientes sem cuidadora" mostrava como descoberto quem entrou pela porta 2.

**A porta 2 foi fechada.** Nenhum papel humano tem mais `acs.vinculo.criar`/`.revogar` — as duas
sobrevivem apenas no cliente de serviço `pac-service`, que as usa para propagar. Uma entrada só, e
o PAC continua sendo quem inicia. Um teste percorre **todos** os papéis afirmando a ausência, para
que devolvê-las a alguém não passe despercebido.

**O passado foi reconciliado** (migration `V0211`): a projeção do PAC foi reparada a partir do ACS,
que é a autoridade sobre vínculos (ADR-010). Resultado no dev — divergência de 17 → 7, descobertos
de 25 → 15.

Os **7 restantes** são deliberados: pacientes **apagados por LGPD** cujo link segue `ACTIVE` no
ACS. Recriar o vínculo local de um paciente apagado seria reconstruir associação de um dado que se
pediu para remover. Mas isso expõe uma pergunta em aberto: **o apagamento LGPD não revoga o vínculo
no ACS.** Manter o registro local é decisão registrada (é o registro de que houve cuidado); o link
do ACS é *acesso*, e acesso a um paciente apagado não tem a mesma justificativa.

---

## 7. Decisões tomadas em 2026-08-24

| Pergunta | Decisão | Estado |
|---|---|---|
| Cuidadora pode criar paciente? | **Não.** Só a coordenadora cadastra e vincula | ✅ aplicado |
| Cuidadora mantém "editar paciente"? | **Sim** — mas editar deixou de conceder vínculo | ✅ aplicado |
| Criar clínica precisa de tela? | **Sim**, para o admin de plataforma | 🔲 a construir |
| O que o apoio ao paciente vê? | Histórico, principalmente; e é o **último degrau** do escalonamento | ✅ no ADR-011 |
| Qual porta do vínculo fecha? | **A do ACS.** Nenhum humano vincula direto; só o serviço PAC propaga | ✅ aplicado + reconciliado (V0211) |

## 8. Ainda em aberto

1. **O apagamento LGPD não revoga o vínculo no ACS** (§6). 7 pacientes apagados seguem com link
   `ACTIVE`.
3. **Tela do admin de plataforma** — criar clínica + primeira coordenadora.
4. **Menu por perfil e tela de login** — o que §4 propõe, ainda não construído.
