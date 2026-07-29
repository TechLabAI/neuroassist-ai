<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-006 — Caregiver-patient linkage drives data-scope

## Status
aceita

## Contexto
Duas perguntas atravessam todo o sistema: "quem deve receber o alarme deste paciente?" (roteamento, EVNT/
DASH/EDGE) e "quais pacientes este cuidador pode ver?" (data-scope, constituição entity-scope ES.001/002).
Num tenant `familia` a resposta é trivial (uma cuidadora, um paciente), mas num tenant `clinica` há
muitos cuidadores e muitos pacientes, com um `coordenador_clinica` que enxerga tudo e `cuidadora`s que só
enxergam os pacientes atribuídos a elas. Se cada serviço derivar essa relação por conta própria (por
convenção, por heurística de tenant, ou por cópia local), o roteamento e o escopo divergem — um alarme de
emergência pode ir para a pessoa errada, ou uma cuidadora pode ver um paciente que não é dela. Precisa
haver uma fonte única.

## Decisão
Vamos tornar o **vínculo cuidador–paciente do ACS (`CaregiverPatientLink`) a fonte única** tanto de
roteamento quanto de data-scope. Nenhum outro serviço reinventa esse mapa: EVNT resolve "quem notificar"
via o resolve de diretório do ACS (`CaregiverRoutingClient`), e o data-scope por paciente de uma
`cuidadora` deriva dos seus links ativos. A regra de escopo por papel é: `coordenador_clinica` →
todos os pacientes do tenant; `cuidadora` → apenas os pacientes com link `active` a ela. Um link só é
válido se cuidador e paciente pertencem ao **mesmo tenant** (invariante de isolamento). O `paciente_id`
é uma referência lógica a um agregado do PAC (não FK de banco), validada no momento do link via resolve
ao PAC. Revogar um link remove roteamento e escopo no próximo lookup, preservando a trilha em HIST.

## Consequências
**Positivas:** roteamento e autorização de dados consistentes por construção (uma fonte, não N cópias);
o modelo suporta naturalmente tanto `familia` (um link) quanto `clinica` (muitos), sem caso especial;
o escopo por paciente alimenta diretamente os filtros Hibernate de entity-scope; auditável (link é dado,
mudança vai para HIST). **Negativas / trade-offs:** coloca o ACS no caminho de resolução de roteamento de
emergência — que deve ser fail-closed (um link não resolvido é falha operacional, não alarme silenciado);
introduz consistência eventual entre a revogação de um link e tokens/caches já emitidos; exige que EVNT/
EDGE/DASH chamem o ACS em vez de inferir localmente, adicionando um hop de rede por resolução (mitigável
com cache de curta duração no consumidor).
