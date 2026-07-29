<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-001 — Hexagonal Architecture

## Status
aceita

## Contexto
NeuroAssist AI é uma plataforma healthtech, multi-tenant e sujeita à LGPD, com múltiplos serviços
(negócio + infraestrutura) sobre a stack canônica Java 21 / Spring Boot. Precisamos de uma
organização interna consistente que isole regra de domínio de detalhes de I/O (banco, HTTP,
integrações edge), permita testar o núcleo sem infraestrutura e mantenha o mesmo padrão em todos
os serviços do monorepo.

## Decisão
Vamos adotar **arquitetura hexagonal (ports & adapters)** em todos os serviços do backend. Cada
serviço se organiza em `api/` (adapters de entrada), `application/` (casos de uso/orquestração),
`domain/` (modelo + ports) e `infrastructure/` (adapters de saída). O domínio não depende de
frameworks; toda I/O é confinada a adapters. Serviços de infraestrutura (GWY, REG, MIG) seguem o
mesmo esqueleto, ainda que com domínio mínimo.

## Consequências
**Positivas:** núcleo testável sem banco/HTTP; troca de adapter sem tocar o domínio; padrão único
em todo o monorepo; fronteiras claras para revisão de segurança/LGPD.
**Negativas / trade-offs:** mais camadas e boilerplate (mapeamento entre modelos de domínio e de
persistência); curva para novos contribuidores; excesso de indireção se aplicado a serviços
triviais — aceito em nome da consistência.
