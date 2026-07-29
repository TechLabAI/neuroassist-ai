<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-006 — Heartbeat Liveness and Profile Delivery

## Status
aceita

## Contexto
O valor central de EDGE para a segurança é responder "este paciente está sendo observado agora?". Um
dispositivo que caiu em silêncio é a pior falha — a família confia que Márcio pode pedir ajuda quando
não pode. Restrições concretas: o projeto **não tem broker** (`stack.messaging.enabled = false`), então
EDGE não pode publicar um evento "dispositivo offline"; a nota de stack prevê sincronização edge→backend
por REST/HTTPS síncrono com outbox de retry local. Além disso, GEST autora o perfil de detecção
versionado e o GEST ADR-005 já previu "pull no heartbeat via EDGE". Precisamos decidir **como a liveness
é determinada** e **como o perfil chega ao dispositivo** sem um barramento assíncrono e sem depender de
um job que possa falhar deixando um dispositivo morto marcado como vivo.

## Decisão
Vamos usar **heartbeat pull-based com liveness derivada em tempo de leitura e entrega de perfil
piggyback no heartbeat**:
1. **Liveness é derivada, não persistida.** `status` (`online`/`offline`) é uma função pura de
   `now − ultima_sincronizacao` contra um **threshold de staleness** (parâmetro operacional, ex.: 3× o
   intervalo de heartbeat), avaliada quando alguém lê. Não há flag persistido nem job para "virar"
   offline — logo não há job para falhar (elimina o risco de falso "online"). `ultima_sincronizacao` é
   carimbada com o **relógio do servidor** no aceite do heartbeat (mitiga clock skew do edge).
2. **DASH consome liveness por polling.** Sem broker, DASH lê a liveness num endpoint EDGE em intervalos
   curtos; a janela de staleness + o intervalo de polling limitam a latência até a cuidadora ver a queda.
   Quando um broker for habilitado no futuro, `DispositivoFicouOffline` vira o primeiro evento publicado.
3. **Entrega de perfil piggyback no heartbeat.** A resposta do heartbeat carrega o **ponteiro de versão**
   do perfil de detecção atual (buscado do GEST) e o dispositivo reporta a versão que está de fato
   rodando; EDGE compara e sinaliza **drift** quando divergem. EDGE transporta, GEST autora — EDGE nunca
   muta conteúdo de perfil.

## Consequências
**Positivas:** nenhuma dependência de scheduler para liveness (menos partes móveis no MVP); liveness
determinística e consistente para todo leitor; entrega de perfil desacoplada da disponibilidade do
GEST/edge e com ponto natural de detecção de drift; comportamento coerente com `messaging.enabled =
false`.
**Negativas / trade-offs:** a detecção de queda tem **latência de polling** — para um produto de
segurança, este é o trade-off mais sensível (doc 06 R1), mitigado por janela e polling curtos e a
revisitar com broker quando o volume crescer; ler liveness exige computar tempo a cada leitura (custo
desprezível); um alerta *proativo* (sem alguém lendo) exigiria, no MVP, um sweep periódico opcional —
deliberadamente adiado para não introduzir dependência de scheduler agora.
