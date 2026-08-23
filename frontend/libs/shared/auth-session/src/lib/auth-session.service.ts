// @neuroassist/shared/auth-session — AuthSessionService (P1 FE-E2E-003)
//
// Holds the authenticated session as Angular signals. HttpClient calls reach
// POST /oauth2/token (login) and POST /oauth2/token/refresh (rotation).
// The refresh() method is single-flight: concurrent callers share one in-flight
// Promise<void> and never trigger more than one /oauth2/token/refresh request.
//
// LGPD/health data: sessionStorage only — cleared on tab close, never persists
// across browser sessions. See auth-session.ts for full storage rationale.

import { Injectable, Signal, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthClaims, AuthTokens } from './auth-session';

/** Shape of the ACS POST /oauth2/token response (AutenticacaoResponse.java). */
interface TokenResponse {
  readonly accessToken: string;
  readonly refreshToken: string | null;
  readonly tokenType: string;
  readonly expiresIn: number;
  readonly tenant: string;  // UUID — same as empresaId / tenant_id in JWT
  readonly sub: string;     // caregiver UUID
  readonly papel: string;
  readonly authorities: string[];
}

const STORAGE_KEY = 'na_auth_v1';

interface StoredSession {
  readonly accessToken: string;
  readonly refreshToken: string;
  readonly expiresAt: number;
  readonly claims: AuthClaims;
}

@Injectable({ providedIn: 'root' })
export class AuthSessionService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  // ── internal writable signals ─────────────────────────────────────────────
  private readonly _accessToken = signal<string | null>(null);
  private readonly _refreshToken = signal<string | null>(null);
  private readonly _claims = signal<AuthClaims | null>(null);

  // ── public read-only API ──────────────────────────────────────────────────
  readonly accessToken: Signal<string | null> = this._accessToken.asReadonly();
  readonly claims: Signal<AuthClaims | null> = this._claims.asReadonly();
  readonly isAuthenticated: Signal<boolean> = computed(
    () => this._accessToken() !== null,
  );

  // ── single-flight refresh ─────────────────────────────────────────────────
  /** Shared in-flight refresh promise — null when no refresh is in progress. */
  private refreshInFlight: Promise<void> | null = null;

  constructor() {
    this._restoreFromStorage();
  }

  // ── public methods ────────────────────────────────────────────────────────

  /**
   * Authenticates with caregiver credentials against POST /oauth2/token.
   * Stores tokens in sessionStorage on success.
   * Throws on invalid credentials (401) or network error.
   */
  async login(username: string, password: string): Promise<void> {
    const body = { grantType: 'password', login: username, senha: password };
    const response = await firstValueFrom(
      this.http.post<TokenResponse>('/oauth2/token', body),
    );
    this._applyTokens(response);
  }

  /**
   * Rotates tokens via POST /oauth2/token/refresh.
   * Single-flight: up to N concurrent calls produce exactly 1 HTTP request.
   * On failure: calls logout() and re-throws.
   */
  refresh(): Promise<void> {
    if (this.refreshInFlight) {
      return this.refreshInFlight;
    }
    this.refreshInFlight = this._doRefresh().finally(() => {
      this.refreshInFlight = null;
    });
    return this.refreshInFlight;
  }

  /**
   * Clears the session (signals + sessionStorage) and navigates to /entrada.
   */
  logout(): void {
    this._accessToken.set(null);
    this._refreshToken.set(null);
    this._claims.set(null);
    this._clearStorage();
    void this.router.navigate(['/entrada']);
  }

  // ── private helpers ───────────────────────────────────────────────────────

  private async _doRefresh(): Promise<void> {
    const refreshToken = this._refreshToken();
    if (!refreshToken) {
      this.logout();
      throw new Error('refresh_no_token');
    }
    try {
      const response = await firstValueFrom(
        this.http.post<TokenResponse>('/oauth2/token/refresh', { refreshToken }),
      );
      this._applyTokens(response);
    } catch (err) {
      this.logout();
      throw err;
    }
  }

  private _applyTokens(response: TokenResponse): void {
    const expiresAt = Date.now() + response.expiresIn * 1000;
    const claims: AuthClaims = {
      sub: response.sub,
      empresaId: response.tenant,
      scopes: response.authorities,
    };
    const tokens: AuthTokens = {
      accessToken: response.accessToken,
      refreshToken: response.refreshToken ?? '',
      expiresAt,
    };

    this._accessToken.set(tokens.accessToken);
    this._refreshToken.set(tokens.refreshToken);
    this._claims.set(claims);

    this._persistToStorage({ ...tokens, claims });
  }

  private _persistToStorage(session: StoredSession): void {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch {
      // sessionStorage may be unavailable (private mode, quota exceeded)
      // In-memory signals remain valid for this tab session.
    }
  }

  private _clearStorage(): void {
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // ignore
    }
  }

  private _restoreFromStorage(): void {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw) return;
      const stored = JSON.parse(raw) as StoredSession;
      if (stored.expiresAt > Date.now()) {
        this._accessToken.set(stored.accessToken);
        this._refreshToken.set(stored.refreshToken);
        this._claims.set(stored.claims);
      } else {
        this._clearStorage();
      }
    } catch {
      // Malformed or missing — start fresh
      this._clearStorage();
    }
  }
}
