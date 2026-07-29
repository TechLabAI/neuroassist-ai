<!-- OUTPUT-SCHEMA
- Riscos
- Gaps Conhecidos
- Suposicoes a Validar
- Dependencias Criticas
-->

# 06 — Risks, Gaps and Assumptions

## Riscos
1. **Falsa confiança em gesto não detectável (safety).** Um nome cadastrado na UI pode parecer
   confiável à cuidadora sem o motor realmente reconhecê-lo. Mitigação: máquina de estados de
   detectabilidade + invariante que impede comando de emergência sobre primitivo não `supported`
   (ADR-005 local). Risco herdado de Framing §6.1/§9.3.
2. **Deriva de perfil edge↔backend.** Um dispositivo pode rodar uma versão de perfil desatualizada
   se não sincronizar. Mitigação: perfil versionado e imutável + pull no heartbeat (EDGE); o
   dispositivo sempre sabe sua versão determinística.
3. **Classificação multi-classe entre comandos parecidos.** Risco de confusão entre primitivos
   próximos cresce com o tamanho do catálogo (Framing §9.3). GEST não classifica (edge faz), mas
   define os parâmetros; limite prático de catálogo é um risco aberto a validar.
4. **Gesto composto (2 primitivos) aumenta falso-negativo.** Ambos precisam ser `supported`; a
   janela de tempo tolerada entre primitivos é parâmetro de perfil ainda não dimensionado.

## Gaps Conhecidos
- **Semântica de `engine_feature_ref`.** O handle opaco que liga um TipoGesto a uma feature real do
  motor ainda não tem formato definido (depende do pipeline MediaPipe do edge). Definir em Specs.
- **Critério de promoção `pending-training → supported`.** No MVP a validação do motor é
  out-of-band (manual, pelo condutor). Falta definir a evidência mínima para promover.
- **Parâmetros de classificação.** Faixas de `confidence threshold` e janelas min/max de gesto ainda
  não calibradas — vêm parcialmente de PAC (`Comando`) e são snapshotadas no perfil.
- **Autenticação do edge.** O credencial de dispositivo para pull de perfil depende do pareamento
  em EDGE — contrato ainda não fechado.

## Suposicoes a Validar
1. O motor de detecção do edge expõe (ou pode expor) um vocabulário de features aberto o suficiente
   para novos TipoGesto sem retrain — OU cada novo tipo exige treino, o que torna `pending-training`
   um estado real e não cosmético (Framing §6.1 TODO[arquiteto]). **A confirmar com o edge.**
2. Volume de eventos/perfis do MVP (1 tenant, 1 paciente) permite REST síncrono + PostgreSQL direto,
   sem broker nem search engine (Framing §11.2).
3. GEST não precisa persistir nenhum dado biométrico/pessoal — apenas vocabulário e parâmetros
   (mantém GEST fora da superfície sensível de LGPD).
4. O termo de tenant técnico permanece `empresa`; exibição no produto ("unidade de cuidado") é
   responsabilidade da UI (Framing §10 TODO[arquiteto]).

## Dependencias Criticas
- **Edge `GestureDetectionEngine`** — deployable separado; sem ele o perfil não é consumido e o
  contrato de detecção não é exercido (ADR-005 local).
- **EDGE (Integração de Dispositivo)** — pareamento e heartbeat que disparam o pull de perfil.
- **PAC (Catálogo de Comandos)** — consumidor da eligibilidade de primitivos; fonte parcial de
  parâmetros de classificação.
- **EVNT (Eventos e Alarmes)** — consumidor do contrato de detecção para construir o `Evento`.
- **MIG (Migrations)** — dono de todo o DDL das tabelas de GEST (Flyway centralizado).
- **GWY / REG** — ingress/auth e discovery.
