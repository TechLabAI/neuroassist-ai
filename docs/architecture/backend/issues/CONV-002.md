# CONV-002 -- Criar entidades JPA — ArvoreFala, NoArvoreFala, NoConteudo, AuditOutbox

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto

Materializa o modelo de dados do CONV como entidades JPA a partir do DDL canônico em
`contracts/data-model.md`. O CONV tem 4 tabelas: `conv_arvore_fala` (aggregate root `ArvoreFala`),
`conv_no_arvore_fala` (entity `NoArvoreFala`), `conv_no_conteudo` (entity `NoConteudo`) e
`conv_audit_outbox` (aggregate `AuditOutbox`). Todas vivem no schema per-tenant
(`t_<empresa>`) — configurado via `TenantContextHolder` do monorepo (`ADR-003`).
O CONV nunca escreve `conv_no_arvore_fala` / `conv_no_conteudo` em runtime (dados de autoria
são do TREI); `conv_audit_outbox` é a única tabela que CONV escreve.

## Spec de referencia

- SPEC-04 (data-model-migrations — DDL canônico)
- `contracts/data-model.md` (DDL completo com invariantes)
- `ADR-001-hexagonal-architecture.md` (entidades no pacote `domain/`)

## Escopo

- [ ] Criar `domain/model/ArvoreFala.java` (@Entity, UUID pk, pacienteId, localesSuportados como `String[]` / `List<String>`, createdAt/updatedAt)
- [ ] Criar `domain/model/NoArvoreFala.java` (@Entity, UUID pk, arvoreId, parentId nullable, tipo enum `TipoNo`, engineFeatureRef nullable, ordem, timestamps; sem FK cross-service para PAC)
- [ ] Criar `domain/model/NoConteudo.java` (@Entity, UUID pk, noId, locale enum `LocaleConv`, texto, audioRef nullable, audioFormato nullable, confirmadoCuidadora, confirmadoPaciente, status enum `StatusConteudo`, timestamps; UNIQUE (noId, locale))
- [ ] Criar `domain/model/AuditOutbox.java` (@Entity, UUID pk, clientEventId nullable, tipo enum `TipoFato`, pacienteId, noId nullable, payload JSONB, status enum `StatusOutbox`, tentativas, createdAt, sentAt nullable)
- [ ] Criar enums em `domain/model/`: `TipoNo` (CATEGORIA, FRASE), `LocaleConv` (PT, EN), `StatusConteudo` (ATIVO, PENDENTE), `TipoFato` (SPEECH_NODE_SELECTED, PHRASE_MATCHED, CONV_SERVED_DEGRADED), `StatusOutbox` (PENDENTE, ENVIADO, DESCARTADO), `AudioFormato` (MP3, OPUS)
- [ ] `./gradlew :conv-service:compileJava` retorna BUILD SUCCESS

## Resultado esperado

Entidades JPA compiláveis com enums corretos, mapeamento schema-per-tenant via `@Table(schema = "#{tenantSchema}")` ou equivalente do padrão do monorepo.

## Criterios de aceite

- [ ] `./gradlew :conv-service:compileJava` BUILD SUCCESS
- [ ] Entidades anotadas com `@Entity` e `@Table(name = "conv_<nome>")` no pacote `com.neuroassist.conv.domain.model`
- [ ] Todos os enums com valores correspondentes ao DDL (case: `CATEGORIA`/`FRASE`, `PT`/`EN`, `ATIVO`/`PENDENTE`, etc.)
- [ ] `NoConteudo` tem constraint `@UniqueConstraint(columnNames = {"no_id", "locale"})` declarada
- [ ] `AuditOutbox.clientEventId` tem índice único parcial declarado (ou anotação equivalente para o DDL do Flyway gerado)

## Dependencias

- CONV-001 (precede)

## Estimativa

M

## Prioridade

P1
