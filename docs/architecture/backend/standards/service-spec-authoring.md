# Service Spec Authoring (NeuroAssist AI Backend)

> Processo e convenções para criar especificações técnicas de backend de qualquer
> microserviço.

Use este arquivo quando precisar especificar um serviço novo (ex.: começando a `crm-service`)
ou refinar especificação de um existente (ex.: nova feature em `<service-name>`).

---

## Pipeline de Authoring (3 fases)

```
Fase 1 — Análise dos artefatos arquiteturais
  └── Ler ADRs + modelo de dados + protótipo + mapeamento legado

Fase 2 — Geração do plano de spec
  └── Listar todas as SPECs a produzir, dependências e ordem

Fase 3 — Geração das specs individuais
  └── Criar cada SPEC em docs/architecture/backend/services/<service>/
```

NeuroAssist AI simplifica o processo do padrão Hub: não exige 35 diagramas PlantUML por serviço,
mas mantém o rigor de SPECs implementáveis sem ambiguidade.

---

## Fase 1 -- Análise dos artefatos arquiteturais

### 1.1 Artefatos obrigatórios a ler

| Arquivo | O que extrair |
|---|---|
| `docs/architecture/shared/blueprint-core.md` | Modelo mental, camadas, responsabilidade do serviço |
| `docs/architecture/shared/multi-empresa.md` | ADR-001 schema-per-empresa (sempre relevante) |
| `docs/architecture/shared/modelo-dados/README.md` | Índice de DDL; foco nas tabelas do módulo |
| `sql/01_schema_novo.sql` | DDL canônico Postgres do módulo |
| `docs/architecture/backend/standards/services-catalog.md` | Pacote canônico + responsabilidades do serviço |
| `MAPEAMENTO_DADOS_LEGADO.md` (raiz) | De-para legado → novo (tabelas do módulo) |
| `MODELO_DADOS.md` (raiz) | Narrativa de modelo conceitual |
| `prototipo-canonico` (memória) + `prototipo-padroes-telas` | Padrões de UI que o BE alimenta |
| `TELAS_FALTANTES.md` | Telas (e portanto endpoints) que precisam existir |

### 1.2 O que identificar na análise

- **Aggregate raiz** do domínio e seus value objects.
- **Estados** do ciclo de vida (máquina de estados, quando aplicável).
- **Ports/adapters externos** (SEFAZ, banco, e-mail, gateway de pagamento).
- **Serviços de domínio transversais** (idempotência, validação fiscal).
- **Multi-empresa:** quais tabelas vivem em `<empresa-schemas>` vs `neuroassist_global`.
- **Eventos produzidos e consumidos** (catálogo em `services-catalog.md`).
- **Feign clients** necessários (catálogo em `services-catalog.md`).
- **Dependências abertas** (DEP-XX) que precisam confirmação antes da implementação.

### 1.3 Sinalizar dependências abertas

Durante a análise, rastreie informações que precisam confirmação externa:

```
DEP-01 — [Descrição] — [Quem confirma] — [Impacto nas specs]
```

Aparecem como notas `> **DEP-XX:**` nas specs que as referenciam.

---

## Fase 2 -- Plano de Spec

### 2.1 Localização

```
docs/architecture/backend/services/<service>/plano-spec.md
```

### 2.2 Estrutura obrigatória

```markdown
# Plano de Spec -- <service-name>

## 1. Lista de SPECs
| ID     | Título                              | Inputs                | Outputs                  | Dependências |
|--------|-------------------------------------|-----------------------|--------------------------|--------------|
| SPEC-01| API Surface                         | --                    | endpoints REST           | SPEC-04, SPEC-08 |
| SPEC-02| Use cases (Application Services)    | SPEC-01               | application/service/*    | SPEC-03      |
| ...    | ...                                 | ...                   | ...                      | ...          |

## 2. Dependências abertas
| DEP | Descrição | Specs afetadas | Impacto |
|-----|-----------|----------------|---------|

## 3. Sequência de produção
Fundação (domain, estado, validação) → application → infrastructure → api → BDD
```

### 2.3 SPECs canônicas NeuroAssist AI (8 + 1 opcional)

Backbone padrão -- adapte conforme o domínio. Específicos como `<example-service-1>` podem ter
+1 SPEC dedicado a SEFAZ integration.

| SPEC | Título | Escopo |
|---|---|---|
| **SPEC-01** | API Surface | Endpoints HTTP, contratos request/response, códigos de status, permissões (`<modulo>.<recurso>.<acao>`) |
| **SPEC-02** | Domain Model | Aggregate raiz, value objects, enums, regras invariantes |
| **SPEC-03** | Domain Services | Regras transversais (cross-entity), `DomainServiceConfig` |
| **SPEC-04** | Application Services | Use cases, sequências, transações, EmpresaContextHolder.require() |
| **SPEC-05** | Persistence | JPA entities, adapters, queries customizadas, índices |
| **SPEC-06** | Messaging | Eventos produzidos (envelope, queue, payload) + consumed (idempotência) |
| **SPEC-07** | External Integrations | Feign clients + Resilience4j; integração SEFAZ/bancos/etc. quando aplicável |
| **SPEC-08** | BDD Test Scenarios | Cenários Gherkin: happy path, falhas, idempotência, multi-empresa |

**SPEC opcional:**
- **SPEC-09 -- State Machine** (se o domínio tem máquina de estados complexa: NFe, orçamento, oportunidade CRM)

---

## Fase 3 -- Specs individuais

### 3.1 Localização

```
docs/architecture/backend/services/<service>/SPEC-{numero}-{slug}.md
```

### 3.2 Nomenclatura

```
SPEC-{numero}-{slug-kebab-case}.md
```

- Numeração pela **sequência de produção** (dependências primeiro).
- Slug em kebab-case, máximo 40 caracteres.

Exemplos:
```
SPEC-01-api-surface.md
SPEC-04-application-services.md
SPEC-08-bdd-scenarios.md
SPEC-09-state-machine-nfe.md
```

### 3.2.1 — Palavras reservadas em `generator-config.yml` (D-399)

O arquivo `generator-config.yml` é lido via `yaml.safe_load()` (PyYAML YAML 1.1). As
seguintes palavras são interpretadas como **booleanos** quando não estão entre aspas:

| Palavra sem aspas | Valor Python recebido |
|---|---|
| `on`, `yes`, `true` | `True` |
| `off`, `no`, `false` | `False` |

**Regra:** use sempre strings quoted para valores que não sejam booleanos semânticos:

```yaml
# CORRETO
defaultState: "enabled"

# ERRADO — PyYAML lê como True (bool Python); gerador emite "True" em vez de "enabled"
defaultState: on
```

O script `generator-config-gen.py validate` detecta `defaultState` booleano e reporta
o erro com dica de correção (D-394). Valores canônicos: `"enabled"` ou `"disabled"`.

---

### 3.3 Frontmatter obrigatório

```yaml
---
document_type: backend-spec
document_id: <service>-spec-{numero}-{slug}
title: SPEC-{numero} — {Título} — <service-name>
owner: Tech Lead / Arquitetura
status: draft
language: pt-BR
scope: <service>-mvp
last_updated: {YYYY-MM-DD}
---
```

### 3.4 Estrutura interna padrão

```markdown
# SPEC-{numero} — {Título}

## 1. Objetivo
[Uma a três frases: o que esta spec especifica e por que existe]

## 2. [Conteúdo técnico principal]
[Tabelas, contratos, regras, sequências — específicos por spec]

## 3. [Seções complementares]
...

## N-1. Multi-empresa
[Como esta spec lida com EmpresaContextHolder; quais tabelas em <empresa-schemas> vs neuroassist_global]

## N. Dependências
- **SPEC-XX** -- [o que esta spec precisa daquela]
- **DEP-XX** -- [informação externa a confirmar]
```

A seção **Dependências** é sempre a última.

### 3.5 Convenções de conteúdo por tipo

**Contratos de interface (ports, use cases):**

```
Interface: NomeDoPort

metodo(input: TipoInput, context: EmpresaContext) : TipoOutput
  → Passo 1: o que faz
  → Passo 2: o que retorna
  → Em caso de falha: exceção lançada
```

**Tabelas de mapeamento de campos:**

```markdown
| Campo input (request) | Campo intermediário (domain) | Campo persistido (JPA) | Obrigatório |
```

**Sequências de execução (use cases):**

```
1. EmpresaContextHolder.require()
2. PrimeiroPasso.acao(params)
     → Se condição: fazer X
     → Se outra condição: fazer Y
3. SegundoPasso.acao(params)
```

**Tabelas de falha/compensação:**

```markdown
| Falha no passo | Ação | Estado final |
```

**Catálogos de validação:**

```markdown
| Código | Campo | Regra | Severidade (BLOCKING|WARNING) |
```

**Cenários BDD (SPEC-08):**

```gherkin
# language: pt
Funcionalidade: [Nome do fluxo]

  Contexto:
    Dado [pré-condição: empresa ativo, usuário autenticado]
  Cenário: [Resultado esperado]
    Dado [estado inicial]
    Quando [ação executada]
    Então [assertions verificáveis]
    E [assertions adicionais]
```

---

## Regras de qualidade das SPECs

### Q1 -- Toda spec implementável sem ambiguidade

- Nomes de classes, métodos e campos = os mesmos que serão usados no código.
- Tipos explícitos (`String`, `UUID`, `Instant`, `List<X>`, `BigDecimal`, etc.).
- Enums listam todos os valores válidos.
- Regras condicionais determinísticas (sem "talvez", "dependendo").

### Q2 -- Dependências abertas explícitas

Quando uma informação não está disponível:

```markdown
> **DEP-XX:** [Descrição do que falta confirmar e de quem]
```

Nunca inventar valores -- marcar DEP-XX e continuar com placeholders explícitos.

### Q3 -- Isolamento entre specs

Cada spec especifica **uma camada ou capacidade**. Não duplicar:
- Regras de validação → SPEC-02 (Domain Model) ou SPEC-03 (Domain Services)
- Transições de estado → SPEC-09 (State Machine, opcional)
- DDL e índices → sql/01_schema_novo.sql (referenciar -- não duplicar)
- Auth/authz → SPEC-01 (permission keys) + `rbac.md` global

Quando uma spec precisa referenciar algo de outra, usar `(ver SPEC-XX)` ou `(ver rbac.md §X)`.

### Q4 -- Specs são fonte de verdade

Contratos nas specs (interfaces de ports, estrutura de DTOs, sequências de use cases) têm
precedência sobre convenções ad hoc. Código deve estar conforme as specs, não o contrário.

### Q5 -- Multi-empresa sempre explícito

Toda spec que toca dado de empresa deve declarar:
- Quais tabelas vivem em `<empresa-schemas>` vs `neuroassist_global`.
- Onde `EmpresaContextHolder.require()` é chamado.
- Como cross-empresa guard é aplicado (se há aggregate filho).

---

## Diagramas (opcional, focado)

NeuroAssist AI não exige 35 diagramas PlantUML por serviço (como Hub). Diagramas são **opcionais**
mas recomendados para serviços com complexidade alta (`<example-service-1>`, `<example-service-2>`).

Localização: `docs/architecture/backend/services/<service>/diagrams/`

Catálogo mínimo recomendado (~7 diagramas, opcional):
- 1 C4 component (overview)
- 1 aggregate + value objects
- 1-3 sequência (happy path + 1-2 failures)
- 1 state machine (se houver)
- 1 ERD (extraído de `sql/01_schema_novo.sql`)

Use PlantUML com paleta consistente (a definir em ADR-002 visual quando primeiro
serviço for documentado).

---

## Checklist de conclusão

Antes de considerar a SPEC de backend de um serviço pronta:

- [ ] Plano `plano-spec.md` gerado com lista completa de SPECs + dependências abertas
- [ ] SPEC-01 (API Surface) cobre todos os endpoints com contratos completos
- [ ] SPEC-02/03 (Domain) cobre aggregate, value objects, enums e regras invariantes
- [ ] SPEC-04 (Application) lista todos os use cases com sequências + EmpresaContextHolder.require()
- [ ] SPEC-05 (Persistence) referencia DDL em `sql/01_schema_novo.sql` (não duplica)
- [ ] SPEC-06 (Messaging) lista eventos produzidos + consumidos (alinhado com `services-catalog.md` §6)
- [ ] SPEC-07 (External) cobre Feign clients + Resilience4j (alinhado com `services-catalog.md` §7)
- [ ] SPEC-08 (BDD) inclui cenários: happy path, cada falha tipica, idempotência, isolamento de empresa
- [ ] SPEC-09 (State Machine) se aplicável
- [ ] Nenhuma informação inventada -- todas as incertezas marcadas como DEP-XX
- [ ] Permissões nomeadas no padrão `<modulo>.<recurso>.<acao>` (alinhado com `rbac.md`)
- [ ] Multi-empresa explicitado em toda SPEC que toca dado de empresa

---

## Referências

- `services-catalog.md` -- catálogo de microserviços + pacotes canônicos + eventos
- `hexagonal-patterns.md` -- estrutura de pacotes e regras
- `java-conventions.md` -- convenções de código
- `platform-patterns.md` -- JMS, Feign, Flyway, MDC, Saga, multi-schema
- `testing-conventions.md` -- pirâmide de testes
- `rbac.md` -- modelo de papéis e permissões
- `docs/architecture/shared/modelo-dados/README.md` -- índice DDL
- `sql/01_schema_novo.sql` -- DDL canônico
