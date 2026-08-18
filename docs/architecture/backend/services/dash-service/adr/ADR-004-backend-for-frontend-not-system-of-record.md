<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-004 — dash-service is a Backend-for-Frontend, not a System of Record

## Status
aceita

## Contexto
O painel do cuidador precisa de dados que vivem em cinco serviços — histórico (HIST), catálogo e calibração
de comandos (PAC), eventos/alarmes e feedback (EVNT), liveness do dispositivo (EDGE) e identidade/vínculo
(ACS). Há duas formas de montar essas telas: (1) o navegador chama os cinco serviços diretamente, falando
cinco contratos e cinco modelos de auth e vazando o formato do backend para a UI; ou (2) `dash-service`
mantém suas próprias tabelas — um *read-model* materializado e/ou um *inbox* de notificações — e serve o
painel a partir delas. A opção (1) é frágil e acopla a UI a cada mudança de backend. A opção (2) é pior para
um domínio de segurança: cria uma **segunda cópia divergente** de estado crítico (calibração, verdito de
evento, estado de entrega de alarme) que outro serviço já possui e já reconcilia/retenta, abrindo espaço para
o painel mostrar algo diferente da verdade. O sistema não tem AI (`ai.hasAi = false`) nem broker
(`stack.messaging.enabled = false`), então a composição precisa ser determinística e síncrona.

## Decisão
**`dash-service` é um Backend-for-Frontend (BFF) e não é system of record de nada.** Ele expõe **um** limite
autenticado e escopado por cuidador+tenant que: (a) **compõe** os *view models* de cada tela por fan-out
síncrono a HIST/PAC/EVNT/EDGE, lendo a verdade **ao vivo** dos donos; (b) **entrega** ao navegador as
notificações despachadas por EVNT (ADR-005 local); e (c) **encaminha** (write-through) as ações do cuidador
aos serviços donos (ADR-006 local). DASH **não persiste nenhum agregado operacional** no MVP — a única
"estado" que mantém é a **sessão SSE ao vivo (em memória, efêmera)** e, opcionalmente, preferências de UI não
sensíveis (adiado). Todo fato de negócio é lido de, ou escrito através de, seu serviço dono. Decomposições
internas (composição vs gateway de notificação) são explicitamente rejeitadas no MVP: compartilham o mesmo
escopo, auth e sessão.

## Consequências
**Positivas:** limite de propriedade limpo (a verdade fica com HIST/PAC/EVNT/EDGE/ACS; DASH é lente e relé);
sem cópia divergente de estado crítico de segurança; a UI tem uma única porta e um único modelo de auth; o
backend evolui atrás de um painel estável; desligar o DASH não perde dado de negócio (ele não possui nenhum);
persistência quase nula simplifica LGPD (DASH não guarda dado pessoal durável no MVP).
**Negativas / trade-offs:** a latência percebida de uma tela é a do upstream mais lento, e a queda de um
upstream degrada uma seção (mitigado por fan-out paralelo + timeouts + marcadores "seção indisponível");
compor ao vivo a cada request é mais chatty que servir de um read-model (um cache/read-model de vida curta
fica **adiado**, disparado por latência medida — doc 06); resolver escopo do cuidador no ACS por request pode
ser custoso (tradeoff de TTL curto em aberto, doc 06); DASH fica fortemente acoplado aos contratos de leitura
dos upstreams, que precisam ser panel-composable.
