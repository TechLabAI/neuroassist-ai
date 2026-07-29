<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-004 — PAC as Authoring Source of Truth for Command Calibration

## Status
aceita

## Contexto
A calibração de detecção de um paciente — sensibilidade, janela de tempo mínimo/máximo do gesto e nível
de urgência — precisa de um único dono de autoria. Duas superfícies disputam esse papel: **GEST**, que
executa a detecção e publica `DetectionProfile` imutáveis (GEST domain framing §Aggregates), e **PAC**,
onde a família de fato define o que cada gesto significa e quão sensível ele deve ser. A GEST framing já
registra que `ClassificationParams` são *"sourced from PAC `Comando` but snapshotted into the profile"*,
mas o limite de propriedade não estava formalizado. Sem essa definição, corre-se o risco de calibração
duplicada/divergente (a família ajusta em PAC, o motor usa outro valor) e de PAC reimplementar detecção.
Cada `Comando` precisa ser calibrável **independentemente** — afrouxar um gesto não pode afrouxar outro.

## Decisão
**PAC é a fonte de autoria (system of record) da calibração por comando.** O `Comando` (rótulo livre,
necessidade, primitivo(s) referenciado(s), `sensibilidade`, `tempoMinGestoMs`/`tempoMaxGestoMs`,
`urgencia`) é escrito e editado exclusivamente em PAC, com calibração **isolada por comando** (nenhum
estado de calibração compartilhado entre comandos de um mesmo paciente). **GEST lê** o catálogo ativo e
**faz snapshot** desses parâmetros em uma versão imutável de `DetectionProfile` que o edge consome —
GEST nunca autora a calibração, apenas a materializa para o motor. PAC referencia o `TipoGesto` por id;
a detectabilidade do primitivo permanece propriedade de GEST.

## Consequências
**Positivas:** limite de propriedade limpo (PAC = autoria/significado, GEST = detectabilidade/perfil);
calibração independente por comando garantida por construção (sem estado compartilhado); a família tem um
único lugar para ajustar significado e sensibilidade; o snapshot imutável de GEST desacopla edições
futuras da configuração já distribuída ao edge.
**Negativas / trade-offs:** exige um contrato explícito PAC→GEST (catálogo ativo calibrado) e um gatilho
de republicação de perfil quando a calibração muda; há latência entre editar em PAC e o edge receber o
novo perfil (a calibração corrente do edge é sempre um snapshot, não o valor ao vivo — intencional, mas
consumidores precisam entender isso); dois serviços tocam os mesmos números conceituais (PAC autora,
GEST materializa), o que precisa de disciplina de contrato para não divergir.
