# ADR-004 — SQLite for local persistence (outbox + caches)

> **SUPERSEDED (2026-08-22)** por `GEST ADR-007 — O browser é o único motor de detecção`.
> O outbox passa a ser IndexedDB + fila de retry no browser (follow-up 4 da ADR-007).

## Status
Aceita

## Contexto
The PEA needs to persist two types of data across restarts:
1. Undelivered `GestoDetectado` events (outbox — survives EVNT downtime)
2. Fetched detection profile and command vocabulary (caches — avoid re-fetching on restart)

Options: in-memory only (no persistence), SQLite (file-based), Redis (separate process),
plain JSON files.

## Decisão
**SQLite via `aiosqlite`.** SQLite is the correct choice for a co-located single-process
agent with modest storage needs (< 100 rows across all tables). It requires no separate
process or configuration, survives restarts, and is async-compatible via `aiosqlite`.
Plain JSON files lack transactional semantics (risk of corruption on crash during write).
Redis would require an additional running process.

`aiosqlite` wraps SQLite in a thread pool executor, making all reads/writes non-blocking
from the asyncio event loop's perspective.

## Consequências
**Positivas:** zero-admin, single file, transactional writes, async-compatible, ships
with Python stdlib fallback (though `aiosqlite` adds async wrapper).
**Negativas:** single-writer model (fine for PEA — only one process accesses this SQLite);
no built-in replication (not needed for local-only data).
**Trade-off:** accepted.
