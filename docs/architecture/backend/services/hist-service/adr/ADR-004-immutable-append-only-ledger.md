<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-004 — Immutable append-only audit ledger (write-once)

## Status
aceita

## Contexto
HIST exists to be the trustworthy witness of *what happened* across NeuroAssist AI: events raised and
confirmed, commands recalibrated, access granted, devices paired. The operational services (EVNT, PAC, ACS,
EDGE) legitimately mutate their own state, so none can serve as that witness — a record that can be edited
is not evidence. For a healthtech pilot handling ALS/MND patients, the audit trail underpins caregiver
trust, clinical accountability and LGPD traceability of access to patient data. The forces: the trail must
be provably unaltered; corrections must still be possible (people make mistakes); and the design must stay
simple for a small team and a one-patient pilot.

## Decisão
Vamos modelar o ledger como **append-only e write-once**: um `AuditRecord`, uma vez gravado, nunca é
atualizado nem removido por nenhum caminho de código ou API. Não existe endpoint de UPDATE ou DELETE sobre
o ledger. Uma **correção** é sempre um *novo* registro que referencia o anterior (`correctsRecordId`),
preservando o original visível. A imutabilidade é defendida em três camadas: (1) o domínio não expressa
operação de mutação; (2) a aplicação só oferece o caso de uso de *append*; (3) na persistência, onde o
Postgres permitir, o papel do serviço recebe as tabelas do ledger **sem privilégio de UPDATE/DELETE**, como
backstop de defesa em profundidade (o GRANT viaja como migração via MIG).

## Consequências
**Positivas:** o histórico é evidência, não opinião — pode-se *provar* que um registro não foi reescrito;
o modelo de estado do agregado fica trivial (`recorded` terminal), eliminando classes inteiras de bugs de
concorrência de edição; correções ficam auditáveis por construção (a tentativa e a correção coexistem);
alinha-se ao papel de HIST como *sink* (doc 01/04).
**Negativas / trade-offs:** conflito direto com apagamento por sujeito da LGPD — não se pode deletar linhas
sem quebrar a cadeia (tratado apenas em granularidade de tenant no MVP; sub-tenant é gap aberto, doc 06);
crescimento monotônico do armazenamento (sem purga no MVP — aceitável no volume do piloto); correções geram
mais registros a renderizar (DASH precisa entender `correctsRecordId`). Aceito porque imutabilidade é o
próprio produto de HIST; sem ela não há trilha confiável.
