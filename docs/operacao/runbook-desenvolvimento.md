# Runbook de desenvolvimento — como testar localmente

**Para:** quem desenvolve no NeuroAssist AI.
**Data:** 2026-08-25

> Este documento registra os comandos que funcionam e, principalmente, **as armadilhas que já
> custaram horas**. Cada aviso aqui é um erro que alguém cometeu de verdade neste repositório,
> não um cuidado teórico.

---

## Antes de tudo: a regra que não se negocia

**Rode `nx build` antes de todo commit que toque o frontend.**

O transpilador do Jest apaga `import type` e não faz checagem completa de tipos. Numa única
sessão, **três vezes** a suíte inteira (1.200 testes) passou verde e o build de produção reprovou:
um `inject(ElementRef<HTMLElement>)`, um `import type` de um tipo que morava noutra biblioteca, e
um import que simplesmente não entrou no arquivo.

    testes → build → commit

Para o backend o equivalente é `architectureCheck`, que já é bloqueante.

---

## Frontend

```bash
cd frontend

npx nx run-many -t test --all        # ~1.200 unitários, sem Docker
npx nx build neuroassist-ai-web      # o gate que o Jest não cobre
```

### End-to-end

As sete suítes sobem o próprio servidor — não é preciso preparar nada:

```bash
npx nx e2e acs-e2e
npx nx run-many -t e2e -p acs-e2e pac-e2e hist-e2e edge-e2e gest-e2e dash-e2e evnt-e2e
```

Dois conjuntos só rodam quando pedidos, porque exercitam caminhos fechados ao build de produção:

```bash
EDGE_HTTP_MODE=1 npx nx e2e edge-e2e   # os 18 cenários de erro, conflito e RBAC do EDGE
```

### Contra o container — o teste mais duro

É onde aparecem as coisas que o dev server esconde. Foi assim que descobrimos que o login não
funcionaria em produção (chamava um caminho que só o proxy de desenvolvimento servia).

```bash
docker compose -f docker/docker-compose.dev.yml up -d --build web
APP_URL=http://localhost:14200 npx nx run-many -t e2e -p acs-e2e pac-e2e hist-e2e edge-e2e gest-e2e dash-e2e evnt-e2e
```

Ali alguns cenários aparecem **pulados dizendo o motivo** — os que dependem de gancho de
desenvolvimento (motor de gesto simulado, adaptador HTTP). É esperado: esses ganchos são fechados
por `isDevMode()` de propósito, porque uma variável global capaz de desligar a detecção de gesto
não pode existir onde há paciente.

---

## Backend

```bash
cd backend

./gradlew :services:<serviço>:test
./gradlew :services:<serviço>:architectureCheck    # BLOQUEANTE antes de commitar
./gradlew spotlessCheck                            # todos os módulos
./gradlew spotlessApply                            # corrige a formatação
./gradlew :services:neuroassist-ai-migrations:test  # precisa de Docker (Testcontainers)
```

Cenários marcados `@performance` ficam fora do gate de correção — medem relógio de parede, e num
runner compartilhado aferem a máquina, não o código. Para rodá-los:

```bash
./gradlew :services:acs-service:test -Dcucumber.filter.tags="@performance"
```

---

## Ambiente local

```bash
docker compose -f docker/docker-compose.dev.yml up -d
```

Portas no host: `web 14200 · gwy 18080 · acs 8088 · pac 8082 · gest 8084 · edge 8087 ·
hist 8089 · evnt 18085 · dash 18090 · eureka 18761 · postgres 5433`.

Os containers `migrations-global` e `migrations-empresa` aparecem como `Exited (0)` — é o
esperado, são tarefas e não serviços.

### Usuários de semente

Definidos em `V0102__acs_dev_seed.sql`. Todos com a senha `Test@1234!`, e o login é o **e-mail**:

| Login | Papel | Para que serve |
|---|---|---|
| `coord@neuroassist.dev` | Coordenadora | Única que cadastra paciente, gere equipe e vincula cuidadoras |
| `cuidadora@neuroassist.dev` | Cuidadora | Cuida, confirma alarme, edita comandos |
| `jamilly@neuroassist.dev` | Cuidadora | Segunda cuidadora, para cenários de cobertura |
| `apoio@neuroassist.dev` | Apoio | **Sem nenhuma authority de escrita** |
| `ana@clinica029.dev` | Coordenadora | Outra empresa — para provar isolamento entre schemas |

**Escolher o papel errado leva a conclusão errada sobre o produto.** A cuidadora tem quase todas
as permissões de comando (criar, editar, publicar, aposentar, recalibrar) e só não tem
`pac.paciente.criar`. Para testar "alguém sem permissão de escrever", o papel certo é o **apoio**.

---

## Armadilhas

### 1. O bundle velho servido em silêncio

O `nx serve` em modo watch, quando o build falha, **continua servindo o último bundle bom**.
Responde 200, a página carrega, e nada denuncia — você mede código velho e conclui que sua
correção "não pegou".

Antes de confiar numa medição, confira que a saída do serve termina em
`Application bundle generation complete`, e não em `failed`.

### 2. Serviço backend não recarrega com `--build`

Os containers dos serviços **montam o jar** de `build/libs`; não há imagem a reconstruir:

```bash
./gradlew :services:acs-service:bootJar
docker compose -f docker/docker-compose.dev.yml up -d --force-recreate acs-service
```

Sem `--force-recreate`, o compose considera tudo atualizado e você testa o binário antigo. Vale o
mesmo para as migrations — o container aplica o que está **no jar**, não o que está em `src`.

### 3. Migration aplicada é imutável

Editar uma migration que já rodou muda o checksum e o ambiente para de convergir. O remédio:

```bash
docker run --rm --network docker_default \
  -v "<repo>/backend/services/neuroassist-ai-migrations/src/main/resources/db/migration/empresa:/sql:ro" \
  flyway/flyway:10 -url=jdbc:postgresql://neuroassist-postgres:5432/neuroassist_dev \
  -user=neuroassist -password=neuroassist_dev_pass \
  -schemas=<schema> -defaultSchema=<schema> -locations=filesystem:/sql repair
```

**O `-locations` não é opcional.** Sem ele o Flyway resolve contra um conjunto vazio, não repara
nada — e **ainda assim imprime "Successfully repaired"**. Só se descobre conferindo o checksum no
banco. (No Windows, use `pwd -W` para o caminho do volume: caminho com espaço no formato Unix não
monta, e o Flyway avisa apenas `location not found`.)

Confirme com `docker compose ... up migrations-empresa`, que precisa dizer
`All 6 schema(s) converged`.

### 4. Porta 4200 ocupada por outro processo

Já aconteceu de um processo alheio ocupar a 4200 e devolver 404 para toda rota — as suítes rodavam
contra ele e reprovavam por "selector não encontrado", sem defeito algum no aplicativo. Hoje as
suítes **recusam rodar** contra algo que não seja o nosso app (checam `<app-root>` no conteúdo,
porque status não distingue). Se vir essa recusa, é isso.

### 5. Um teste verde não prova que ele pega o defeito

A prática desta casa: depois de escrever um teste, **quebre a produção de propósito** e confirme
que ele fica vermelho. Uma mutação que sobrevive é o mesmo que não ter teste.

Foi assim que se descobriu que o ramo de edição de cuidadora podia voltar a ser um no-op sem que
nada acusasse — os testes existiam, mas nenhum cobria aquele ramo.

---

## Integração contínua

Os workflows `CI — Backend` e `CI — Frontend` estão **desativados manualmente** (25/08/2026).
Para religar:

```bash
gh workflow enable "CI — Backend"
gh workflow enable "CI — Frontend"
```

Enquanto estiverem desligados, os gates que existem no repositório (instalação, drift dos
limiares, formatação, ArchUnit, testes) só rodam quando alguém os roda à mão.
