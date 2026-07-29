<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-006 — TipoGesto Detectability Lifecycle

## Status
aceita

## Contexto
A Emenda 2 do BA (2026-07-27) tornou o catálogo `TipoGesto` extensível pela UI: a cuidadora/condutor
pode cadastrar um novo tipo de movimento (ex.: "Leitura labial") sem depender de release. O próprio
Framing Técnico registra o perigo em §6.1/§7.1a/§9.3 (`TODO[arquiteto]`): **cadastrar o nome de um
gesto NÃO implica que o motor de detecção saiba reconhecê-lo**. Para um produto de segurança, expor
um gesto "cadastrado" como se fosse confiável pode criar falsa expectativa fatal — a cuidadora
poderia mapear um pedido de emergência a um movimento que o edge nunca reconhece. É necessário
separar, no modelo de domínio, "nome cadastrado" de "movimento realmente detectável".

## Decisão
Todo `TipoGesto` carrega um estado de detectabilidade explícito com transições auditadas:
`registered → pending-training → supported`, mais `deprecated` (terminal para elegibilidade).
Regras:
- Um primitivo só é elegível para compor um `Comando` de `urgencia = emergencia` quando `supported`
  (invariante enforced por gest-service; para gesto composto, ambos os primitivos devem ser
  `supported`).
- Um primitivo só atinge `supported` com `engine_feature_ref` não-nulo — deve existir uma feature
  real do motor por trás do nome.
- Apenas primitivos `supported` entram no perfil de detecção publicado ao edge.
- Regressão de `supported` para estado inferior é sempre uma transição explícita e auditada
  (append-only em `tipo_gesto_transition`) — nunca silenciosa.
- Primitivos-semente já validados no motor podem ir direto a `supported`; novos passam por
  `pending-training`.

## Consequências
**Positivas:** fecha o gap de segurança do Framing §6.1/§9.3; PAC e DASH têm uma fonte única para
responder "esse gesto é detectável de fato?"; nenhum comando de emergência pode se apoiar em um
primitivo não suportado; extensibilidade da UI preservada sem sacrificar honestidade.
**Negativas / trade-offs:** adiciona uma máquina de estados e uma trilha de auditoria a manter;
o critério de promoção `pending-training → supported` é out-of-band (manual pelo condutor) no MVP e
precisa ser formalizado em Specs; a cuidadora pode cadastrar um gesto e não poder usá-lo de imediato
(atrito de UX deliberadamente aceito em nome da segurança).
