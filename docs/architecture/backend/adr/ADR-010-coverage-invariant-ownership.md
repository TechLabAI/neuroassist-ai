# ADR-010 — Onde vive a invariante de cobertura (D-204)

**Status:** Aceito
**Data:** 2026-08-24
**Contexto:** [E2E-028](../issues/E2E-028.md) (C-47)
**Relacionado:** [E2E-015](../issues/E2E-015.md) — a propagação que revelou o conflito

---

## Contexto

D-204 diz que um paciente nunca fica sem cuidadora ativa. É uma invariante de segurança clínica,
não de integridade referencial: um paciente sem cuidadora é um paciente cujo alarme não tem para
onde ir.

O `acs-service` a defende, e o código diz isso com todas as letras:

```java
// SAFETY-CRITICAL (D-204): enforce coverage invariant
long activeLinksCount = linkRepository.countActiveByPacienteId(pacienteId);
if (activeLinksCount <= 1) {
    throw new LastCaregiverLinkException(pacienteId);   // 409
}
```

O `pac-service` não sabe que ela existe. E o PAC é a porta por onde uma coordenadora edita o
paciente.

Enquanto os dois lados não se falavam, o conflito era invisível. A propagação da E2E-015 o
revelou: `PUT /pacientes/{id}` com `cuidadorResponsavelIds: []` era aceito com **200** no PAC e
depois recusado com **409** no ACS, deixando `paciente_cuidador` vazio de um lado,
`caregiver_patient_link` ativo do outro, e uma entrada de outbox em `failed`.

A propagação se comportou como projetada — a recusa é terminal e o log diz `The two sides of this
link now disagree`. O defeito é a invariante existir em um lado só.

## Decisão

**O `acs-service` continua sendo o dono e a autoridade da invariante. O `pac-service` ganha um
guarda que recusa o caso inequívoco na porta.**

- PAC recusa **422** quando o update deixaria o paciente com zero cuidadoras.
- ACS mantém o **409**, inalterado, para qualquer chamador que fale com ele diretamente.

## Por que não as alternativas

**PAC consulta o ACS antes de aceitar.** Seria uma regra só, num lugar só — o argumento mais forte
contra a duplicação. Recusado porque acopla a **edição de paciente** à disponibilidade do ACS: com
o ACS fora, ninguém edita paciente nenhum, inclusive edições que não têm nada a ver com cobertura
(trocar o nome, trocar o dispositivo). Trocar uma inconsistência rara por uma indisponibilidade
frequente é mau negócio quando a inconsistência já é detectada e registrada pelo outbox.

**PAC vira o dono da cobertura.** Inverteria a direção da E2E-015. Cobertura é fato de acesso, e
quem responde "quem pode ver este paciente?" é o ACS. O `cuidadorResponsavelIds` do PAC é uma
projeção que o PAC propaga — projeção não vira fonte.

**A invariante deixa de bloquear e vira alerta.** É a alternativa com o melhor argumento de
usabilidade: hoje, para trocar a única cuidadora, é preciso adicionar antes de remover, e quem não
souber disso leva 409 sem entender por quê. Recusada por ora porque D-204 está marcada
SAFETY-CRITICAL e transformá-la em alerta é decisão clínica, não arquitetural. **Fica registrada
como a decisão a revisitar** se o atrito de uso aparecer — e a forma de revisitar é um ADR novo,
não relaxar o guarda.

## O risco assumido, e por que é aceitável aqui

Regra duplicada diverge com o tempo. É um custo real e não vale fingir que não é.

O que o torna suportável neste caso específico: **o guarda do PAC decide sobre o dado que ele está
prestes a escrever, não sobre uma projeção.** Lista de cuidadoras vazia significa zero cobertura,
exatamente, sem depender de o `paciente_cuidador` do PAC estar sincronizado com o
`caregiver_patient_link` do ACS. Não há drift possível no caso que o guarda cobre.

O guarda cobre o caso inequívoco; a autoridade continua no ACS. Se o PAC deixar passar algo por
divergência de projeção, o ACS ainda recusa e o outbox registra — que é o comportamento de hoje,
não uma regressão.

## Consequências

- Uma coordenadora que tente remover a última cuidadora recebe **422 no PAC**, com mensagem que
  explica o que fazer (adicionar outra antes de remover), em vez de um 200 mentiroso seguido de
  divergência silenciosa entre dois bancos.
- O `directory_outbox` deixa de acumular `failed` por esta causa. A entrada `failed` que já existe
  no tenant de dev é dívida a triar, não um caso novo.
- A regra passa a existir em dois lugares. Um teste compara os dois lados para que a divergência,
  se acontecer, seja vermelha em vez de silenciosa.

## Fora de escopo

- **Criar** paciente com lista vazia. O PAC aceita hoje, e é o mesmo buraco pela porta da frente —
  mas a E2E-028 é sobre remoção, e um paciente recém-criado sem cuidadora tem outra ergonomia
  (ninguém foi descoberto; ninguém tinha cobertura ainda). Merece decisão própria.
