<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-006 — Hash-chained tamper-evident audit records

## Status
aceita

## Contexto
ADR-004 makes the ledger append-only at the application and (best-effort) privilege layers, but that alone
does not let a reader *prove* the trail is intact: a sufficiently privileged actor with direct database
access could still alter or drop rows. For a healthtech audit trail used in accountability and LGPD
contexts, the trail should be **tamper-evident** — an alteration or a gap should be detectable on read, not
merely prevented by hope. The constraint is proportionality: the pilot needs a credible, low-cost integrity
signal, not a full cryptographic notarization stack.

## Decisão
Vamos encadear cada registro por hash dentro do seu *stream* (tenant, sujeito): cada `AuditRecord` guarda
`prevHash` (o `recordHash` do registro anterior do mesmo stream, ou uma constante *genesis* para o
primeiro) e `recordHash = sha256(prevHash ‖ canonical(record))`, com `sequenceNo` estritamente monotônico
por stream (índice único `(stream_key, sequence_no)`). Uma API de **verificação de cadeia** recomputa os
hashes de um stream e retorna *íntegra* ou *quebrada@N*, permitindo a um coordenador/DPO provar a
integridade sob demanda. O escopo do encadeamento é (tenant, sujeito) — nunca cruza tenants — coerente com
o isolamento schema-per-empresa (ADR-003 shared). Fica **fora do MVP**: ancoragem externa, checkpoints
assinados e notarização de terceiros — a garantia é tamper-*evidente*, não tamper-*proof*, e isso não deve
ser super-prometido (R5, doc 06).

## Consequências
**Positivas:** qualquer alteração silenciosa ou registro removido torna-se detectável por quem verifica a
cadeia; custo baixo (um SHA-256 por append + um cache de tip por stream, `stream_head`); integridade
naturalmente particionada por sujeito e por tenant, sem cadeia global a gerenciar. **Negativas /
trade-offs:** a garantia só se realiza quando alguém *verifica* — um atacante com acesso de escrita ao banco
poderia recomputar todos os hashes a jusante (portanto evidente para leitores honestos, não à prova de
adulteração); o append serializa no tip do stream (contenção R3 — desprezível no piloto, mitigável com
`stream_head`); a canonicalização do registro precisa ser determinística e versionada para que a
verificação seja estável ao longo do tempo. Aceito porque entrega um sinal de integridade credível e barato,
proporcional ao piloto, deixando notarização forte como evolução futura.
