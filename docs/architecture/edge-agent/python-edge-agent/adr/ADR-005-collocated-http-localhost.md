# ADR-005 — HTTP (not HTTPS) for co-located service communication

## Status
Aceita

## Contexto
The PEA and all NeuroAssist AI Java services run on the same physical machine (confirmed
2026-07-29). All communication is loopback (localhost). The question is whether to use
HTTPS (TLS) or plain HTTP for intra-machine calls.

The initial PEA architecture assumed HTTPS for all calls (treating services as remote).
After the co-location constraint was confirmed, this decision was revisited.

## Decisão
**Plain HTTP on localhost for PEA ↔ Java service communication.** TLS provides confidentiality
and integrity over untrusted networks. On loopback (`127.0.0.1`), the traffic never traverses
a network interface — there is no eavesdropping or MITM risk. Adding TLS would require
certificate management (self-signed or CA) with no security benefit on the same machine.

`backend.url = "http://localhost:8080"` (via GWY) is the default configuration.
Individual service ports are optional overrides (bypassing GWY for direct calls if needed).

The Java services' Spring Security config allows plain HTTP on localhost without breaking
the JWT validation (JWT is validated by signature, not by transport security).

## Consequências
**Positivas:** zero certificate management; simpler config; lower connection overhead.
**Negativas:** if the deployment model ever changes (services on different machines),
the communication layer must be upgraded to HTTPS — a config change, not a code change
(all URLs come from `config.toml`).
**Safeguard:** `ConfigLoader` logs a WARN if `backend.url` starts with `http://` and
the URL is NOT localhost/127.0.0.1, prompting the operator to switch to HTTPS.
