<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-006 — Write-Through to Owner Services (No Local Authoring)

## Status
aceita

## Contexto
O painel origina três tipos de escrita do cuidador: editar a calibração de um comando ("afrouxar o gesto que
dispara demais"), confirmar um evento como `real` ou `falso_positivo`, e reconhecer (acknowledge) uma
notificação. Cada uma dessas verdades **já tem um dono**: a calibração é autorada em PAC (PAC ADR-004 — PAC é
system of record da calibração por comando), o verdito de confirmação pertence a EVNT (EVNT ADR-006 —
confirmação/falso-positivo é terminal e atribuída), e o estado de despacho da notificação pertence a EVNT
(EVNT ADR-005). A tentação é DASH "ajudar": aplicar a edição localmente para a UI responder rápido, ou guardar
uma cópia do último valor. Isso, num domínio de segurança, produz **divergência** — o painel mostra um valor
de sensibilidade que PAC (e portanto o `DetectionProfile` que GEST distribui ao edge) não tem, ou um verdito
que EVNT não registrou. Consistente com o ADR-004 local (DASH não é system of record), a escrita não pode
criar uma segunda fonte de verdade.

## Decisão
**Toda ação de escrita do cuidador é write-through ao serviço dono; DASH não autora nem cacheia o valor
escrito.** DASH **valida** o formato, **autoriza** por tenant + vínculo cuidador↔paciente (ACS) e **encaminha**
a operação: calibração → PAC, confirmação `real`/`falso_positivo` → EVNT, acknowledgement → EVNT. DASH devolve
ao painel **o resultado retornado pelo dono** (não o input otimista) e **não guarda cópia** do valor. O painel
não exibe um valor como aplicado até que o dono o confirme (sem optimistic write que divirja da verdade). A
única "escrita" que DASH origina de fato são os **sinais de entrega/aceite** que reporta a EVNT (ADR-005
local), e mesmo esses são fatos de entrega, não estado de negócio.

## Consequências
**Positivas:** zero divergência entre o painel e a fonte de verdade (a UI reflete o que o dono aceitou);
limite de propriedade preservado (PAC autora calibração, EVNT autora verdito/entrega); auditabilidade limpa —
cada escrita aparece uma vez, no dono, e é registrada por HIST na origem certa; DASH permanece stateless
(reforça ADR-004 local), então um restart não perde nem corrompe escrita alguma.
**Negativas / trade-offs:** sem optimistic update local, a latência da escrita é a do serviço dono (o painel
espera o round-trip para confirmar — mitigável com feedback de UI "salvando…", mas não com uma cópia
divergente); DASH precisa mapear e propagar fielmente os erros do dono (validação de PAC, conflito de estado
de EVNT) para o painel, sem mascarar; o contrato de write-through (formatos aceitos por PAC/EVNT) acopla DASH
às Specs desses serviços e precisa evoluir junto.
