<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-006 — Command Edit as Versioned/Retire (History-Preserving)

## Status
aceita

## Contexto
EVNT é o system of record do `Evento` e, por decisão sua (evnt-service ADR-004), **faz snapshot** do
rótulo e da urgência do `Comando` no momento do evento — justamente para que o evento seja imutável
contra edições posteriores do comando. HIST guarda a trilha imutável. Isso cria uma tensão em PAC: a
família precisa **editar e recalibrar** comandos livremente (é o coração do produto), mas um `Comando`
que já foi referenciado por eventos históricos não pode ter seu significado alterado retroativamente sem
corromper a interpretação desses eventos/auditoria. Uma mutação in-place ingênua (trocar o gesto ou a
necessidade de um comando já usado) reescreveria implicitamente o passado.

## Decisão
PAC trata edição de comando de forma **preservadora de histórico**:
- **Recalibração** (sensibilidade, janela de tempo, urgência) de um comando `published` é aplicada
  in-place, mas **incrementa `calibracaoVersao`** — é uma mudança de sintonia, não de identidade, e os
  snapshots de EVNT já preservam o valor vigente no momento de cada evento.
- **Mudança material de identidade** (trocar o `TipoGesto` referenciado, ou a `necessidade`/rótulo de
  forma que altere o significado) de um comando **já referenciado por eventos** é modelada como
  **aposentar (`retired`) + criar um novo `Comando`**, nunca mutação in-place que reescreva o passado.
- `retired` é terminal para ativação, mas o registro é **mantido indefinidamente** (sem hard delete de
  comando referenciado) para que referências históricas de `Evento`/HIST permaneçam interpretáveis.

## Consequências
**Positivas:** eventos e auditoria históricos permanecem íntegros e interpretáveis; a família ainda edita
livremente (recalibração é in-place e barata); a distinção sintonia-vs-identidade dá uma regra clara para
quando versionar vs. aposentar; alinha PAC ao contrato de snapshot de EVNT sem exigir coordenação em
runtime.
**Negativas / trade-offs:** o catálogo acumula comandos `retired` (mais linhas, precisa de filtragem de
"catálogo ativo" nas leituras de GEST/DASH); distinguir "recalibração" de "mudança material" exige uma
regra explícita e disciplina de UX/Specs (o que conta como material?); sem hard delete, direitos de
supressão LGPD do titular são tratados no nível de tenant/ACS, não por exclusão ad-hoc em PAC.
