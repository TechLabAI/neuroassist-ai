# Como funciona o NeuroAssist AI — guia de quem opera

**Para:** coordenadoras de clínica, cuidadoras e equipe de apoio
**Não precisa saber nada de tecnologia para ler este documento.**

**Data:** 2026-08-24 · Formato provisório (Markdown) — será decidido depois

> **Leia isto primeiro.** Este guia descreve o sistema como ele funciona hoje, no ambiente de
> desenvolvimento. Alguns passos ainda não têm tela e são feitos pela equipe técnica; onde for o
> caso, está dito com todas as letras. Nada aqui é promessa de tela futura.

---

## O que o sistema faz, em uma frase

Uma pessoa que perdeu a fala e o movimento — mas ainda controla um piscar de olho ou o levantar de
uma sobrancelha — usa esse gesto para pedir algo. Uma câmera comum vê o gesto, o sistema entende
qual pedido é, e avisa quem cuida dela.

**É só isso, e é tudo.** Todo o resto do sistema existe para que esse caminho funcione e para que
ninguém fique sem resposta.

---

## As pessoas

| Quem | O que faz |
|---|---|
| **Coordenadora** | Responde pela clínica: cadastra a equipe, os pacientes, e configura os gestos |
| **Cuidadora** | Cuida de pacientes específicos e **recebe os pedidos de ajuda** |
| **Apoio** | Acompanha um paciente (tipicamente família). Não configura nada — mas **recebe o pedido de ajuda quando mais ninguém atendeu**, e pode confirmá-lo |

Há ainda um passo anterior a todos: **alguém cria a clínica**. Hoje isso é feito pela equipe
técnica, a pedido — não existe tela.

---

## Preparando: da clínica vazia ao primeiro pedido de ajuda

### Passo 0 — A clínica é criada *(equipe técnica)*

A clínica nasce junto com sua primeira coordenadora, no mesmo instante. Isso é de propósito: uma
clínica sem ninguém dentro seria uma porta trancada por fora — não haveria quem entrasse para
criar a primeira pessoa.

Você recebe: o nome da clínica cadastrado e o login da primeira coordenadora.

### Entrando no sistema

Cada pessoa entra com o próprio e-mail e senha. Não há cadastro público: a conta é criada por quem
está acima — a clínica e a primeira coordenadora pela equipe técnica, as cuidadoras e o apoio pela
coordenadora.

Duas coisas que valem saber:

- **O sistema mostra a cada perfil apenas o que aquele perfil pode fazer.** Se você não encontra no
  menu algo que uma colega tem, não é defeito: é o seu perfil. Fale com a coordenadora.
- **Fechar o navegador é sair.** É de propósito, porque o mesmo computador costuma ser usado por
  mais de uma pessoa: ninguém herda a sessão de quem sentou ali antes. O preço é entrar de novo ao
  reabrir.

Ao terminar, use **Sair** — não basta fechar a aba se outra pessoa vai usar o computador em
seguida.

### Passo 1 — A coordenadora cadastra a equipe

Cada cuidadora recebe o próprio acesso. **Não compartilhe login.** Não é burocracia: o sistema
registra quem confirmou cada pedido de ajuda, e esse registro é o que permite responder depois
"quem atendeu?".

### Passo 2 — A coordenadora cadastra o paciente

Nome e dados básicos.

### Passo 3 — A coordenadora vincula cuidadoras ao paciente

**Este passo não é opcional.**

Um paciente sem nenhuma cuidadora é um paciente cujo pedido de ajuda não tem para onde ir. Ele
seria detectado, registrado — e não chegaria a ninguém.

Por isso o sistema **recusa** deixar um paciente sem a última cuidadora. Se você precisa trocar
quem cuida, **adicione a nova antes de remover a antiga**. Tentar remover a única resulta numa
mensagem explicando exatamente isso.

> Se quiser mais de uma cuidadora no mesmo paciente, cadastre todas. Quando o pedido não é atendido
> pela primeira, o sistema procura as outras — e ele só consegue fazer isso se elas existirem.

### Passo 4 — O dispositivo é pareado ao paciente

O dispositivo é o computador com a câmera que fica com o paciente. Parear é dizer ao sistema
"esta câmera olha esta pessoa".

O sistema pareia o dispositivo ao **paciente que está aberto na tela**. Confira de quem é o nome
no alto antes de começar: parear é dizer "esta câmera olha esta pessoa", e um dispositivo ligado
ao paciente errado manda os pedidos de uma pessoa para os cuidadores de outra.

Um dispositivo pareado passa a dar sinal de vida periodicamente. Se ele parar, aparece como
offline — e vale tratar isso como urgente: **um dispositivo offline é um paciente sem voz.**

> Se a tela ficar em "aguardando o agente" e não passar disso, o dispositivo do lado do paciente
> ainda não recebeu o código. Confira se ele está ligado e conectado antes de pedir um código novo.

### Passo 5 — A calibração: ensinar o que cada gesto quer dizer

Aqui é onde o sistema vira o sistema daquela pessoa específica.

Para cada pedido que o paciente precisa fazer, define-se:

1. **Qual gesto** — piscar duplo, piscar só o olho esquerdo, levantar as duas sobrancelhas…
2. **O que ele quer dizer** — "preciso de ajuda", "estou com sede", "quero mudar de posição"
3. **Quão urgente é** — emergência ou rotina
4. **Quão sensível deve ser a detecção** — baixa, média ou alta

**Sobre a sensibilidade, que é a parte mais importante e a menos óbvia:**

- **Alta** — o sistema dispara mais fácil. Menos chance de um pedido passar despercebido, mais
  chance de alarme falso.
- **Baixa** — o sistema é mais exigente. Menos alarme falso, mais chance de um pedido não ser
  captado.
- **Média** — o meio-termo, e o padrão.

Não existe resposta certa universal: depende de quanto controle **aquela pessoa** ainda tem sobre
aquele movimento. Alguém com espasmos involuntários frequentes pode precisar de sensibilidade
baixa para o sistema não confundir espasmo com pedido. Alguém com movimento muito fraco precisa de
alta para ser ouvido.

> **Calibre testando, não adivinhando.** A tela de calibração mostra o gesto sendo reconhecido em
> tempo real. Peça ao paciente para fazer o gesto algumas vezes e veja se o sistema pega. Ajuste e
> teste de novo.

> **A sensibilidade é por paciente e por gesto.** Mudar a sensibilidade de um paciente não afeta
> nenhum outro, e a mudança chega ao dispositivo dele sozinha — não é preciso reiniciar nada.

### Quando alguma coisa dá errado

O sistema fala de três jeitos, e a diferença entre eles é proposital:

| O que aparece | O que significa |
|---|---|
| **Um aviso no canto**, que some sozinho | Alguma coisa falhou agora. Quando dá para tentar de novo, ele traz o botão — e nesse caso **não** some sozinho, para você não perder o botão de vista |
| **Uma faixa no alto da tela**, que fica | Uma condição que continua valendo, não um tropeço. Ela permanece enquanto a condição existir |
| **Uma caixa no meio da tela** | Precisa de uma decisão sua antes de seguir |

A caixa que aparece ao tentar publicar um comando de **emergência** com um gesto que a câmera não
consegue reconhecer é a mais importante: ela **não fecha por engano** — nem apertando Esc, nem
clicando fora. O comando não foi publicado, e o botão dela leva à saída ("Alterar para Rotina").
Isso é de propósito: um pedido de socorro apoiado num gesto que o sistema não enxerga seria um
pedido que nunca chega.

---

## No dia a dia: o que acontece quando o paciente pede

```
   paciente faz o gesto
        │
        ▼
   a câmera reconhece
        │
        ▼
   o sistema traduz: "este gesto, desta pessoa, quer dizer ISTO"
        │
        ▼
   o alarme aparece no painel da cuidadora
        │
        ├──► ela confirma  → registrado quem atendeu e quando
        │
        └──► ninguém confirma
                 │
                 ├─ o sistema insiste com a mesma cuidadora
                 ├─ depois procura as OUTRAS cuidadoras do paciente, uma a uma
                 ├─ depois a COORDENADORA da clínica
                 ├─ por último, o APOIO do paciente
                 └─ um pedido de AJUDA nunca é descartado — fica na fila até alguém atender
```

**A regra que mais importa neste sistema:** um pedido de ajuda não expira. Pedidos menos críticos
podem perder o sentido com o tempo — um pedido de água de duas horas atrás provavelmente já não
significa nada. Um pedido de ajuda não. Se ninguém atendeu, o motivo pelo qual ele foi feito
continua lá.

### O que a cuidadora vê

- **Seus pacientes**, com o estado do dispositivo de cada um
- **Alarmes**, com o que ainda não foi confirmado
- **Histórico** dos pacientes dela

### Confirmar não é fechar uma janela

Confirmar significa "eu vi e vou atender". Fica registrado com nome e hora. É esse registro que
responde, depois, perguntas como "esse pedido foi atendido?" e "quanto tempo levou?".

---

## Situações que vão acontecer

**Um alarme chegou e não era nada.**
Acontece, e o remédio é a calibração: se acontece muito com o mesmo gesto, a sensibilidade dele
provavelmente está alta demais para aquele paciente. Fale com a coordenadora.

**O paciente diz que fez o gesto e nada apareceu.**
O oposto: sensibilidade baixa demais, ou o gesto escolhido deixou de ser confortável para ele. ALS
muda com o tempo — **um gesto calibrado há seis meses pode não ser mais o gesto certo.** Recalibrar
periodicamente não é manutenção opcional.

**O dispositivo está offline.**
Enquanto estiver, aquele paciente não tem como pedir nada pelo sistema. Trate como urgente.

**Preciso trocar a cuidadora de um paciente.**
Adicione a nova, depois remova a antiga. O sistema recusa a ordem inversa, e recusa de propósito.

**Um paciente foi cadastrado e ninguém foi vinculado a ele.**
Ele fica invisível: como o painel lista pacientes *pela cuidadora*, um paciente sem cuidadora não
aparece na tela de ninguém. A coordenadora precisa vinculá-lo. *(Existe uma lista desses pacientes
no sistema; a tela ainda não foi construída.)*

---

## O que ainda não existe

Honestidade sobre o estado atual, para ninguém contar com o que não está pronto:

- **Não há tela para criar clínica.** É feito pela equipe técnica.
- **Não há tela dos pacientes sem cuidadora.** A informação existe, a tela não; o item aparece no
  menu marcado como *em breve* e leva à tela mais próxima.
- **Não há uma lista de todos os pacientes da clínica.** A coordenadora vê os pacientes pelos
  vínculos, como as cuidadoras.
- **Não há aviso por celular, SMS ou e-mail.** O alarme só aparece no painel — e só chega a quem
  estiver com o painel aberto.

---

## Perguntas frequentes

**O sistema grava vídeo do paciente?**
Não. A câmera é usada para reconhecer o gesto no instante em que ele acontece. O que fica
registrado é o pedido — não a imagem.

**E se a internet cair?**
O dispositivo continua reconhecendo os gestos, mas o alarme não chega ao painel enquanto a conexão
não voltar.

**Dá para usar o mesmo dispositivo para dois pacientes?**
Não. Cada dispositivo é pareado a um paciente. É o que garante que um pedido seja atribuído à
pessoa certa.

**Uma cuidadora vê os pacientes das colegas?**
Não. Cada uma vê apenas os pacientes a que está vinculada.

**Quem pode apagar os dados pessoais de um paciente (LGPD)?**
Só a coordenadora, e é irreversível. O registro de que houve cuidado permanece; os dados pessoais
são removidos.
