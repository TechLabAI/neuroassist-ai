# ADR-007 (CONV-local) — Voice output: cached audio playback with Web Speech API fallback

- **Status:** proposed (2026-08-31)
- **Scope:** module-local (CONV frontend)
- **Decisor:** FE Architect (vision phase); implements the FE side of ADR-017 and its
  fallback constraint OPS-004.

> Numbered ADR-007 (skipping the local-006 slot) so no CONV-local ADR shares a number
> with a **materialized** shared ADR — shared ADR-006 (module-entry-as-library) is
> accepted project-wide, and reusing 006 locally (as GEST did) proved confusing.

## Context

CONV speaks the selected phrase out loud. Per ADR-017, synthesis (ElevenLabs, the
patient's cloned voice) happens **only** in the TREINAMENTO phase; the runtime just
**plays cached audio** referenced by the node (`conteudo.<locale>.audioRef`). Two
runtime realities force a fallback: (a) a phrase authored since the last training has
no cached audio yet, and (b) the audio source can be unavailable. OPS-004 mandates a
degraded path so **communication never stops** — the worst outcome for an assistive
speech channel is silence.

## Decision

The FE resolves `audioRef` to an audio URL (via `conv-service`/GWY) and plays the
cached clip. The `TtsPlaybackFacade` implements a strict fallback chain per leaf
activation:

1. **Cached audio present and playable** → play it (patient's cloned voice). `source =
   'cached-audio'`.
2. **Cached audio absent or fails to load/play** → speak the node text via the browser
   **Web Speech API** (generic voice). `source = 'web-speech'`, `state = 'fallback'`.
3. **Web Speech unavailable** → render the phrase text full-bleed and prominent.
   `source = 'text-only'`, `state = 'unavailable'`.

The FE **never** calls ElevenLabs and never synthesizes at runtime (ADR-017). Playback
is always in the **active locale** (ADR-018) — a node pending in the active locale is
never spoken by silently substituting another locale's audio. The resolved audio blob
may be cached in-memory for the session only (small clips; not persisted — SEC-002).

## Alternatives considered

1. **Real-time ElevenLabs synthesis per selection (original ADR-013 model).** Rejected
   by ADR-017: per-use cost (FIN-001), network latency and internet dependence on the
   patient's daily critical path; the browser must never hold ElevenLabs credentials.
2. **Cached audio with no fallback** (fail if audio missing). Rejected: violates
   OPS-004 — a newly authored phrase or a transient source outage would leave the
   patient unable to speak, the single worst failure mode of the channel.
3. **Web Speech API as the primary voice** (skip cached audio). Rejected: loses the
   patient's own cloned voice, which is a core product value; Web Speech is a safety
   net, not the default.

## Consequences

- **Positive:** runtime has zero LLM/TTS-API cost and no internet dependence for
  already-trained phrases; guaranteed spoken/visible output for every selection;
  keeps ElevenLabs and its credentials entirely out of the client.
- **Negative:** fallback voice loses cloned-voice fidelity (acceptable, monitored via
  fallback rate — doc 06 §1); adds a small session-scoped audio cache and a three-state
  playback machine to test; depends on `conv-service` exposing resolvable audio refs.

## References

- ADR-017 (TTS via ElevenLabs, synthesis in TREINAMENTO, cached audio) — `techlab-ba/
  projects/neuro-assist-ai/architecture/adr/ADR-017-tts-elevenlabs-cache-audio.md`
- ADR-018 (per-locale content and audio) · ADR-015 (retrieval-only runtime)
- Constraints OPS-004 (Web Speech fallback), SEC-002 (sensitive voice/health data)
- CONV docs 02 §3 (golden path), 03 §4 (playback transitions), 05 §3/§5
