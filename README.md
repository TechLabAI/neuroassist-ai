# NeuroAssist AI

Plataforma de tecnologia assistiva que transforma pequenos movimentos voluntários residuais, de pessoas com doenças neurodegenerativas como ELA/MND, em pedidos de ajuda estruturados. Os movimentos são capturados por uma webcam comum e processados por visão computacional no próprio dispositivo (edge).

## Para quem é

Pacientes com mobilidade motora muito reduzida, os cuidadores que os acompanham e as equipes que mantêm o acesso e o histórico desses pedidos. O paciente aciona comandos por gestos, o cuidador recebe o alerta e o sistema registra o que aconteceu.

O repositório contém código e documentação. Não contém dados de pacientes.

## Módulos

| Código | Módulo | Serviço | Responsabilidade |
|---|---|---|---|
| GEST | Detecção de Gesto | `gest-service` | Detecta o gesto no dispositivo edge |
| EVNT | Eventos e Alarmes | `evnt-service` | Registra eventos e dispara alarmes |
| PAC | Pacientes | `pac-service` | Pacientes e catálogo de comandos |
| ACS | Cuidadores e Acesso | `acs-service` | Cuidadores, autenticação e permissões |
| EDGE | Dispositivo Edge | `edge-service` | Integração com o dispositivo edge |
| HIST | Histórico e Auditoria | `hist-service` | Histórico e trilha de auditoria |
| DASH | Dashboard | `dash-service` | Dashboard e notificações |
| CONV | Conversação | `conv-service` | Conversação por árvore de falas |
| TREI | Treinamento | `trei-service` | Autoria da árvore de falas com apoio de IA |

Além dos módulos há o API gateway (`neuroassist-ai-api-gateway`), o registro de serviços (`neuroassist-ai-service-registry`) e as migrações centralizadas (`neuroassist-ai-migrations`).

## Tecnologias

- **Backend:** Java 21, Spring Boot 3.5, Gradle (Kotlin DSL), ActiveMQ Artemis (JMS), PostgreSQL 15+. Arquitetura hexagonal, com regras verificadas por ArchUnit.
- **Frontend:** Angular em um workspace Nx.
- **Infraestrutura local:** Docker Compose.

## Estrutura do repositório

```
backend/services/   um microsserviço Spring Boot por módulo, mais gateway, registro e migrações
frontend/           workspace Nx: apps/neuroassist-ai-web e libs por módulo
docker/             compose de desenvolvimento
docs/architecture/  especificação, ADRs, contratos, planos e issues
docs/paper/         plano do artigo acadêmico e artefatos da prova de conceito
scripts/            gates de conformidade e automação do pipeline
```

## Como rodar

Pré-requisitos: Java 21, Node, Docker e Python 3.

```bash
# infraestrutura local (PostgreSQL, Artemis)
docker compose -f docker/docker-compose.dev.yml up -d

# backend (na pasta backend/)
./gradlew :services:<servico>:build

# frontend (na pasta frontend/)
npm install
npx nx serve neuroassist-ai-web
```

As migrações ficam em `backend/services/neuroassist-ai-migrations` e devem ser aplicadas antes de subir os serviços. Um `bootRun` sem perfil não aplica nada. O passo a passo está no [CLAUDE.md](CLAUDE.md).

## Testes e qualidade

```bash
# em backend/
./gradlew :services:<servico>:test                # testes unitários
./gradlew :services:<servico>:architectureCheck   # regras ArchUnit
./gradlew :services:<servico>:integrationTest     # Testcontainers, exige Docker

# em frontend/
npx nx affected:test
npx nx affected:lint
```

## Status e planos

O trabalho é organizado em issues atômicas por módulo (`GEST-001`, `EVNT-001`, `PAC-001` e assim por diante).

- Issues e planos por módulo: veja o [ROADMAP.md](ROADMAP.md)
- Decisões de arquitetura: `docs/architecture/backend/adr/` e `docs/architecture/frontend/adr/`
- Guia do projeto, convenções e regras de arquitetura: [CLAUDE.md](CLAUDE.md)

## Contribuindo

Não há push direto: toda mudança entra por Pull Request. Rode `architectureCheck` antes de commitar.

## Licença

Distribuído sob a licença MIT. Veja o arquivo [LICENSE](LICENSE).
