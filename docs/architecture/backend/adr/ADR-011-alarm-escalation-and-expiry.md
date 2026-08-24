# ADR-011 — O que acontece com um alarme que não chega

**Status:** Aceito
**Data:** 2026-08-24
**Contexto:** [E2E-020](../issues/E2E-020.md) (C-46)
**Decisão de:** produto/clínica — registrada aqui, não inferida pela engenharia

---

## Contexto

O retry sweep do EVNT funciona: um alarme para uma cuidadora sem painel aberto fica `NO_SESSION` e
é reentregue quando ela abre a sessão SSE. Mas ao atingir `evnt.dispatch-retry.max-attempts` — 10
tentativas, cerca de 5 minutos no default — o dispatch vai para `FAILED`:

```java
if (dispatch.attempts() >= maxAttempts) {
    dispatchRepository.save(withStatus(dispatch, DispatchStatuses.FAILED));
}
```

`FAILED` é terminal e não notifica ninguém. Se a cuidadora não abrir o painel nessa janela, **um
alarme de emergência morre em silêncio.**

É melhor do que antes — antes nunca era reenviado — mas a janela e o que acontece ao fim dela
estavam sendo decididos por um contador de retry, não por alguém. Este é um paciente com ALS
pedindo ajuda pelo único canal que lhe resta.

## Decisão

**Três partes, e as três valem juntas.**

### 1. Escalonar — uma escada de quatro degraus

Esgotada a janela da destinatária original, o alarme sobe. É uma **escada, não um leque**: cada
degrau só é acionado depois de o anterior não receber.

```
1. a cuidadora destinatária original
2. as demais cuidadoras vinculadas àquele paciente
3. a COORDENADORA da clínica
4. o PACIENTE_APOIO vinculado ao paciente        ← último grau
```

O directory está populado desde a E2E-015, então a lista de quem cuida daquele paciente é
conhecida. E a E2E-028 garante que ela nunca é vazia: um paciente sempre tem ao menos uma cuidadora
ativa.

Os degraus 3 e 4 não saem da mesma lista dos degraus 1 e 2: a coordenadora é da **clínica**, não de
um paciente; o apoio é vinculado ao paciente mas não é cuidadora dele. A ordem é normativa, e o
registro de cada tentativa precisa dizer **em que degrau** ela aconteceu — sem isso não há como
responder "por que a coordenadora foi acionada?" depois de um incidente.

> **Lacuna conhecida no degrau 4.** `PACIENTE_APOIO` tem hoje apenas `acs.directory.read`,
> `dash.panel-composition.read` e `hist:read`. **Não tem `evnt.event-confirmation.confirmar`** — ou
> seja, o último degrau da escada recebe um alarme que não consegue confirmar. É um beco sem saída
> e precisa de decisão: ou o papel ganha a permissão de confirmar, ou o degrau 4 é notificação sem
> ação e alguém precisa dizer o que se espera de quem o recebe.

### 2. Nunca desistir

`FAILED` deixa de ser o destino de um alarme que ninguém recebeu. Esgotadas as cuidadoras, o
alarme **permanece na fila**, continuando a ser tentado.

A objeção óbvia — "a fila cresce indefinidamente" — é respondida pela parte 3, e não por desistir.
Desistir é a única resposta que este sistema não pode dar: o custo de um alarme a mais na fila é
memória; o custo de um alarme descartado é um pedido de ajuda que não chegou.

### 3. Expiração por relevância clínica, e ajuda nunca expira

Nem todo alarme envelhece igual. Um pedido de água que ninguém atendeu em duas horas perdeu o
sentido — a pessoa ou foi atendida por outra via, ou a necessidade passou, ou piorou e virou outra
coisa. Um pedido de **ajuda** não perde o sentido nunca: se ninguém atendeu, o motivo pelo qual
ele foi feito continua lá.

> **Um alarme de ajuda não expira. Ponto.**
>
> Ele sai da fila quando alguém o reconhece (`ACKNOWLEDGED`), e por nenhum outro caminho.

Para os demais, a expiração é por **urgência**, não por contagem de tentativas. `comando.urgencia`
já distingue `emergencia` de `rotina` (V0201), e é esse campo — não um contador — que decide se um
alarme pode envelhecer.

## O que isso significa para `FAILED`

`FAILED` deixa de significar "desistimos". Passa a existir apenas para o alarme que **expirou por
relevância clínica** — um desfecho declarado, não um contador estourado. E mesmo esse é visível:
nenhum alarme sai da fila sem deixar registro de que saiu e por quê.

Um alarme de emergência nunca chega a `FAILED` por esgotamento.

## Por que não as alternativas

**Canal alternativo (push/SMS/e-mail).** Não recusado — adiado. É a resposta certa para "ninguém
online", que o escalonamento não cobre, mas é infra nova com custo e latência próprios. A escada é
o que dá para subir agora, com o directory que já existe.

**Só tornar o `FAILED` visível.** Seria honesto e barato, e foi recusado porque não muda o
desfecho: o alarme daquele momento continua não chegando. Visibilidade é consequência da decisão,
não a decisão.

## Consequências

- A fila passa a poder conter alarmes antigos de emergência. É intencional, e o custo é conhecido.
- Escalonar significa notificar alguém que não era a destinatária original — aceitável e desejado
  aqui, mas é decisão de privacidade tanto quanto de segurança: quem escala vê que aquele paciente
  pediu ajuda. Os degraus 1, 2 e 4 estão dentro do vínculo com o paciente que o ACS já autoriza. O
  degrau 3 é diferente: **a coordenadora passa a ver o pedido de um paciente ao qual não está
  vinculada.** É justificável — ela responde pela clínica — mas é uma ampliação de acesso decidida
  aqui, e não uma consequência técnica.
- A política de expiração precisa de um dono explícito por tipo de necessidade. Enquanto não
  houver, o default seguro é **não expirar** — o oposto do default atual, que era desistir.

## Fora de escopo

- Canal alternativo (push/SMS). Issue própria.
- O que fazer quando a escada inteira se esgota e ninguém está online — hoje o alarme fica na
  fila, que é a resposta correta desta decisão, mas não é o mesmo que alguém ter sido avisado.
