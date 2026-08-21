package com.neuroassist.hist.domain.model;

public enum ChainStatus {
    intact,
    broken,
    empty,
    /** Chain is structurally intact but some record payloads are unreadable because the per-patient
     * encryption key was erased (LGPD crypto-shredding, P10 / HIST-024). */
    partially_erased
}
