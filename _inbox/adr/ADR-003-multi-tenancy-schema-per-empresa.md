# ADR-003 — Multi-tenancy via schema-per-empresa desde o MVP

> **Data:** 2026-07-27
> **Status:** draft (BA)
> **Decisor:** Danilo Amaral (condutor técnico)

---

## Contexto

O framework padroniza schema-per-empresa para isolamento multi-tenant. O MVP tem um único tenant ativo (família Amaral), sem cliente institucional.

---

## Opções consideradas

### Opção 1 — Schema-per-empresa (padrão do framework)

- **Vantagens:**
  - Permite expansão futura para clínicas sem retrabalho estrutural
  - Isolamento forte de dados sensíveis de saúde por tenant
- **Desvantagens:**
  - Overhead de infraestrutura para um único tenant ativo
- **Custo:** baixo (padrão já suportado pelo framework)

TODO[arquiteto]: investigar alternativas (single-schema com tenant_id) antes de fechar ADR — não avaliadas por já ser decisão de plataforma.

---

## Decisão

Escolhida: **Schema-per-empresa, mesmo com 1 tenant ativo.**.

---

## Justificativa

Framing §10; LBC §5.3/§6.2 preveem expansão institucional futura — construir multi-tenant desde o início evita retrabalho se o piloto for validado.

---

## Como reverter se a decisão se mostrar errada

Migrar de schema-per-empresa para single-schema exigiria migração de dados e mudança na camada de acesso — custo médio, viável com poucos tenants.

---

## Sinais que indicariam que está errada

- Se o número de tenants nunca crescer além de 1 em ~1 ano, o overhead de schema-per-empresa pode ser desnecessário — reavaliar nesse caso
